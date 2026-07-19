package dev.gentime.app.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.gentime.app.data.Supa
import dev.gentime.app.data.model.PingInsert
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground location service — runs only while on-the-clock (started on
 * check-in, stopped on check-out). Emits a balanced-power ping every 5 minutes
 * to location_pings. Privacy by design: no tracking off-the-clock.
 */
class TrackingService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val uid = Supa.client.auth.currentUserOrNull()?.id ?: return
            scope.launch {
                runCatching {
                    Supa.client.postgrest["location_pings"].insert(
                        PingInsert(uid, loc.latitude, loc.longitude, loc.accuracy.toDouble()),
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground with type=location throws SecurityException if location
        // permission isn't granted (Android 14+). Fail soft — never crash the app;
        // the punch is already recorded, tracking is a best-effort extra.
        return try {
            startForeground(NOTIF_ID, buildNotification())
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS)
                .build()
            client.requestLocationUpdates(request, callback, mainLooper)
            START_STICKY
        } catch (e: Exception) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GenTime — on the clock")
            .setContentText("Sharing your location with your supervisor while you work.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "On-the-clock tracking", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "gentime_tracking"
        private const val NOTIF_ID = 42
        private const val INTERVAL_MS = 5 * 60 * 1000L

        fun start(context: Context) =
            context.startForegroundService(Intent(context, TrackingService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, TrackingService::class.java))
    }
}
