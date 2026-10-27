package com.vorontsov.audioreverser.testutil

import com.vorontsov.audioreverser.domain.model.Attempt
import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.model.GuessAttempt
import com.vorontsov.audioreverser.domain.model.RoundDetails
import com.vorontsov.audioreverser.domain.model.RoundStatus
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * In-memory repository double. It mirrors the real integrity rules (completed rounds
 * reject new attempts, secret access follows status) so use case and ViewModel tests
 * exercise meaningful behavior instead of mock interactions.
 */
class FakeRoundRepository : RoundRepository {

    private val rounds = MutableStateFlow<Map<String, GameRound>>(emptyMap())
    private val attempts = MutableStateFlow<Map<String, List<GuessAttempt>>>(emptyMap())
    private var idCounter = 0
    private var clock = 1_000L

    var secretAssets: MutableMap<String, Pair<String, String>> = mutableMapOf()
    val deleteRoundCalls = mutableListOf<String>()

    override fun observeRounds(): Flow<List<RoundSummary>> =
        combine(rounds, attempts) { roundMap, attemptMap ->
            roundMap.values
                .sortedByDescending { it.createdAt }
                .map { RoundSummary(it, attemptMap[it.id]?.size ?: 0) }
        }

    override fun observeRoundDetails(roundId: String): Flow<RoundDetails?> =
        combine(rounds, attempts) { roundMap, attemptMap ->
            roundMap[roundId]?.let { round ->
                val list = attemptMap[roundId].orEmpty()
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                    .mapIndexed { index, attempt -> Attempt(attempt, index + 1) }
                RoundDetails(
                    round = round,
                    secretReversed = null,
                    secretOriginal = if (round.isCompleted) {
                        secretAssets[roundId]?.let {
                            com.vorontsov.audioreverser.domain.model.AudioAsset(
                                it.first,
                                round.secretDurationMs ?: 0,
                                true,
                            )
                        }
                    } else {
                        null
                    },
                    attempts = list,
                )
            }
        }

    override suspend fun createRound(): GameRound {
        val id = "round-${++idCounter}"
        val round = GameRound(
            id = id,
            title = "",
            status = RoundStatus.IN_PROGRESS,
            createdAt = clock++,
            updatedAt = clock,
            secretDurationMs = null,
            hasSecretRecording = false,
        )
        rounds.value = rounds.value + (id to round)
        attempts.value = attempts.value + (id to emptyList())
        return round
    }

    override suspend fun getRound(roundId: String): GameRound? = rounds.value[roundId]

    override suspend fun renameRound(roundId: String, title: String) {
        val round = rounds.value[roundId] ?: return
        rounds.value = rounds.value + (roundId to round.copy(title = title))
    }

    override suspend fun deleteRound(roundId: String) {
        deleteRoundCalls += roundId
        rounds.value = rounds.value - roundId
        attempts.value = attempts.value - roundId
        secretAssets.remove(roundId)
    }

    override suspend fun completeRound(roundId: String) {
        val round = rounds.value[roundId] ?: return
        rounds.value = rounds.value + (roundId to round.copy(status = RoundStatus.COMPLETED))
    }

    override suspend fun saveSecretAudio(
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    ) {
        val round = rounds.value[roundId] ?: error("missing round")
        check(round.status == RoundStatus.IN_PROGRESS) { "cannot overwrite completed secret" }
        rounds.value = rounds.value + (
            roundId to round.copy(
                secretDurationMs = durationMs,
                hasSecretRecording = true,
            )
            )
        secretAssets[roundId] = originalPath to reversedPath
    }

    override suspend fun addAttempt(
        attemptId: String,
        roundId: String,
        originalPath: String,
        reversedPath: String,
        durationMs: Long,
    ): GuessAttempt {
        val round = rounds.value[roundId] ?: error("missing round")
        check(round.status == RoundStatus.IN_PROGRESS) { "completed round rejects attempts" }
        val attempt = GuessAttempt(
            id = attemptId,
            roundId = roundId,
            title = "",
            createdAt = clock++,
            durationMs = durationMs,
            originalPath = originalPath,
            reversedPath = reversedPath,
            originalExists = true,
            reversedExists = true,
        )
        attempts.value = attempts.value + (roundId to (attempts.value[roundId].orEmpty() + attempt))
        return attempt
    }

    override suspend fun renameAttempt(attemptId: String, title: String) {
        attempts.value = attempts.value.mapValues { (_, list) ->
            list.map { if (it.id == attemptId) it.copy(title = title) else it }
        }
    }

    override suspend fun deleteAttempt(attemptId: String) {
        attempts.value = attempts.value.mapValues { (_, list) ->
            list.filterNot { it.id == attemptId }
        }
    }

    override suspend fun getAttemptAssetPath(attemptId: String, reversed: Boolean): String? =
        attempts.value.values.flatten().firstOrNull { it.id == attemptId }
            ?.let { if (reversed) it.reversedPath else it.originalPath }

    override suspend fun cleanupInterruptedDeletions() = Unit
}
