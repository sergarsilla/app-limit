package dev.applimit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.applimit.R
import dev.applimit.data.appLabel
import dev.applimit.data.limitStore
import kotlinx.coroutines.launch

class LimitReachedActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        setContent {
            AppTheme {
                LimitReachedScreen(
                    label = appLabel(pkg),
                    pkg = pkg,
                    onOneMore = { grant { limitStore().grantExtra(pkg, MINUTE, oneMore = true) } },
                    onFifteenMore = { grant { limitStore().grantExtra(pkg, 15 * MINUTE) } },
                    onIgnoreToday = { grant { limitStore().ignoreToday(pkg) } },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
    }

    /** The grant is stored before the app is reopened, so the service sees it on arrival. */
    private fun grant(save: suspend () -> Unit) {
        val target = pkg
        lifecycleScope.launch {
            save()
            packageManager.getLaunchIntentForPackage(target)?.let(::startActivity)
            finish()
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val MINUTE = 60_000L

        fun intent(context: Context, pkg: String): Intent =
            Intent(context, LimitReachedActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
    }
}

@Composable
private fun LimitReachedScreen(
    label: String,
    pkg: String,
    onOneMore: () -> Unit,
    onFifteenMore: () -> Unit,
    onIgnoreToday: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var oneMoreUsed by remember(pkg) { mutableStateOf(true) }
    LaunchedEffect(pkg) { oneMoreUsed = context.limitStore().snapshot().day(pkg).oneMoreUsed }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.limit_reached_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.limit_reached_text, label),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            if (!oneMoreUsed) {
                FilledTonalButton(onOneMore, Modifier.fillMaxWidth()) { Text(stringResource(R.string.one_more_minute)) }
            }
            FilledTonalButton(onFifteenMore, Modifier.fillMaxWidth()) { Text(stringResource(R.string.fifteen_more_minutes)) }
            OutlinedButton(onIgnoreToday, Modifier.fillMaxWidth()) { Text(stringResource(R.string.ignore_today)) }
            Button(onClose, Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
    }
}
