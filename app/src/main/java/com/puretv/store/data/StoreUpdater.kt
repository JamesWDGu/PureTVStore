package com.puretv.store.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class StoreUpdate(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/**
 * Checks the store's own GitHub releases and downloads the next APK.
 * Uses the release API first and falls back to the quota-free Atom feed.
 */
class StoreUpdater(private val context: Context) {

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun apkFile(version: String): File =
        File(File(context.cacheDir, "updates"), "PureTVStore-$version.apk")

    /** Returns an update only when the release is newer than the installed version. */
    suspend fun findUpdate(): StoreUpdate? = withContext(Dispatchers.IO) {
        val api = try {
            Result.success(fetchApiRelease())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
        val update = api.getOrNull() ?: try {
            fetchAtomRelease()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (update == null && api.isFailure) throw api.exceptionOrNull()!!
        update?.takeIf { VersionNames.isNewer(it.versionName, currentVersionName()) }
    }

    suspend fun download(
        update: StoreUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = apkFile(update.versionName)
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("下载失败（${connection.responseCode}）")
            }
            val declaredSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            val total = declaredSize.takeIf { it > 0 } ?: update.apkSize
            val progressStep = (total / 200).coerceAtLeast(64 * 1024)
            val lastReported = AtomicLong(0)
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported.get() >= progressStep) {
                            lastReported.set(downloaded)
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            target
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchApiRelease(): StoreUpdate? {
        val response = request(LATEST_RELEASE_URL)
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (response.code != HttpURLConnection.HTTP_OK) {
            throw IOException("更新服务暂时不可用（${response.code}）")
        }
        return parseStoreUpdate(response.body)
    }

    private fun fetchAtomRelease(): StoreUpdate? {
        val response = request(atomUrl(UPDATER_REPO), "application/atom+xml")
        if (response.code != HttpURLConnection.HTTP_OK) return null
        return parseStoreUpdateAtom(response.body)
    }

    private class Response(val code: Int, val body: String)

    private fun request(url: String, accept: String = "application/vnd.github+json"): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT = "PureTVStore"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$UPDATER_REPO/releases/latest"
    }
}

private const val UPDATER_REPO = "JamesWDGu/PureTVStore"

internal fun atomUrl(repo: String): String = "https://github.com/$repo/releases.atom"

/** Parses the latest release payload and picks the `PureTVStore-*.apk` asset. */
internal fun parseStoreUpdate(json: String): StoreUpdate? {
    val root = JSONObject(json)
    if (root.optBoolean("draft")) return null
    val tag = root.optString("tag_name")
    val version = VersionNames.normalize(tag).takeIf { it.isNotEmpty() } ?: return null
    val assets = root.optJSONArray("assets") ?: return null
    var chosen: JSONObject? = null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val name = asset.optString("name")
        if (!name.endsWith(".apk", ignoreCase = true)) continue
        if (name.startsWith("PureTVStore-", ignoreCase = true)) {
            chosen = asset
            break
        }
        if (chosen == null) chosen = asset
    }
    val asset = chosen ?: return null
    return StoreUpdate(
        versionName = version,
        notes = plainReleaseNotes(root.optString("body")),
        apkUrl = asset.optString("browser_download_url"),
        apkSize = asset.optLong("size"),
    )
}

/** Parses the first entry of GitHub's releases Atom feed; no API quota is used. */
internal fun parseStoreUpdateAtom(xml: String): StoreUpdate? {
    val entry = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        .find(xml)?.groupValues?.get(1) ?: return null
    val tag = Regex("/releases/tag/([^\"]+)").find(entry)?.groupValues?.get(1) ?: return null
    val version = VersionNames.normalize(tag)
    if (version.isEmpty() || !version.first().isDigit()) return null
    val encodedNotes = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
        .find(entry)?.groupValues?.get(1).orEmpty()
    return StoreUpdate(
        versionName = version,
        notes = plainReleaseNotes(htmlToText(encodedNotes)),
        apkUrl = "https://github.com/$UPDATER_REPO/releases/download/$tag/" +
            "PureTVStore-$version.apk",
        apkSize = 0L,
    )
}

internal fun plainReleaseNotes(text: String): String = text
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace("**", "")
    .replace("`", "")
    .replace(Regex("(?m)^---\\s*$"), "")
    .lines()
    .joinToString("\n") { it.trimEnd() }
    .trim()

private fun htmlToText(html: String): String = html
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("&#(x?[0-9a-fA-F]+);")) { match ->
        val raw = match.groupValues[1]
        val code = if (raw.startsWith("x", ignoreCase = true)) {
            raw.drop(1).toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
        code?.takeIf { it in 1..0x10FFFF }
            ?.let { String(Character.toChars(it)) }
            ?: match.value
    }
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</(p|h[1-6]|li|ul|ol)>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
