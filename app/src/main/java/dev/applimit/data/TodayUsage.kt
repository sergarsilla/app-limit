package dev.applimit.data

import android.app.usage.UsageEvents.Event
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/**
 * Foreground time since local midnight, rebuilt from the history Android already records.
 * Called once when a limited app comes to the front and when the app list is shown; never on a timer.
 */
object TodayUsage {

    private val TYPES = intArrayOf(
        Event.ACTIVITY_RESUMED, Event.ACTIVITY_PAUSED, Event.ACTIVITY_STOPPED,
        Event.SCREEN_NON_INTERACTIVE, Event.DEVICE_SHUTDOWN,
    )

    // Screen-off and shutdown are reported under this package.
    private const val SYSTEM_PACKAGE = "android"

    // Events are read from a little before the window so an app already open at its start is counted
    // from that start; a pause with no earlier resume is not evidence of anything.
    private const val LOOKBACK = 3 * 60 * 60_000L

    fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Returns an empty map when usage access has not been granted. */
    fun query(
        context: Context,
        packages: Collection<String>? = null,
        begin: Long = startOfToday(),
        end: Long = System.currentTimeMillis(),
    ): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val query = UsageEventsQuery.Builder(begin - LOOKBACK, end).setEventTypes(*TYPES)
            if (packages != null) query.setPackageNames(*(packages + SYSTEM_PACKAGE).toTypedArray())
            usm.queryEvents(query.build())
        } else {
            usm.queryEvents(begin - LOOKBACK, end)
        } ?: return emptyMap()

        val apps = HashMap<String, Tracker>()
        val event = Event()
        while (events.getNextEvent(event)) {
            when (event.eventType) {
                Event.SCREEN_NON_INTERACTIVE, Event.DEVICE_SHUTDOWN -> {
                    apps.values.forEach { it.closeAll(event.timeStamp) }
                }
                Event.ACTIVITY_RESUMED, Event.ACTIVITY_PAUSED, Event.ACTIVITY_STOPPED -> {
                    val pkg = event.packageName
                    if (packages != null && pkg !in packages) continue
                    val app = apps.getOrPut(pkg) { Tracker(begin) }
                    val activity = event.className.orEmpty()
                    when (event.eventType) {
                        Event.ACTIVITY_RESUMED -> app.resumed(activity, event.timeStamp)
                        Event.ACTIVITY_PAUSED -> app.paused(activity, event.timeStamp)
                        else -> app.stopped(activity, event.timeStamp)
                    }
                }
            }
        }
        return apps.mapValues { it.value.totalUntil(end) }
    }

    /**
     * An app is in the foreground while any of its activities is resumed. The public API has no
     * instance id, so activities are counted per class: two screens of the same class overlap briefly,
     * and some apps go from resumed straight to stopped with no pause in between.
     */
    private class Tracker(private val begin: Long) {
        private val resumed = HashMap<String, Int>()
        private val pausedNotStopped = HashMap<String, Int>()
        private var open = 0
        private var since = 0L
        private var total = 0L

        fun resumed(activity: String, time: Long) {
            if (open == 0) since = time
            resumed.increment(activity)
            open++
        }

        fun paused(activity: String, time: Long) {
            if (!resumed.decrement(activity)) return
            pausedNotStopped.increment(activity)
            if (--open == 0) add(time)
        }

        /** A stop normally follows a pause already counted; only a stop with none pending ends a resume. */
        fun stopped(activity: String, time: Long) {
            if (pausedNotStopped.decrement(activity)) return
            if (resumed.decrement(activity) && --open == 0) add(time)
        }

        fun closeAll(time: Long) {
            if (open > 0) add(time)
            resumed.clear()
            pausedNotStopped.clear()
            open = 0
        }

        fun totalUntil(end: Long): Long {
            if (open > 0) add(end)
            return total
        }

        /** Only the part of the interval inside the window counts. */
        private fun add(until: Long) {
            total += (until - maxOf(since, begin)).coerceAtLeast(0)
        }

        private fun HashMap<String, Int>.increment(key: String) {
            this[key] = (this[key] ?: 0) + 1
        }

        private fun HashMap<String, Int>.decrement(key: String): Boolean {
            val count = this[key] ?: 0
            if (count == 0) return false
            this[key] = count - 1
            return true
        }
    }
}
