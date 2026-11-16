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
        bitmap?.let(::trimTransparent)?.asImageBitmap()
    }

    private fun installedIcon(context: Context, packageName: String): Bitmap? = runCatching {
        val pm = context.packageManager
        pm.getApplicationIcon(packageName)
            .toBitmap(width = 144, height = 144)
            .let { if (it.width > 144) Bitmap.createScaledBitmap(it, 144, 144, true) else it }
    }.getOrNull()

    /**
     * Removes transparent padding around the artwork so the icon fills its tile.
     * Icons that already reach the edges are returned unchanged.
     */
    private fun trimTransparent(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] ushr 24 != 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return source
        val trimmedWidth = right - left + 1
        val trimmedHeight = bottom - top + 1
        if (trimmedWidth >= width * 0.99f && trimmedHeight >= height * 0.99f) return source
        return runCatching {
            Bitmap.createBitmap(source, left, top, trimmedWidth, trimmedHeight)
        }.getOrDefault(source)
    }

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
