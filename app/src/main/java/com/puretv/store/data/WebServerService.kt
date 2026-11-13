package com.puretv.store.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

object ServerState {
    val running = MutableStateFlow(false)
    val url = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
}

class WebServerService : Service() {

    private var server: WebServer? = null
    private lateinit var fileAccess: FileAccess

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        fileAccess = FileAccess(this)
        startForegroundNotification("正在启动…")

        val address = NetworkAddress.localIpv4() ?: "电视IP"
        val url = "http://$address:${WebServer.PORT}"
        try {
            val started = WebServer(
                context = this,
                fileAccess = fileAccess,
                onApkUploaded = ::installApk,
            ).also { it.start(10_000, false) }
            server = started
            ServerState.running.value = true
            ServerState.url.value = url
            ServerState.error.value = null
            updateNotification("已开启：$url")
        } catch (t: Throwable) {
            ServerState.running.value = false
            ServerState.url.value = null
            ServerState.error.value = "网页服务启动失败：${t.message ?: "端口可能被占用"}"
            updateNotification("启动失败")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        ServerState.running.value = false
        ServerState.url.value = null
        super.onDestroy()
    }

    private fun installApk(file: java.io.File, fileName: String): ApkInstaller.Result {
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val label = info?.applicationInfo
            ?.let { packageManager.getApplicationLabel(it).toString() }
            ?: fileName.removeSuffix(".apk")
        val result = ApkInstaller.install(this, file, label, info?.packageName)
        if (result is ApkInstaller.Result.Started) {
            Notifications.showConfirmInstall(this, label)
        } else if (result is ApkInstaller.Result.NeedsPermission) {
            Notifications.showInstallPermission(this)
        }
        return result
    }

    private fun startForegroundNotification(text: String) {
        val notification = Notifications.serverNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                Notifications.SERVER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Notifications.SERVER_NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(
            Notifications.SERVER_NOTIFICATION_ID,
            Notifications.serverNotification(this, text),
        )
    }

    companion object {
        const val ACTION_STOP = "com.puretv.store.STOP_WEB_SERVER"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WebServerService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebServerService::class.java))
        }
    }
}
