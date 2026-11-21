package dev.applimit.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.applimit.R
import dev.applimit.data.InstalledApp
import dev.applimit.data.TodayUsage
import dev.applimit.data.appIcon
import dev.applimit.data.launcherApps
import dev.applimit.data.limitStore
import dev.applimit.data.notificationsAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MIN_LIMIT = 5
private const val MAX_LIMIT = 240
private const val LIMIT_STEP = 5
private const val DEFAULT_LIMIT = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(resumes: Int, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.limitStore() }
    val limits by store.limits.collectAsState(initial = emptyMap())
    val pinEnabled by store.pinEnabled.collectAsState(initial = false)
    // A change that loosens a limit waits here for the PIN.
    var pendingChange by remember { mutableStateOf<Pair<InstalledApp, Int?>?>(null) }
    val apps by produceState(emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.Default) { context.launcherApps() }
    }
    var usage by remember { mutableStateOf(emptyMap<String, Long>()) }
    LaunchedEffect(resumes) { usage = withContext(Dispatchers.Default) { TodayUsage.query(context) } }

    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }
    val icons = remember { HashMap<String, ImageBitmap>() }

    val shown = remember(apps, usage, limits, query) {
        apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                compareByDescending<InstalledApp> { it.pkg in limits }
                    .thenByDescending { usage[it.pkg] ?: 0L }
                    .thenBy { it.label.lowercase() },
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { NotificationBanner(resumes) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(shown, key = { it.pkg }) { app ->
                AppRow(app, usage[app.pkg] ?: 0L, limits[app.pkg], icons) { editing = app }
            }
        }
    }

    editing?.let { app ->
        LimitSheet(
            app = app,
            current = limits[app.pkg],
            onSave = { minutes ->
                if (pinEnabled && loosens(limits[app.pkg], minutes)) {
                    pendingChange = app to minutes
                    false
                } else {
                    store.setLimit(app.pkg, minutes)
                    true
                }
            },
            onDismiss = { editing = null },
        )
    }

    pendingChange?.let { (app, minutes) ->
        PinDialog(
            title = stringResource(R.string.pin_required),
            onSubmit = { pin ->
                store.checkPin(pin).also { ok ->
                    if (ok) {
                        store.setLimit(app.pkg, minutes)
                        pendingChange = null
                        editing = null
                    }
                }
            },
            onDismiss = { pendingChange = null },
        )
    }
}

/** Removing or raising a limit is what the PIN guards; tightening one never needs it. */
private fun loosens(current: Int?, new: Int?): Boolean = current != null && (new == null || new > current)

@Composable
private fun AppRow(
    app: InstalledApp,
    usedMs: Long,
    limitMinutes: Int?,
    icons: HashMap<String, ImageBitmap>,
    onClick: () -> Unit,
) {
    val lessThanMinute = stringResource(R.string.less_than_minute)
    val used = stringResource(R.string.used_today, formatDuration(usedMs, lessThanMinute))
    val supporting = if (limitMinutes == null) {
        used
    } else {
        used + " · " + stringResource(R.string.limit_of, formatDuration(limitMinutes * 60_000L, lessThanMinute))
    }
    ListItem(
        headlineContent = {
            Text(app.label, fontWeight = if (limitMinutes != null) FontWeight.SemiBold else null)
        },
        supportingContent = { Text(supporting) },
        leadingContent = { AppIcon(app.pkg, icons) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AppIcon(pkg: String, cache: HashMap<String, ImageBitmap>) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState(cache[pkg], pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { context.appIcon(pkg, sizePx)?.asImageBitmap() }
                ?.also { cache[pkg] = it }
        }
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        ?: Spacer(Modifier.size(40.dp))
}

@Composable
private fun NotificationBanner(resumes: Int) {
    val context = LocalContext.current
    var allowed by remember(resumes) { mutableStateOf(context.notificationsAllowed()) }
    if (allowed) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        asked = true
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notifications_text))
            Button(onClick = {
                // After a refusal Android stops showing the dialog, so go to the settings page instead.
                if (asked) context.openNotificationSettings() else request.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.notifications_button)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(
    app: InstalledApp,
    current: Int?,
    onSave: suspend (Int?) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableFloatStateOf((current ?: DEFAULT_LIMIT).toFloat()) }
    val lessThanMinute = stringResource(R.string.less_than_minute)
    val haptics = LocalHapticFeedback.current

    // One tick per 5-minute step, from the slider or the buttons.
    fun change(value: Float) {
        val clamped = value.coerceIn(MIN_LIMIT.toFloat(), MAX_LIMIT.toFloat())
        if (clamped.roundToInt() != minutes.roundToInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        minutes = clamped
    }

    fun saveAndClose(value: Int?) {
        scope.launch {
            if (!onSave(value)) return@launch
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(app.label, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.daily_limit), style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = { change(minutes - LIMIT_STEP) },
                    enabled = minutes > MIN_LIMIT,
                ) { Text("−$LIMIT_STEP") }
                Text(
                    formatDuration(minutes.roundToInt() * 60_000L, lessThanMinute),
                    style = MaterialTheme.typography.displaySmall,
                )
                FilledTonalButton(
                    onClick = { change(minutes + LIMIT_STEP) },
                    enabled = minutes < MAX_LIMIT,
                ) { Text("+$LIMIT_STEP") }
            }
            Slider(
                value = minutes,
                onValueChange = ::change,
                valueRange = MIN_LIMIT.toFloat()..MAX_LIMIT.toFloat(),
                steps = (MAX_LIMIT - MIN_LIMIT) / LIMIT_STEP - 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current != null) {
                    OutlinedButton(onClick = { saveAndClose(null) }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.remove_limit))
                    }
                }
                Button(onClick = { saveAndClose(minutes.roundToInt()) }, Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
