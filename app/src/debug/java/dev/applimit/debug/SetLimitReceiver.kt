package dev.applimit.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.applimit.data.TodayUsage
import dev.applimit.data.limitStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Test hooks driven from a computer:
 * adb shell am broadcast -n dev.applimit/.debug.SetLimitReceiver --es package PKG --ei minutes N
 *   sets the limit (0 removes it);
 * adb shell am broadcast -n dev.applimit/.debug.SetLimitReceiver --es package PKG --ei addUsedSeconds N
 *   adds usage so a long grant can be used up without waiting for it.
 */
class SetLimitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("package") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val store = context.limitStore()
            if (intent.hasExtra("addUsedSeconds")) {
                val usedMs = maxOf(TodayUsage.query(context, listOf(pkg))[pkg] ?: 0, store.snapshot().day(pkg).usedMs)
                store.addUsage(pkg, LocalDate.now(), usedMs - store.snapshot().day(pkg).usedMs +
                    intent.getIntExtra("addUsedSeconds", 0) * 1000L)
            } else {
                store.setLimit(pkg, intent.getIntExtra("minutes", 0).takeIf { it > 0 })
            }
            pending.finish()
        }
    }
}
