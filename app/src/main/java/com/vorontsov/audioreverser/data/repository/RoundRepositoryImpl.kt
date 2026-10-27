package com.vorontsov.audioreverser.data.repository

import androidx.room.withTransaction
import com.vorontsov.audioreverser.data.local.AudioReverserDatabase
import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity
import com.vorontsov.audioreverser.data.local.mapper.secretOriginalAsset
import com.vorontsov.audioreverser.data.local.mapper.secretReversedAsset
import com.vorontsov.audioreverser.data.local.mapper.toAttemptsWithNumbers
import com.vorontsov.audioreverser.data.local.mapper.toDomain
import com.vorontsov.audioreverser.data.local.mapper.toRoundStatus
import com.vorontsov.audioreverser.data.local.mapper.toStorage
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.model.GuessAttempt
import com.vorontsov.audioreverser.domain.model.RoundDetails
import com.vorontsov.audioreverser.domain.model.RoundStatus
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoundRepositoryImpl @Inject constructor(
    private val database: AudioReverserDatabase,
    private val fileStore: RecordingFileStore,
) : RoundRepository {

    private val roundDao get() = database.gameRoundDao()
    private val attemptDao get() = database.guessAttemptDao()

    override fun observeRounds(): Flow<List<RoundSummary>> =
        combine(
            roundDao.observeRounds(),
            attemptDao.observeAllCounts(),
        ) { rounds, counts ->
            val countsById = counts.associate { it.roundId to it.count }
            rounds.map { round ->
                RoundSummary(
                    round = round.toDomain(),
                    attemptCount = countsById[round.id] ?: 0,
                )
            }
        }

    override fun observeRoundDetails(roundId: String): Flow<RoundDetails?> =
        combine(
            roundDao.observeRound(roundId),
            attemptDao.observeAttempts(roundId),
        ) { round, attempts ->
            round?.let {
                RoundDetails(
                    round = it.toDomain(),
                    secretReversed = it.secretReversedAsset(),
                    secretOriginal = it.secretOriginalAsset(),
                    attempts = attempts.toAttemptsWithNumbers(),
                )
            }
        }

    override suspend fun createRound(): GameRound {
        val now = System.currentTimeMillis()
        val entity = GameRoundEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            status = RoundStatus.IN_PROGRESS.toStorage(),
            createdAt = now,
            updatedAt = now,
            secretOriginalPath = null,
            secretReversedPath = null,
            secretDurationMs = null,
        )
        roundDao.insert(entity)
        fileStore.roundDir(entity.id).mkdirs()
        return entity.toDomain()
    }

    override suspend fun getRound(roundId: String): GameRound? =
        roundDao.getRound(roundId)?.toDomain()

    override suspend fun renameRound(roundId: String, title: String) {
        roundDao.rename(roundId, title, System.currentTimeMillis())
    }

    override suspend fun deleteRound(roundId: String) {
        // Two-phase delete: hide the row first so it disappears from every query
        // immediately, then remove files, then the row. If the process dies at any
        // point the startup sweeper finishes the job from the pending_deletion marker.
        val attempts = attemptDao.getAttempts(roundId)
        database.withTransaction {
            roundDao.markPendingDeletion(roundId)
            attempts.forEach { attemptDao.markPendingDeletion(it.id) }
        }
        fileStore.deleteRoundDir(roundId)
        database.withTransaction {
            attemptDao.getAttemptsPendingDeletion()
                .filter { it.roundId == roundId }
                .forEach { attemptDao.deleteById(it.id) }
            roundDao.deleteById(roundId)
        }
    }

    override suspend fun completeRound(roundId: String) {
        roundDao.updateStatus(
            roundId = roundId,
            status = RoundStatus.COMPLETED.toStorage(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun saveSecretAudio(
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    ) {
        val round = roundDao.getRound(roundId)
            ?: throw IllegalStateException("Round $roundId does not exist")
        if (round.status.toRoundStatus() == RoundStatus.COMPLETED) {
            throw IllegalStateException("Cannot overwrite the secret of a completed round")
        }
        // Replace a previous secret attempt: remove the old files after the row update
        // so a crash mid-operation never leaves the database pointing at deleted files.
        val oldOriginal = round.secretOriginalPath
        val oldReversed = round.secretReversedPath
        roundDao.setSecretAudio(
            roundId = roundId,
            originalPath = originalPath,
            reversedPath = reversedPath,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
        )
        if (oldOriginal != null && oldOriginal != originalPath) fileStore.deleteQuietly(File(oldOriginal))
        if (oldReversed != null && oldReversed != reversedPath) fileStore.deleteQuietly(File(oldReversed))
    }

    override suspend fun addAttempt(
        attemptId: String,
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    ): GuessAttempt {
        val round = roundDao.getRound(roundId)
            ?: throw IllegalStateException("Round $roundId does not exist")
        if (round.status.toRoundStatus() == RoundStatus.COMPLETED) {
            fileStore.deleteQuietly(File(originalPath))
            fileStore.deleteQuietly(File(reversedPath))
            throw IllegalStateException("Cannot add attempts to a completed round")
        }
        val entity = GuessAttemptEntity(
            id = attemptId,
            roundId = roundId,
            title = "",
            createdAt = System.currentTimeMillis(),
            originalAudioPath = originalPath,
            reversedAudioPath = reversedPath,
            durationMs = durationMs,
        )
        try {
            roundDao.getRound(roundId) ?: error("Round vanished")
            attemptDao.insert(entity)
        } catch (e: Exception) {
            fileStore.deleteQuietly(File(originalPath))
            fileStore.deleteQuietly(File(reversedPath))
            throw e
        }
        return entity.toDomain()
    }

    override suspend fun renameAttempt(attemptId: String, title: String) {
        attemptDao.rename(attemptId, title)
    }

    override suspend fun deleteAttempt(attemptId: String) {
        val attempt = attemptDao.getAttempt(attemptId) ?: return
        attemptDao.markPendingDeletion(attemptId)
        fileStore.deleteQuietly(File(attempt.originalAudioPath))
        fileStore.deleteQuietly(File(attempt.reversedAudioPath))
        attemptDao.deleteById(attemptId)
    }

    override suspend fun getAttemptAssetPath(attemptId: String, reversed: Boolean): String? {
        val attempt = attemptDao.getAttempt(attemptId) ?: return null
        return if (reversed) attempt.reversedAudioPath else attempt.originalAudioPath
    }

    override suspend fun cleanupInterruptedDeletions() {
        attemptDao.getAttemptsPendingDeletion().forEach { attempt ->
            fileStore.deleteQuietly(File(attempt.originalAudioPath))
            fileStore.deleteQuietly(File(attempt.reversedAudioPath))
            attemptDao.deleteById(attempt.id)
        }
        roundDao.getRoundsPendingDeletion().forEach { round ->
            fileStore.deleteRoundDir(round.id)
            attemptDao.getAttemptsPendingDeletion()
                .filter { it.roundId == round.id }
                .forEach { attemptDao.deleteById(it.id) }
            roundDao.deleteById(round.id)
        }
        // Remove round directories that no longer have a database row, e.g. when a
        // previous deletion finished removing files but died before deleting the row.
        fileStore.cleanupOrphanRoundDirs(roundDao.getAllRoundIds().toSet())
        fileStore.cleanupTempDir()
    }
}
