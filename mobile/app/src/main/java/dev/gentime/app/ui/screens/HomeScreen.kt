package dev.gentime.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.gentime.app.biometric.BiometricAuth
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.Session
import dev.gentime.app.location.LocationHelper
import dev.gentime.app.location.TrackingService
import kotlinx.coroutines.launch

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(repo: AttendanceRepository, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    val session = remember { Session(activity) }
    var onClock by remember { mutableStateOf(session.onTheClock) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val pending by repo.pendingCount.collectAsState(initial = 0)

    fun doPunch() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val ok = if (BiometricAuth.canAuthenticate(activity)) {
                    BiometricAuth.authenticate(activity)
                } else true // devices without enrolled biometrics fall through
                if (!ok) { message = "Authentication cancelled"; return@launch }

                val type = if (onClock) "check_out" else "check_in"
                val fix = LocationHelper.currentFix(activity)
                repo.punch(type, fix?.lat, fix?.lng, fix?.accuracyM)

                onClock = !onClock
                if (onClock) {
                    // A location foreground service can only start when location
                    // permission is granted (Android 14+). Tracking is optional
                    // — the punch is already recorded either way.
                    if (hasLocationPermission(activity)) {
                        runCatching { TrackingService.start(activity) }
                    }
                } else {
                    runCatching { TrackingService.stop(activity) }
                }
                message = when {
                    onClock && !hasLocationPermission(activity) ->
                        "Checked in (grant location to share your position while on the clock)"
                    onClock -> "Checked in"
                    else -> "Checked out"
                }
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (onClock) "You're on the clock" else "You're off the clock",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pending > 0) "$pending punch(es) pending sync" else "All synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { doPunch() },
            enabled = !busy,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (onClock) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(180.dp),
        ) {
            if (busy) CircularProgressIndicator(color = Color.White)
            else Text(
                if (onClock) "Check Out" else "Check In",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(24.dp))
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
