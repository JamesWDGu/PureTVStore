package com.puretv.store.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads a real app icon: the installed package's icon when present, otherwise the
 * optional `iconUrl` from the store config. Results are cached in memory and on disk.
 */
object AppIconLoader {
    private val memory = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    suspend fun load(
        context: Context,
        app: StoreApp,
        installed: Boolean,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val installedIcon = if (installed) installedIcon(context, app.packageName) else null
        val bitmap = installedIcon ?: app.iconUrl?.let { loadRemote(context, it) }
        bitmap?.asImageBitmap()
    }

    private fun installedIcon(context: Context, packageName: String): Bitmap? = runCatching {
        val pm = context.packageManager
        pm.getApplicationIcon(packageName)
            .toBitmap(width = 144, height = 144)
            .let { if (it.width > 144) Bitmap.createScaledBitmap(it, 144, 144, true) else it }
    }.getOrNull()

    private fun loadRemote(context: Context, url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val file = File(context.cacheDir, "icons/${url.hashCode()}.png")
        val cached = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        val bitmap = cached ?: download(url)?.also { bmp ->
            file.parentFile?.mkdirs()
            runCatching {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } ?: return null
        memory.put(url, bitmap)
        return bitmap
    }

    private fun download(url: String): Bitmap? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "PureTVStore")
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
