package dev.gentime.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.gentime.app.biometric.BiometricAuth
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.Session
import dev.gentime.app.location.LocationHelper
import dev.gentime.app.location.TrackingService
import dev.gentime.app.ui.components.OutlineCardModifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live wall-clock: big time + full date, ticking each second. */
@Composable
private fun LiveClock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val date = Date(now)
    // Attendance is anchored to Philippine Standard Time regardless of the
    // device's own time zone, so everyone sees the same clock.
    val manila = TimeZone.getTimeZone("Asia/Manila")
    val time = remember { SimpleDateFormat("h:mm:ss a", Locale.US).apply { timeZone = manila } }
    val day = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).apply { timeZone = manila } }
    Text(
        time.format(date),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        day.format(date),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
}

/**
 * Turns a raw sync error into a plain-language cause the user can act on. Only
 * permanent failures are surfaced; transient (network) errors return null so we
 * don't alarm the user over something the automatic retry will clear.
 */
private fun syncErrorMessage(raw: String?): String? {
    raw ?: return null
    return when {
        raw.contains("device_mismatch") ->
            "This device isn't registered — ask your admin to reset your device."
        raw.contains("profile_inactive") ->
            "Your account is inactive — contact your admin."
        else -> null
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(repo: AttendanceRepository, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    val session = remember { Session(activity) }
    var onClock by remember { mutableStateOf(session.onTheClock) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Shown when the OS won't display the permission dialog (previously denied
    // with "don't ask again") — offers a one-tap jump to the app's settings.
    var showOpenSettings by remember { mutableStateOf(false) }
    val pending by repo.pendingCount.collectAsState(initial = 0)
    val syncError by repo.lastSyncError.collectAsState(initial = null)

    fun openAppSettings() {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null),
                ),
            )
        }
    }

    fun realDoPunch() {
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

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            showOpenSettings = false
            realDoPunch()  // granted → proceed
        } else {
            // Denied. If the system won't offer the dialog again
            // (shouldShowRationale is false after a denial = "don't ask
            // again"), send the user straight to app settings instead.
            val canAskAgain = activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            message = if (canAskAgain) {
                "Location is required to check in. Please allow it when asked."
            } else {
                "Location is turned off for GenTime. Tap \"Open Settings\", " +
                    "enable Location, then check in again."
            }
            showOpenSettings = !canAskAgain
        }
    }

    // Checking IN needs location (for GPS + geofence). If it isn't granted yet,
    // ask right here, then punch. Checking OUT doesn't need it.
    fun onPunchClick() {
        showOpenSettings = false
        if (!onClock && !hasLocationPermission(activity)) {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else {
            realDoPunch()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LiveClock()

        // Status: white card, thin outline, with a state dot.
        Row(
            OutlineCardModifier().fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    if (onClock) Color(0xFF059669) else MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (onClock) "On the clock" else "Off the clock",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val friendly = syncErrorMessage(syncError)
                Text(
                    when {
                        friendly != null -> friendly
                        pending > 0 -> "$pending pending sync"
                        else -> "All synced"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (friendly != null || pending > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(56.dp))

        val punchColor = if (onClock) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
        Box(
            Modifier
                .size(184.dp)
                .clip(CircleShape)
                .background(punchColor)
                .then(if (busy) Modifier else Modifier.clickable { onPunchClick() }),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    if (onClock) "Check Out" else "Check In",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (showOpenSettings) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = { openAppSettings() }) { Text("Open Settings") }
        }
    }
}
