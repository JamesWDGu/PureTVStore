package com.puretv.store.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.puretv.store.MainActivity
import com.puretv.store.R

object Notifications {
    const val CHANNEL_SERVER = "web_server"
    const val CHANNEL_INSTALL = "install"

    const val SERVER_NOTIFICATION_ID = 10
    const val INSTALL_NOTIFICATION_ID = 11

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVER, "网页传文件", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "网页服务的运行状态" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSTALL,
                "安装应用",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "从商店或网页安装应用" },
        )
    }

    fun serverNotification(context: Context, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, WebServerService::class.java)
                .setAction(WebServerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVER)
            .setSmallIcon(R.drawable.ic_stat_store)
            .setContentTitle("网页传文件")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "停止", stop)
            .build()
    }

    fun showConfirmInstall(context: Context, label: String) {
        notify(
            context,
            INSTALL_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_INSTALL)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setContentTitle("等待确认安装")
                .setContentText("请在电视上确认安装 $label")
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showInstallPermission(context: Context) {
        notify(
            context,
            INSTALL_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_INSTALL)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setContentTitle("需要允许安装应用")
                .setContentText("请在商店中允许安装未知应用后重试")
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showInstallResult(context: Context, label: String, success: Boolean, message: String?) {
        val text = when {
            success -> "$label 已安装"
            !message.isNullOrBlank() && message != "null" -> "$label 安装失败：$message"
            else -> "$label 安装失败"
        }
        notify(
            context,
            INSTALL_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_INSTALL)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setContentTitle(if (success) "安装完成" else "安装未完成")
                .setContentText(text)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(context: Context, id: Int, notification: Notification) {
        context.getSystemService(NotificationManager::class.java)
            ?.notify(id, notification)
    }
}
