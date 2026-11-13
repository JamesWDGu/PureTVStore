package com.puretv.store.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

data class GitHubRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val assets: List<ReleaseAsset>,
) {
    val versionName: String get() = VersionNames.normalize(tag)
}

class GitHubException(message: String) : IOException(message)

object GitHubReleaseParser {
    fun parse(json: String): GitHubRelease {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw GitHubException("版本信息格式不正确")
        }
        val assetsJson = root.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val asset = assetsJson.optJSONObject(index) ?: continue
                val name = asset.optString("name").trim()
                val url = asset.optString("browser_download_url").trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    add(ReleaseAsset(name, url, asset.optLong("size", 0L)))
                }
            }
        }
        return GitHubRelease(
            tag = root.optString("tag_name").trim(),
            title = root.optString("name").trim(),
            notes = root.optString("body").trim(),
            assets = assets,
        )
    }
}

class GitHubClient(private val userAgent: String = "PureTVStore") {
    fun latestRelease(repo: String): GitHubRelease {
        val json = get("https://api.github.com/repos/$repo/releases/latest")
        val release = GitHubReleaseParser.parse(json)
        if (release.tag.isBlank()) throw GitHubException("该仓库还没有发布版本")
        return release
    }

    fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val hint = if (code == 403) "（访问次数过多，请稍后再试）" else ""
                throw GitHubException("GitHub 返回 $code$hint")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun download(url: String, target: java.io.File, onProgress: (Long, Long) -> Unit) {
        target.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw GitHubException("下载安装包失败（$code）")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 200) {
                            lastReport = now
                            onProgress(done, if (total > 0) total else 0L)
                        }
                    }
                    onProgress(done, if (total > 0) total else done)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
