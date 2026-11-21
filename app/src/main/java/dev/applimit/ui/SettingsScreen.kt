package dev.applimit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.applimit.R
import dev.applimit.data.limitStore
import kotlinx.coroutines.launch

private enum class PinStep { None, Create, ConfirmRemove, ConfirmChange }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { context.limitStore() }
    val scope = rememberCoroutineScope()
    val pinEnabled by store.pinEnabled.collectAsState(initial = false)
    var step by remember { mutableStateOf(PinStep.None) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pin_explanation), style = MaterialTheme.typography.bodyMedium)
                    if (pinEnabled) {
                        OutlinedButton(onClick = { step = PinStep.ConfirmChange }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pin_change))
                        }
                        OutlinedButton(onClick = { step = PinStep.ConfirmRemove }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pin_turn_off))
                        }
                    } else {
                        Button(onClick = { step = PinStep.Create }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pin_turn_on))
                        }
                    }
                }
            }
        }
    }

    val close = { step = PinStep.None }
    when (step) {
        PinStep.None -> Unit
        PinStep.Create -> NewPinDialog(onSubmit = { scope.launch { store.setPin(it); close() } }, onDismiss = close)
        PinStep.ConfirmRemove -> PinDialog(
            title = stringResource(R.string.pin_turn_off),
            onSubmit = { pin -> store.checkPin(pin).also { if (it) { store.setPin(null); close() } } },
            onDismiss = close,
        )
        PinStep.ConfirmChange -> PinDialog(
            title = stringResource(R.string.pin_change),
            onSubmit = { pin -> store.checkPin(pin).also { if (it) step = PinStep.Create } },
            onDismiss = close,
        )
    }
}
