package dev.applimit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.applimit.R
import dev.applimit.data.Pin
import kotlinx.coroutines.launch

/** Asks for the current PIN; [onSubmit] returns whether it was right. */
@Composable
fun PinDialog(title: String, onSubmit: suspend (String) -> Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            PinField(pin, stringResource(R.string.pin), if (wrong) stringResource(R.string.pin_wrong) else null) {
                pin = it
                wrong = false
            }
        },
        confirmButton = {
            TextButton(
                enabled = Pin.isValid(pin) && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        wrong = !onSubmit(pin)
                        if (wrong) pin = ""
                        checking = false
                    }
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks for a new PIN twice. */
@Composable
fun NewPinDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = repeat.length >= pin.length && repeat.isNotEmpty() && repeat != pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pin_rules, Pin.MIN_LENGTH, Pin.MAX_LENGTH),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(pin, stringResource(R.string.pin), null) { pin = it }
                PinField(repeat, stringResource(R.string.pin_repeat), if (mismatch) stringResource(R.string.pin_mismatch) else null) {
                    repeat = it
                }
            }
        },
        confirmButton = {
            TextButton(enabled = Pin.isValid(pin) && pin == repeat, onClick = { onSubmit(pin) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PinField(value: String, label: String, error: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(Pin.MAX_LENGTH)) },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        // Not a password: keeps password managers from offering to save it.
        modifier = Modifier.semantics { contentDataType = ContentDataType.None },
    )
}
