package dev.gentime.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gentime.app.ui.AppRoot
import dev.gentime.app.ui.AppViewModel
import dev.gentime.app.ui.theme.GenTimeTheme

/**
 * FragmentActivity is required for AndroidX BiometricPrompt. Hosts the whole
 * Compose app; permissions (fine location + notifications) are requested up
 * front. Background location is intentionally NOT requested.
 *
 * If a previous launch crashed, we show that trace FIRST (bare MaterialTheme,
 * before touching permissions or the app graph) so the error is always
 * visible on the next open regardless of where it happened.
 */
class MainActivity : FragmentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val diag = getSharedPreferences("gentime_diag", MODE_PRIVATE)
        val lastCrash = diag.getString("last_crash", null)
        if (lastCrash != null) {
            setContent {
                MaterialTheme {
                    CrashScreen(lastCrash) { diag.edit().remove("last_crash").apply(); recreate() }
                }
            }
            return
        }

        runCatching { requestPermissions() }

        setContent {
            GenTimeTheme {
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                AppRoot(state = state, vm = vm, activity = this)
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

@Composable
private fun CrashScreen(trace: String, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Startup error", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDismiss) { Text("Dismiss & retry") }
        Spacer(Modifier.height(12.dp))
        Text(
            trace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
    }
}
