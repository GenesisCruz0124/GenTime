package dev.gentime.app.data

import android.content.Context
import dev.gentime.app.data.local.AppDatabase
import dev.gentime.app.data.local.PunchEntity
import dev.gentime.app.data.model.DailyRecord
import dev.gentime.app.data.model.Profile
import dev.gentime.app.data.model.SubmitEventArgs
import dev.gentime.app.sync.SyncScheduler
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One of today's punches, formatted for display in the local time zone. */
data class TodayPunch(val type: String, val timeLocal: String, val synced: Boolean)

/**
 * Single entry point for attendance. A punch is written to Room first
 * (offline-first) and a WorkManager sync is scheduled; the network call is a
 * best-effort optimisation, never a precondition.
 */
class AttendanceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val session = Session(appContext)
    private val dao = db.punchDao()

    val pendingCount: Flow<Int> = dao.pendingCount()

    /** Raw error text of the most recent failed sync, or null if none. */
    val lastSyncError: Flow<String?> = dao.lastError()

    suspend fun currentProfile(): Profile? {
        val uid = Supa.client.auth.currentUserOrNull()?.id ?: return null
        return Supa.client.postgrest["profiles"]
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()
    }

    /** Enqueue a punch and kick the sync worker. Returns the client event id. */
    suspend fun punch(
        eventType: String,
        lat: Double?,
        lng: Double?,
        accuracyM: Double?,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.enqueue(
            PunchEntity(
                clientEventId = id,
                eventType = eventType,
                eventAt = Instant.now().toString(),
                lat = lat,
                lng = lng,
                accuracyM = accuracyM,
            ),
        )
        session.onTheClock = eventType == "check_in"
        SyncScheduler.enqueue(appContext)
        return id
    }

    /** Drain the queue via the validated RPC. Called by the sync worker. */
    suspend fun syncPending(): Boolean {
        val pending = dao.pending()
        var allOk = true
        for (p in pending) {
            try {
                Supa.client.postgrest.rpc(
                    "submit_attendance_event",
                    SubmitEventArgs(
                        clientEventId = p.clientEventId,
                        eventType = p.eventType,
                        eventAt = p.eventAt,
                        lat = p.lat,
                        lng = p.lng,
                        accuracyM = p.accuracyM,
                        deviceId = session.deviceId,
                        isOfflineSync = p.attempts > 0,
                    ),
                )
                dao.mark(p.clientEventId, PunchEntity.STATUS_SYNCED, null)
            } catch (e: Exception) {
                allOk = false
                dao.mark(p.clientEventId, PunchEntity.STATUS_ERROR, e.message)
            }
        }
        return allOk
    }

    /**
     * Today's check-in/out punches from the on-device queue (works offline and
     * before the nightly DTR job runs), oldest first. This is what backs the
     * "Today" card, so a user always sees their login for the current day.
     */
    suspend fun todayPunches(): List<TodayPunch> {
        val manila = ZoneId.of("Asia/Manila")
        val today = LocalDate.now(manila).toString()
        val fmt = DateTimeFormatter.ofPattern("h:mm a")
        return dao.recent().mapNotNull { p ->
            val zoned = runCatching {
                Instant.parse(p.eventAt).atZone(manila)
            }.getOrNull() ?: return@mapNotNull null
            if (zoned.toLocalDate().toString() != today) return@mapNotNull null
            TodayPunch(
                type = p.eventType,
                timeLocal = zoned.format(fmt),
                synced = p.status == PunchEntity.STATUS_SYNCED,
            )
        }.reversed()  // dao.recent() is newest-first; show chronological
    }

    suspend fun myRecords(limit: Int = 60): List<DailyRecord> {
        val uid = Supa.client.auth.currentUserOrNull()?.id ?: return emptyList()
        return Supa.client.postgrest["daily_records"]
            .select {
                filter { eq("profile_id", uid) }
                order("work_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList()
    }
}
