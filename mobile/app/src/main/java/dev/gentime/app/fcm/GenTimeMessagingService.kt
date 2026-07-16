package dev.gentime.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.gentime.app.data.Supa
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GenTimeMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Persist the FCM token to the caller's profile so the server can target it. */
    override fun onNewToken(token: String) {
        val uid = Supa.client.auth.currentUserOrNull()?.id ?: return
        scope.launch {
            runCatching {
                Supa.client.postgrest["profiles"].update(
                    buildJsonObject { put("fcm_token", token) },
                ) { filter { eq("id", uid) } }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        showNotification(
            title = n?.title ?: "GenTime",
            body = n?.body ?: message.data["body"] ?: "",
        )
    }

    private fun showNotification(title: String, body: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        mgr.notify(System.currentTimeMillis().toInt(), notif)
    }

    companion object {
        const val CHANNEL_ID = "gentime_alerts"
    }
}
