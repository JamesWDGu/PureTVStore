package com.puretv.store.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import com.puretv.store.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.content.FileProvider
import java.io.File

/**
 * Streams an APK into a PackageInstaller session. The television always shows the
 * system confirmation dialog; this app cannot install silently.
 */
object ApkInstaller {
    sealed interface Result {
        data object Started : Result
        data object NeedsPermission : Result
        data class Failed(val message: String) : Result
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, apk: File, label: String, packageName: String?): Result {
        if (!canInstall(context)) return Result.NeedsPermission
        if (!apk.isFile || apk.length() == 0L) return Result.Failed("安装包不存在或为空")

        val installer = context.packageManager.packageInstaller
        var session: PackageInstaller.Session? = null
        return try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppLabel(label)
                setSize(apk.length())
            }
            val sessionId = installer.createSession(params)
            session = installer.openSession(sessionId)
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
                session.fsync(output)
            }

            val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_PACKAGE, packageName)
            }
            // Android 14+ rejects an immutable status receiver: PackageInstaller fills
            // the extras itself, so the PendingIntent must be mutable.
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_MUTABLE
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                flags,
            )
            session.commit(pendingIntent.intentSender)
            Result.Started
        } catch (t: Throwable) {
            runCatching { session?.abandon() }
            Result.Failed(t.message ?: "无法开始安装")
        } finally {
            runCatching { session?.close() }
        }
    }

    /**
     * Hands the APK to the system installer through FileProvider. The store UI uses
     * this foreground path because some TVs block confirmation activities started
     * from a PackageInstaller status broadcast.
     */
    fun installWithSystemUi(context: Context, apk: File): Result {
        if (!canInstall(context)) return Result.NeedsPermission
        if (!apk.isFile || apk.length() == 0L) return Result.Failed("安装包不存在或为空")
        return try {
            val directory = File(context.cacheDir, "install").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val target = File(directory, apk.name)
            apk.copyTo(target, overwrite = true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                    )
                },
            )
            Result.Started
        } catch (t: Throwable) {
            Result.Failed(t.message ?: "无法打开系统安装界面")
        }
    }

    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}"),
        )

    const val EXTRA_LABEL = "extra_label"
    const val EXTRA_PACKAGE = "extra_package"
}

object InstallCenter {
    data class InstallResult(
        val packageName: String?,
        val label: String,
        val success: Boolean,
        val message: String?,
    )

    val results = MutableStateFlow<InstallResult?>(null)

    /** Applies the final status broadcast and notifies the store UI. */
    fun complete(context: Context, intent: Intent, fallbackMessage: String? = null) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val systemMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(ApkInstaller.EXTRA_PACKAGE)
        val label = intent.getStringExtra(ApkInstaller.EXTRA_LABEL) ?: "应用"
        val success = status == PackageInstaller.STATUS_SUCCESS
        results.value = InstallResult(
            packageName = packageName,
            label = label,
            success = success,
            message = if (success) null else fallbackMessage ?: systemMessage,
        )
        Notifications.showInstallResult(context, label, success, systemMessage)
        if (success) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

/** Receives PackageInstaller status and shows the system confirmation dialog. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
            if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                InstallCenter.complete(context, intent)
                return
            }
            val confirm = IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_INTENT,
                Intent::class.java,
            )
            if (confirm == null) {
                InstallCenter.complete(context, intent, "电视没有返回安装确认界面")
                return
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(confirm)
            } catch (t: Throwable) {
                Log.w("PureTVStore", "无法打开系统安装确认界面", t)
                InstallCenter.complete(context, intent, "电视上没有可用的安装界面")
            }
        } catch (t: Throwable) {
            Log.w("PureTVStore", "处理安装状态失败", t)
        }
    }
}
