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

    @Query("UPDATE punch_queue SET status = :status, attempts = attempts + 1, lastError = :error WHERE clientEventId = :id")
    suspend fun mark(id: String, status: String, error: String?)
}
