package dev.gentime.app.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One-shot high-accuracy fix for a punch. Caller must hold location permission. */
object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context): Fix? = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                cont.resume(loc?.let { Fix(it.latitude, it.longitude, it.accuracy.toDouble()) })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    data class Fix(val lat: Double, val lng: Double, val accuracyM: Double)
}
