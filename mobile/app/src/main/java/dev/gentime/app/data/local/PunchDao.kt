package dev.gentime.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PunchDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(punch: PunchEntity)

    @Query("SELECT * FROM punch_queue WHERE status != 'synced' ORDER BY eventAt ASC")
    suspend fun pending(): List<PunchEntity>

    @Query("SELECT COUNT(*) FROM punch_queue WHERE status = 'queued' OR status = 'error'")
    fun pendingCount(): Flow<Int>

    // Newest punches regardless of sync status — used to surface "today" locally,
    // since the computed daily record only appears after the overnight job.
    @Query("SELECT * FROM punch_queue ORDER BY eventAt DESC LIMIT 30")
    suspend fun recent(): List<PunchEntity>

    @Query("UPDATE punch_queue SET status = :status, attempts = attempts + 1, lastError = :error WHERE clientEventId = :id")
    suspend fun mark(id: String, status: String, error: String?)
}
