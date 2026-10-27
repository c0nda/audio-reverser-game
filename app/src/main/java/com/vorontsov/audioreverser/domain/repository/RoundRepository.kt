package com.vorontsov.audioreverser.domain.repository

import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.model.GuessAttempt
import com.vorontsov.audioreverser.domain.model.RoundDetails
import com.vorontsov.audioreverser.domain.model.RoundSummary
import kotlinx.coroutines.flow.Flow

interface RoundRepository {

    fun observeRounds(): Flow<List<RoundSummary>>

    fun observeRoundDetails(roundId: String): Flow<RoundDetails?>

    suspend fun createRound(): GameRound

    suspend fun getRound(roundId: String): GameRound?

    suspend fun renameRound(roundId: String, title: String)

    suspend fun deleteRound(roundId: String)

    suspend fun completeRound(roundId: String)

    suspend fun saveSecretAudio(
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    )

    suspend fun addAttempt(
        attemptId: String,
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    ): GuessAttempt

    suspend fun renameAttempt(attemptId: String, title: String)

    suspend fun deleteAttempt(attemptId: String)

    suspend fun getAttemptAssetPath(attemptId: String, reversed: Boolean): String?

    suspend fun cleanupInterruptedDeletions()
}
