package dev.applimit.data

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import dev.applimit.service.LimitService

fun Context.serviceComponent() = ComponentName(this, LimitService::class.java)

fun Context.isServiceEnabled(): Boolean {
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val component = serviceComponent()
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

fun Context.hasUsageAccess(): Boolean {
    val appOps = getSystemService(AppOpsManager::class.java)
    return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
        AppOpsManager.MODE_ALLOWED
}

fun Context.notificationsAllowed(): Boolean =
    getSystemService(NotificationManager::class.java).areNotificationsEnabled()
