package dev.gentime.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.LaunchedEffect
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
 * Compose app.
 *
 * Permissions are requested from Compose via rememberLauncherForActivityResult
 * (see RequestStartupPermissions). Launching an ActivityResultLauncher directly
 * from onCreate crashed on some OEM/Android-16 builds; the Compose launcher is
 * lifecycle-safe. Background location is intentionally NOT requested.
 */
class MainActivity : FragmentActivity() {

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

        setContent {
            GenTimeTheme {
                RequestStartupPermissions()
                val vm: AppViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                AppRoot(state = state, vm = vm, activity = this)
            }
        }
    }
}

/** Requests fine location (+ notifications on 13+) once, safely, from Compose. */
@Composable
private fun RequestStartupPermissions() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* result ignored; features degrade gracefully if denied */ }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        runCatching { launcher.launch(perms) }
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
