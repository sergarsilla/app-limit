package dev.applimit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.applimit.data.hasUsageAccess
import dev.applimit.data.isServiceEnabled

class MainActivity : ComponentActivity() {

    // Permissions are granted in system settings, so they are re-read every time the user comes back.
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App(resumes) } }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}

@Composable
private fun App(resumes: Int) {
    val context = LocalContext.current
    val serviceEnabled = remember(resumes) { context.isServiceEnabled() }
    val usageAccess = remember(resumes) { context.hasUsageAccess() }
    var settings by rememberSaveable { mutableStateOf(false) }
    if (serviceEnabled && usageAccess) {
        if (settings) SettingsScreen(onBack = { settings = false }) else AppListScreen(resumes) { settings = true }
    } else {
        SetupScreen(serviceEnabled, usageAccess)
    }
}
