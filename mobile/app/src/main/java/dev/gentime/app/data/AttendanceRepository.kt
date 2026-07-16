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
import java.util.UUID

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
