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
        Event.ACTIVITY_RESUMED, Event.ACTIVITY_PAUSED, Event.ACTIVITY_STOPPED, Event.DEVICE_SHUTDOWN,
    )

    // Shutdown is reported under this package; without it an app open at power-off would count
    // as open for the whole time the phone was off.
    private const val SYSTEM_PACKAGE = "android"

    fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Returns an empty map when usage access has not been granted. */
    fun query(context: Context, packages: Collection<String>? = null): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val begin = startOfToday()
        val end = System.currentTimeMillis()
        val events = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val query = UsageEventsQuery.Builder(begin, end).setEventTypes(*TYPES)
            if (packages != null) query.setPackageNames(*(packages + SYSTEM_PACKAGE).toTypedArray())
            usm.queryEvents(query.build())
        } else {
            usm.queryEvents(begin, end)
        } ?: return emptyMap()

        val apps = HashMap<String, Tracker>()
        val event = Event()
        while (events.getNextEvent(event)) {
            val type = event.eventType
            if (type !in TYPES) continue
            if (type == Event.DEVICE_SHUTDOWN) {
                apps.values.forEach { it.closeAll(event.timeStamp) }
                continue
            }
            val pkg = event.packageName
            if (packages != null && pkg !in packages) continue
            apps.getOrPut(pkg) { Tracker(begin) }.on(type, event.className.orEmpty(), event.timeStamp)
        }
        return apps.mapValues { it.value.totalUntil(end) }
    }

    /** An app is in the foreground while any of its activities is resumed. */
    private class Tracker(private val dayStart: Long) {
        private val resumed = HashSet<String>()
        private var since = 0L
        private var total = 0L
        private var seen = false

        fun on(type: Int, activity: String, time: Long) {
            when (type) {
                Event.ACTIVITY_RESUMED -> {
                    if (resumed.isEmpty()) since = time
                    resumed += activity
                }
                else -> when {
                    resumed.remove(activity) -> if (resumed.isEmpty()) total += time - since
                    // Opened before midnight and still open: count from the start of the day.
                    !seen && type == Event.ACTIVITY_PAUSED -> total += time - dayStart
                }
            }
            seen = true
        }

        fun closeAll(time: Long) {
            if (resumed.isNotEmpty()) total += time - since
            resumed.clear()
            seen = true
        }

        fun totalUntil(end: Long) = total + if (resumed.isNotEmpty()) end - since else 0
    }
}
