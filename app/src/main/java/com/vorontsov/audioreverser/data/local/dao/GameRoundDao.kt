package com.vorontsov.audioreverser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRoundDao {

    @Query("SELECT * FROM game_rounds WHERE pending_deletion = 0 ORDER BY created_at DESC")
    fun observeRounds(): Flow<List<GameRoundEntity>>

    @Query("SELECT * FROM game_rounds WHERE id = :roundId AND pending_deletion = 0")
    fun observeRound(roundId: String): Flow<GameRoundEntity?>

    @Query("SELECT * FROM game_rounds WHERE id = :roundId")
    suspend fun getRound(roundId: String): GameRoundEntity?

    @Query("SELECT id FROM game_rounds")
    suspend fun getAllRoundIds(): List<String>

    @Query("SELECT * FROM game_rounds WHERE pending_deletion = 1")
    suspend fun getRoundsPendingDeletion(): List<GameRoundEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(round: GameRoundEntity)

    @Update
    suspend fun update(round: GameRoundEntity)

    @Query(
        """
        UPDATE game_rounds
        SET status = :status, updated_at = :updatedAt
        WHERE id = :roundId
        """,
    )
    suspend fun updateStatus(roundId: String, status: String, updatedAt: Long)

    @Query(
        """
        UPDATE game_rounds
        SET title = :title, updated_at = :updatedAt
        WHERE id = :roundId
        """,
    )
    suspend fun rename(roundId: String, title: String, updatedAt: Long)

    @Query(
        """
        UPDATE game_rounds
        SET secret_original_path = :originalPath,
            secret_reversed_path = :reversedPath,
            secret_duration_ms = :durationMs,
            updated_at = :updatedAt
        WHERE id = :roundId
        """,
    )
    suspend fun setSecretAudio(
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
        updatedAt: Long,
    )

    @Query("UPDATE game_rounds SET pending_deletion = 1 WHERE id = :roundId")
    suspend fun markPendingDeletion(roundId: String)

    @Query("DELETE FROM game_rounds WHERE id = :roundId")
    suspend fun deleteById(roundId: String)
}
