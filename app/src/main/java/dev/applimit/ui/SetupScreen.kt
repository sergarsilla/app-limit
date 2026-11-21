package dev.applimit.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.applimit.R
import dev.applimit.data.serviceComponent

@Composable
fun SetupScreen(serviceEnabled: Boolean, usageAccess: Boolean) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppBadge(72.dp)
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)

            SetupStep(
                number = 1,
                title = stringResource(R.string.setup_accessibility_title),
                text = stringResource(R.string.setup_accessibility_text),
                done = serviceEnabled,
                action = stringResource(R.string.setup_accessibility_button),
                onAction = { context.openAccessibilitySettings() },
            ) {
                Text(
                    stringResource(R.string.setup_restricted_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { context.openAppInfo() }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_restricted_button))
                }
            }

            SetupStep(
                number = 2,
                title = stringResource(R.string.setup_usage_title),
                text = stringResource(R.string.setup_usage_text),
                done = usageAccess,
                action = stringResource(R.string.setup_usage_button),
                onAction = { context.openUsageAccessSettings() },
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    text: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$number. $title", style = MaterialTheme.typography.titleMedium)
            if (done) {
                Text(stringResource(R.string.setup_done), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAction, Modifier.fillMaxWidth()) { Text(action) }
                extra()
            }
        }
    }
}

/** The direct link to one service's switch is reserved to the system; Settings highlights the entry instead. */
fun Context.openAccessibilitySettings() {
    val key = serviceComponent().flattenToString()
    startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(SETTINGS_HIGHLIGHT_KEY, key)
            .putExtra(SETTINGS_HIGHLIGHT_ARGS, Bundle().apply { putString(SETTINGS_HIGHLIGHT_KEY, key) }),
    )
}

private const val SETTINGS_HIGHLIGHT_KEY = ":settings:fragment_args_key"
private const val SETTINGS_HIGHLIGHT_ARGS = ":settings:show_fragment_args"

fun Context.openAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private fun Context.openUsageAccessSettings() {
    val forThisApp = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
    startFirstAvailable(forThisApp, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
}

fun Context.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

private fun Context.startFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }
}
