package dev.gentime.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.gentime.app.data.AttendanceRepository

/**
 * Drains the offline punch queue through the validated RPC. On failure it
 * returns retry(), so WorkManager re-runs it with exponential backoff once the
 * network constraint is met again.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = AttendanceRepository(applicationContext)
        return try {
            if (repo.syncPending()) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
