package com.puretv.store.data

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.ResponseException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLConnection

/**
 * LAN web panel: APK upload + recursive file management.
 * Bound to all interfaces on [PORT]; intended for a trusted home network only.
 */
class WebServer(
    private val context: Context,
    private val fileAccess: FileAccess,
    private val onApkUploaded: (File, String) -> ApkInstaller.Result,
) : NanoHTTPD(PORT) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.uri) {
                "/" -> htmlResponse()
                "/api/status" -> jsonResponse(statusJson())
                "/api/files" -> filesResponse(session)
                "/api/download" -> downloadResponse(session)
                "/api/upload" -> uploadResponse(session)
                "/api/rename" -> renameResponse(session)
                "/api/delete" -> deleteResponse(session)
                "/api/mkdir" -> mkdirResponse(session)
                else -> jsonError(Response.Status.NOT_FOUND, "没有这个页面")
            }
        } catch (e: FileAccessException) {
            jsonError(Response.Status.BAD_REQUEST, e.message ?: "无法访问文件")
        } catch (e: ResponseException) {
            jsonError(e.status, e.message ?: "请求无法处理")
        } catch (t: Throwable) {
            jsonError(Response.Status.INTERNAL_ERROR, t.message ?: "服务器处理失败")
        }
    }

    private fun htmlResponse(): Response {
        val html = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            .apply { addHeader("Cache-Control", "no-store") }
    }

    private fun statusJson(): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return JSONObject()
            .put("name", "PureTV 商店")
            .put("version", info.versionName ?: "")
            .put("port", PORT)
            .put("url", "http://${NetworkAddress.localIpv4() ?: "电视IP"}:$PORT")
            .put("allFiles", fileAccess.hasAllFilesAccess())
            .put("canInstall", ApkInstaller.canInstall(context))
            .put("roots", JSONArray(fileAccess.roots().map { it.absolutePath }))
            .toString()
    }

    private fun filesResponse(session: IHTTPSession): Response {
        if (session.method != Method.GET) return methodNotAllowed()
        val dir = fileAccess.resolveDir(param(session, "path"))
        val entries = JSONArray()
        fileAccess.list(dir.absolutePath).forEach { entry ->
            entries.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("path", entry.path)
                    .put("dir", entry.directory)
                    .put("size", entry.size)
                    .put("modified", entry.modified),
            )
        }
        val root = fileAccess.roots().firstOrNull()
        val parent = dir.parentFile
            ?.takeIf { parentFile -> root != null && isInside(root, parentFile) }
            ?.absolutePath
        val json = JSONObject()
            .put("path", dir.absolutePath)
            .put("root", root?.absolutePath ?: "")
            .put("parent", parent ?: JSONObject.NULL)
            .put("entries", entries)
        return jsonResponse(json.toString())
    }

    private fun downloadResponse(session: IHTTPSession): Response {
        if (session.method != Method.GET) return methodNotAllowed()
        val file = fileAccess.resolve(param(session, "path"))
        if (!file.isFile) return jsonError(Response.Status.NOT_FOUND, "文件不存在")
        val mime = if (file.name.endsWith(".apk", ignoreCase = true)) {
            "application/vnd.android.package-archive"
        } else {
            URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            file.inputStream(),
            file.length(),
        ).apply {
            addHeader("Content-Disposition", contentDisposition(file.name))
            addHeader("Cache-Control", "no-store")
        }
    }

    private fun uploadResponse(session: IHTTPSession): Response {
        if (session.method != Method.POST) return methodNotAllowed()
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files["file"] ?: return jsonError(Response.Status.BAD_REQUEST, "没有收到文件")
        val name = param(session, "name") ?: "upload.bin"
        val dir = fileAccess.resolveDir(param(session, "dir"))
        val saved = fileAccess.saveUpload(File(tempPath), dir, name)
        val isApk = saved.name.endsWith(".apk", ignoreCase = true)

        val json = JSONObject()
            .put("ok", true)
            .put("name", saved.name)
            .put("path", saved.absolutePath)
        if (isApk) {
            val result = onApkUploaded(saved, saved.name)
            json.put("install", installResultJson(result))
        }
        return jsonResponse(json.toString())
    }

    private fun renameResponse(session: IHTTPSession): Response {
        if (session.method != Method.POST) return methodNotAllowed()
        parseForm(session)
        val path = param(session, "path") ?: return jsonError(Response.Status.BAD_REQUEST, "缺少文件")
        val name = param(session, "name") ?: return jsonError(Response.Status.BAD_REQUEST, "缺少名称")
        val target = fileAccess.rename(path, name)
        return jsonResponse(JSONObject().put("ok", true).put("path", target.absolutePath).toString())
    }

    private fun deleteResponse(session: IHTTPSession): Response {
        if (session.method != Method.POST) return methodNotAllowed()
        parseForm(session)
        val path = param(session, "path") ?: return jsonError(Response.Status.BAD_REQUEST, "缺少文件")
        fileAccess.delete(path)
        return jsonResponse(JSONObject().put("ok", true).toString())
    }

    private fun mkdirResponse(session: IHTTPSession): Response {
        if (session.method != Method.POST) return methodNotAllowed()
        parseForm(session)
        val parent = param(session, "dir") ?: return jsonError(Response.Status.BAD_REQUEST, "缺少目录")
        val name = param(session, "name") ?: return jsonError(Response.Status.BAD_REQUEST, "缺少名称")
        val created = fileAccess.mkdir(parent, name)
        return jsonResponse(
            JSONObject().put("ok", true).put("path", created.absolutePath).toString(),
        )
    }

    private fun parseForm(session: IHTTPSession) {
        runCatching { session.parseBody(HashMap()) }
    }

    private fun param(session: IHTTPSession, name: String): String? =
        session.parameters[name]?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun installResultJson(result: ApkInstaller.Result): JSONObject = when (result) {
        ApkInstaller.Result.Started -> JSONObject().put("status", "started")
        ApkInstaller.Result.NeedsPermission -> JSONObject()
            .put("status", "needs_permission")
            .put("message", "请在电视上的商店中允许安装未知应用")
        is ApkInstaller.Result.Failed -> JSONObject()
            .put("status", "failed")
            .put("message", result.message)
    }

    private fun jsonResponse(json: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
            .apply { addHeader("Cache-Control", "no-store") }

    private fun jsonError(status: Response.IStatus, message: String): Response =
        newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            JSONObject().put("ok", false).put("error", message).toString(),
        )

    private fun methodNotAllowed(): Response =
        jsonError(Response.Status.METHOD_NOT_ALLOWED, "请求方式不正确")

    private fun contentDisposition(name: String): String {
        val ascii = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
    }

    companion object {
        const val PORT = 8686
    }
}
