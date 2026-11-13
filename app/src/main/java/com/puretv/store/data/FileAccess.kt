package com.puretv.store.data

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.IOException

data class FileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
)

class FileAccessException(message: String) : Exception(message)

/** Recursive file management limited to the app folder or a granted shared-storage root. */
class FileAccess(private val context: Context) {

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun roots(): List<File> {
        val result = mutableListOf<File>()
        context.getExternalFilesDir(null)?.takeIf { it.exists() }?.let { result += it }
        if (hasAllFilesAccess()) {
            val shared = Environment.getExternalStorageDirectory()
            if (shared != null && shared.exists()) result.add(0, shared)
        }
        return result.distinctBy { it.absolutePath }
    }

    fun defaultRoot(): File = roots().firstOrNull() ?: context.filesDir

    fun resolve(path: String?): File {
        val roots = roots()
        val requested = if (path.isNullOrBlank()) defaultRoot() else File(path)
        val canonical = try {
            requested.canonicalFile
        } catch (_: IOException) {
            throw FileAccessException("无法访问该位置")
        }
        if (roots.none { isInside(it.canonicalFile, canonical) }) {
            throw FileAccessException("只能管理授权范围内的文件")
        }
        return canonical
    }

    fun resolveDir(path: String?): File {
        val dir = resolve(path)
        if (!dir.isDirectory) throw FileAccessException("该位置不是文件夹")
        return dir
    }

    fun list(path: String?): List<FileEntry> {
        val dir = resolveDir(path)
        return dir.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    directory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    modified = file.lastModified(),
                )
            }
            .orEmpty()
    }

    fun delete(path: String?) {
        val file = resolve(path)
        if (isRoot(file)) throw FileAccessException("不能删除根目录")
        if (!file.deleteRecursively()) throw FileAccessException("删除失败，文件可能正在使用")
    }

    fun rename(path: String?, newName: String): File {
        val file = resolve(path)
        if (isRoot(file)) throw FileAccessException("不能重命名根目录")
        val safe = sanitizeName(newName)
        val target = File(file.parentFile ?: throw FileAccessException("无法定位上级目录"), safe)
        if (target.exists()) throw FileAccessException("同名文件已存在")
        if (!file.renameTo(target)) throw FileAccessException("重命名失败")
        return target
    }

    fun mkdir(parentPath: String?, name: String): File {
        val parent = resolveDir(parentPath)
        val target = File(parent, sanitizeName(name))
        if (target.exists()) throw FileAccessException("同名文件已存在")
        if (!target.mkdirs()) throw FileAccessException("新建文件夹失败")
        return target
    }

    fun saveUpload(temp: File, dir: File, rawName: String): File {
        val safe = sanitizeName(rawName)
        val target = uniqueFile(dir, safe)
        return try {
            temp.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            target
        } catch (e: IOException) {
            target.delete()
            throw FileAccessException("保存上传文件失败")
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        for (index in 2..999) {
            val candidate = File(dir, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
        }
        throw FileAccessException("同名文件过多")
    }

    private fun isRoot(file: File): Boolean =
        roots().any { runCatching { it.canonicalFile == file }.getOrDefault(false) }
}

fun sanitizeName(raw: String): String {
    val cleaned = raw.substringAfterLast('/').substringAfterLast('\\').trim().trim('.')
    val safe = cleaned.ifBlank { "未命名" }.take(180)
    return if (safe == "." || safe == "..") "未命名" else safe
}

fun isInside(root: File, target: File): Boolean {
    val rootPath = root.canonicalPath
    val targetPath = target.canonicalPath
    return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
}
