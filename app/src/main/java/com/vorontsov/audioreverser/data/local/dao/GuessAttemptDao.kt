package com.vorontsov.audioreverser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GuessAttemptDao {

    @Query(
        """
        SELECT * FROM guess_attempts
        WHERE round_id = :roundId AND pending_deletion = 0
        ORDER BY created_at ASC, id ASC
        """,
    )
    fun observeAttempts(roundId: String): Flow<List<GuessAttemptEntity>>

    @Query(
        """
        SELECT * FROM guess_attempts
        WHERE round_id = :roundId AND pending_deletion = 0
        ORDER BY created_at ASC, id ASC
        """,
    )
    suspend fun getAttempts(roundId: String): List<GuessAttemptEntity>

    @Query("SELECT COUNT(*) FROM guess_attempts WHERE round_id = :roundId AND pending_deletion = 0")
    fun observeAttemptCount(roundId: String): Flow<Int>

    @Query(
        """
        SELECT round_id, COUNT(*) AS count FROM guess_attempts
        WHERE pending_deletion = 0
        GROUP BY round_id
        """,
    )
    fun observeAllCounts(): Flow<List<RoundAttemptCount>>

    @Query("SELECT DISTINCT round_id FROM guess_attempts WHERE pending_deletion = 0")
    suspend fun getRoundIdsWithAttempts(): List<String>

    @Query("SELECT * FROM guess_attempts WHERE id = :attemptId")
    suspend fun getAttempt(attemptId: String): GuessAttemptEntity?

    @Query("SELECT * FROM guess_attempts WHERE pending_deletion = 1")
    suspend fun getAttemptsPendingDeletion(): List<GuessAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attempt: GuessAttemptEntity)

    @Query(
        """
        UPDATE guess_attempts
        SET title = :title
        WHERE id = :attemptId
        """,
    )
    suspend fun rename(attemptId: String, title: String)

    @Query("UPDATE guess_attempts SET pending_deletion = 1 WHERE id = :attemptId")
    suspend fun markPendingDeletion(attemptId: String)

    @Query("DELETE FROM guess_attempts WHERE id = :attemptId")
    suspend fun deleteById(attemptId: String)
}
