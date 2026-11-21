package dev.applimit.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.applimit.data.TodayUsage
import dev.applimit.data.limitStore
import dev.applimit.ui.LimitReachedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Learns which app is in front from window events instead of polling.
 *
 * Outside limited apps the system only delivers events from limited packages, so this service
 * is not woken while other apps are used. Inside a limited app it listens to every package until
 * the next window change, because leaving is only visible as an event from another package.
 * Time runs out on one delayed message per session, cancelled on leaving or on screen-off.
 */
class LimitService : AccessibilityService() {

    private class Session(val pkg: String, val day: LocalDate, val startedAt: Long, val timed: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store by lazy { limitStore() }

    private var limited: Set<String> = emptySet()
    private var foreground: String? = null
    private var session: Session? = null
    private var sessionJob: Job? = null
    private var pausedByScreenOff: String? = null
    private var listeningToAll = false
    private var overlays: Set<String> = emptySet()
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> onScreenUsable()
                Intent.ACTION_SCREEN_ON ->
                    if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) onScreenUsable()
            }
        }
    }

    override fun onServiceConnected() {
        listenToLimitedOnly()
        scope.launch {
            store.limits.collect { limits ->
                limited = limits.keys
                Log.d(TAG, "limits $limits")
                val current = foreground
                endSession()
                if (current != null && current in limited) begin(current) else listenToLimitedOnly()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == foreground || pkg in overlays) return
        Log.d(TAG, "front $pkg")
        foreground = pkg
        pausedByScreenOff = null
        endSession()
        if (pkg in limited) begin(pkg) else listenToLimitedOnly()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        endSession()
        unregisterScreenReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(pkg: String) {
        listenToAll()
        sessionJob = scope.launch {
            val snapshot = store.snapshot()
            val limitMin = snapshot.limits[pkg] ?: return@launch
            val today = LocalDate.now()
            val day = snapshot.day(pkg, today)
            if (day.ignored) {
                session = Session(pkg, today, SystemClock.elapsedRealtime(), timed = false)
                scheduleMidnight()
                return@launch
            }
            val history = withContext(Dispatchers.Default) { TodayUsage.query(this@LimitService, listOf(pkg))[pkg] }
            val used = maxOf(history ?: 0, day.usedMs)
            val remaining = limitMin * MINUTE + day.extraMs - used
            Log.d(TAG, "begin $pkg used=${used / 1000}s (history=${history?.div(1000)}s) remaining=${remaining / 1000}s")
            if (remaining <= 0) {
                block(pkg)
                return@launch
            }
            session = Session(pkg, today, SystemClock.elapsedRealtime(), timed = true)
            handler.postDelayed({ block(pkg) }, remaining)
            if (remaining > WARNING_BEFORE) {
                handler.postDelayed({ Notifications.warn(this@LimitService, pkg) }, remaining - WARNING_BEFORE)
            }
            scheduleMidnight()
        }
    }

    private fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        handler.removeCallbacksAndMessages(null)
        val ended = session ?: return
        session = null
        if (!ended.timed) return
        val ms = SystemClock.elapsedRealtime() - ended.startedAt
        Log.d(TAG, "end ${ended.pkg} after ${ms / 1000}s")
        scope.launch { store.addUsage(ended.pkg, ended.day, ms) }
    }

    /**
     * Covers the app with the limit screen, whose task sits on top of home so leaving it lands on
     * the home screen. A separate GLOBAL_ACTION_HOME raced with this launch and could hide the screen.
     */
    private fun block(pkg: String) {
        Log.d(TAG, "block $pkg")
        endSession()
        startActivity(LimitReachedActivity.intent(this, pkg))
    }

    /** A session running at midnight restarts so it is measured against the new day. */
    private fun scheduleMidnight() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        handler.postDelayed({
            val pkg = session?.pkg ?: return@postDelayed
            endSession()
            begin(pkg)
        }, Duration.between(now, midnight).toMillis() + MIDNIGHT_MARGIN)
    }

    private fun onScreenOff() {
        val pkg = foreground?.takeIf { it in limited } ?: return
        Log.d(TAG, "screen off in $pkg")
        endSession()
        pausedByScreenOff = pkg
        foreground = null
    }

    /** Unlocking may return to the limited app without any window event, so resume explicitly. */
    private fun onScreenUsable() {
        val pkg = pausedByScreenOff ?: return
        pausedByScreenOff = null
        if (foreground != null) return
        Log.d(TAG, "screen back in $pkg")
        foreground = pkg
        begin(pkg)
    }

    private fun listenToAll() {
        if (listeningToAll) return
        listeningToAll = true
        overlays = setOfNotNull(SYSTEM_UI, currentKeyboard())
        setPackages(null)
        registerScreenReceiver()
    }

    private fun listenToLimitedOnly() {
        listeningToAll = false
        unregisterScreenReceiver()
        // An empty filter means "every package", so with nothing limited listen only to this app.
        setPackages(limited.ifEmpty { setOf(packageName) })
    }

    private fun setPackages(packages: Set<String>?) {
        serviceInfo = serviceInfo.apply { packageNames = packages?.toTypedArray() }
    }

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        unregisterReceiver(screenReceiver)
        screenReceiverRegistered = false
    }

    private companion object {
        const val TAG = "AppLimit"
        const val SYSTEM_UI = "com.android.systemui"
        const val MINUTE = 60_000L
        const val WARNING_BEFORE = 5 * MINUTE
        const val MIDNIGHT_MARGIN = 1_000L
    }
}
