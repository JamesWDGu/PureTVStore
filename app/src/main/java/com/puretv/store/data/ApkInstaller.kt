package com.puretv.store.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import com.puretv.store.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
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
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(ApkInstaller.EXTRA_PACKAGE)
        val label = intent.getStringExtra(ApkInstaller.EXTRA_LABEL) ?: "应用"

        // -1 asks the installer UI to show the system confirmation dialog; the
        // final success/failure arrives in a second broadcast after the user acts.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { confirm ->
                    runCatching { context.startActivity(confirm) }
                }
            return
        }

        val success = status == PackageInstaller.STATUS_SUCCESS

        InstallCenter.results.value = InstallCenter.InstallResult(
            packageName = packageName,
            label = label,
            success = success,
            message = message,
        )
        Notifications.showInstallResult(context, label, success, message)
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
