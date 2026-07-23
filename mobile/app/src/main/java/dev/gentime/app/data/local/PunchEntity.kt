package dev.gentime.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued punch. Written to Room the instant the user taps, before any
 * network call. `clientEventId` is the idempotency key the server dedupes on,
 * so retries are always safe.
 */
@Entity(tableName = "punch_queue")
data class PunchEntity(
    @PrimaryKey val clientEventId: String,
    val eventType: String,     // check_in | check_out
    val eventAt: String,       // ISO-8601 device timestamp
    val lat: Double?,
    val lng: Double?,
    val accuracyM: Double?,
    val status: String = STATUS_QUEUED,   // queued | synced | error
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_SYNCED = "synced"
        const val STATUS_ERROR = "error"
    }
}
