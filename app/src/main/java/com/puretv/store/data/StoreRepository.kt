package com.puretv.store.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Store data: remote config with a cached and bundled fallback, plus release metadata.
 */
class StoreRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("store_cache", Context.MODE_PRIVATE)
    private val client = GitHubClient()
    private val releaseCache = mutableMapOf<String, Pair<Long, GitHubRelease>>()

    suspend fun loadApps(): List<StoreApp> = withContext(Dispatchers.IO) {
        try {
            val json = client.get(CONFIG_URL)
            val apps = StoreConfigParser.parse(json)
            prefs.edit().putString(KEY_CONFIG, json).apply()
            apps
        } catch (t: Throwable) {
            val cached = prefs.getString(KEY_CONFIG, null)
                ?.let { runCatching { StoreConfigParser.parse(it) }.getOrNull() }
            cached ?: bundledApps()
        }
    }

    suspend fun latestRelease(app: StoreApp): GitHubRelease = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        releaseCache[app.repo]?.let { (time, release) ->
            if (now - time < RELEASE_CACHE_MS) return@withContext release
        }
        val release = client.latestRelease(app.repo)
        releaseCache[app.repo] = now to release
        release
    }

    suspend fun downloadAsset(
        asset: ReleaseAsset,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        client.download(asset.downloadUrl, target, onProgress)
    }

    private fun bundledApps(): List<StoreApp> = try {
        val json = context.assets.open("store-apps.json").bufferedReader().use { it.readText() }
        StoreConfigParser.parse(json)
    } catch (t: Throwable) {
        throw StoreConfigException("无法读取商店配置，请检查网络后重试")
    }

    companion object {
        const val CONFIG_URL =
            "https://raw.githubusercontent.com/JamesWDGu/PureTVStore/main/store/apps.json"
        private const val KEY_CONFIG = "config_json"
        private const val RELEASE_CACHE_MS = 5 * 60 * 1000L
    }
}
