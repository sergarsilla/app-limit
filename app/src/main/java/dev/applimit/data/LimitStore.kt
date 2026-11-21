package dev.applimit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate

/**
 * The service and the screens run in separate processes so the always-running one never loads the
 * UI toolkit; this store is shared by both and learns of the other's writes through a file observer.
 */
private object SharedStore {
    @Volatile private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
        instance ?: MultiProcessDataStoreFactory.create(
            serializer = PreferencesFileSerializer,
            produceFile = { File(context.applicationContext.filesDir, "datastore/app_limit.preferences_pb") },
        ).also { instance = it }
    }
}

fun Context.limitStore() = LimitStore(SharedStore.get(this))

/** Today's counters for one app. Entries from an earlier day read as empty, so midnight needs no alarm. */
data class AppDay(
    val usedMs: Long = 0,
    val extraMs: Long = 0,
    val oneMoreUsed: Boolean = false,
    val ignored: Boolean = false,
)

class Snapshot(private val prefs: Preferences) {
    val limits: Map<String, Int> = prefs.limits()

    fun day(pkg: String, today: LocalDate = LocalDate.now()): AppDay {
        if (prefs[DAY] != today.toString()) return AppDay()
        return AppDay(
            usedMs = prefs[usedKey(pkg)] ?: 0,
            extraMs = prefs[extraKey(pkg)] ?: 0,
            oneMoreUsed = prefs[oneMoreKey(pkg)] ?: false,
            ignored = prefs[ignoredKey(pkg)] ?: false,
        )
    }
}

class LimitStore internal constructor(private val dataStore: DataStore<Preferences>) {

    /** Daily limit in minutes per package. */
    val limits: Flow<Map<String, Int>> = dataStore.data.map { it.limits() }.distinctUntilChanged()

    val snapshots: Flow<Snapshot> = dataStore.data.map(::Snapshot)

    suspend fun snapshot() = Snapshot(dataStore.data.first())

    suspend fun setLimit(pkg: String, minutes: Int?) {
        dataStore.edit { if (minutes == null) it.remove(limitKey(pkg)) else it[limitKey(pkg)] = minutes }
    }

    /** Usage measured by the service, kept as a fallback for when the system history is unavailable. */
    suspend fun addUsage(pkg: String, day: LocalDate, ms: Long) = editToday(day) {
        it[usedKey(pkg)] = (it[usedKey(pkg)] ?: 0) + ms
    }

    suspend fun grantExtra(pkg: String, ms: Long, oneMore: Boolean = false) = editToday(LocalDate.now()) {
        it[extraKey(pkg)] = (it[extraKey(pkg)] ?: 0) + ms
        if (oneMore) it[oneMoreKey(pkg)] = true
    }

    suspend fun ignoreToday(pkg: String) = editToday(LocalDate.now()) { it[ignoredKey(pkg)] = true }

    /** Applies [block] only if [day] is still today, clearing earlier days' counters first. */
    private suspend fun editToday(day: LocalDate, block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            val today = LocalDate.now()
            if (prefs[DAY] != today.toString()) {
                prefs.asMap().keys.filter { it.name.startsWith(DAILY_PREFIX) }.forEach { prefs.remove(it) }
                prefs[DAY] = today.toString()
            }
            if (day == today) block(prefs)
        }
    }
}

private const val LIMIT_PREFIX = "limit/"
private const val DAILY_PREFIX = "day/"
private val DAY = stringPreferencesKey("day")

private fun limitKey(pkg: String) = intPreferencesKey(LIMIT_PREFIX + pkg)
private fun usedKey(pkg: String) = longPreferencesKey("${DAILY_PREFIX}used/$pkg")
private fun extraKey(pkg: String) = longPreferencesKey("${DAILY_PREFIX}extra/$pkg")
private fun oneMoreKey(pkg: String) = booleanPreferencesKey("${DAILY_PREFIX}one_more/$pkg")
private fun ignoredKey(pkg: String) = booleanPreferencesKey("${DAILY_PREFIX}ignored/$pkg")

private fun Preferences.limits(): Map<String, Int> = asMap().entries
    .filter { it.key.name.startsWith(LIMIT_PREFIX) }
    .associate { it.key.name.removePrefix(LIMIT_PREFIX) to it.value as Int }
