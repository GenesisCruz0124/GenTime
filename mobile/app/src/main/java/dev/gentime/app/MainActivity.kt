package dev.gentime.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
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
 */
class MainActivity : FragmentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
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
