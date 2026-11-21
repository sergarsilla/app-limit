package dev.applimit.debug

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dev.applimit.data.TodayUsage
import kotlin.math.abs

/**
 * Compares the usage this app computes with UsageStatsManager's own daily totals over the same window
 * (Android's daily bucket does not start at midnight):
 * adb shell am broadcast -n dev.applimit/.debug.VerifyUsageReceiver && adb logcat -d -s AppLimitVerify
 */
class VerifyUsageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val bucket = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DAY, now)
            .filter { it.totalTimeInForeground > 0 }
        val begin = bucket.maxOfOrNull { it.firstTimeStamp } ?: return
        val end = bucket.filter { it.firstTimeStamp == begin }.maxOf { it.lastTimeStamp }
        val android = bucket.filter { it.firstTimeStamp == begin }.associate { it.packageName to it.totalTimeInForeground }

        val mine = TodayUsage.query(context, begin = begin, end = end)
        var worst = 0L
        (android.keys + mine.keys).filter { (android[it] ?: 0) + (mine[it] ?: 0) >= MINUTE / 2 }
            .sortedByDescending { android[it] ?: 0 }
            .forEach { pkg ->
                val diff = (mine[pkg] ?: 0) - (android[pkg] ?: 0)
                worst = maxOf(worst, abs(diff))
                Log.i(TAG, "%-40s mine=%6ds android=%6ds diff=%+5ds".format(
                    pkg, (mine[pkg] ?: 0) / 1000, (android[pkg] ?: 0) / 1000, diff / 1000))
            }
        Log.i(TAG, "window ${java.util.Date(begin)} .. ${java.util.Date(end)} worst diff ${worst / 1000}s")

        intent.getStringExtra("dump")?.let { dumpEvents(usm, it, begin, end) }

        val started = SystemClock.elapsedRealtime()
        TodayUsage.query(context, listOf("com.zhiliaoapp.musically"))
        Log.i(TAG, "full-day query for one package took ${SystemClock.elapsedRealtime() - started} ms")
    }

    /** Every event of one package in the window, to explain a mismatch. */
    private fun dumpEvents(usm: UsageStatsManager, pkg: String, begin: Long, end: Long) {
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        val format = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)
        while (events.getNextEvent(event)) {
            if (event.packageName != pkg && event.packageName != "android") continue
            if (event.packageName == "android" && event.eventType !in SYSTEM_TYPES) continue
            Log.i(TAG, "${format.format(event.timeStamp)} type=${event.eventType} ${event.packageName} ${event.className}")
        }
    }

    private companion object {
        val SYSTEM_TYPES = setOf(15, 16, 26, 27)
        const val TAG = "AppLimitVerify"
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
