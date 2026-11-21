package dev.applimit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.applimit.R
import dev.applimit.ui.appLabel

object Notifications {

    private const val CHANNEL = "limit_warning"

    fun warn(context: Context, pkg: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.warning_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_hourglass)
            .setContentTitle(context.getString(R.string.warning_title, context.appLabel(pkg)))
            .setContentText(context.getString(R.string.warning_text))
            .setTimeoutAfter(WARNING_TIMEOUT)
            .setAutoCancel(true)
            .build()
        manager.notify(pkg.hashCode(), notification)
    }

    private const val WARNING_TIMEOUT = 5 * 60_000L
}
