package com.vorontsov.audioreverser.data.local.mapper

import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity
import com.vorontsov.audioreverser.domain.model.Attempt
import com.vorontsov.audioreverser.domain.model.AudioAsset
import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.model.GuessAttempt
import com.vorontsov.audioreverser.domain.model.RoundStatus
import java.io.File

private const val STATUS_IN_PROGRESS = "IN_PROGRESS"
private const val STATUS_COMPLETED = "COMPLETED"

fun RoundStatus.toStorage(): String = when (this) {
    RoundStatus.IN_PROGRESS -> STATUS_IN_PROGRESS
    RoundStatus.COMPLETED -> STATUS_COMPLETED
}

fun String.toRoundStatus(): RoundStatus = when (this) {
    STATUS_COMPLETED -> RoundStatus.COMPLETED
    else -> RoundStatus.IN_PROGRESS
}

fun GameRoundEntity.toDomain(): GameRound = GameRound(
    id = id,
    title = title,
    status = status.toRoundStatus(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    secretDurationMs = secretDurationMs,
    hasSecretRecording = secretOriginalPath != null && secretReversedPath != null,
)

fun GuessAttemptEntity.toDomain(): GuessAttempt = GuessAttempt(
    id = id,
    roundId = roundId,
    title = title,
    createdAt = createdAt,
    durationMs = durationMs,
    originalPath = originalAudioPath,
    reversedPath = reversedAudioPath,
    originalExists = File(originalAudioPath).exists(),
    reversedExists = File(reversedAudioPath).exists(),
)

fun List<GuessAttemptEntity>.toAttemptsWithNumbers(): List<Attempt> =
    mapIndexed { index, entity -> Attempt(entity.toDomain(), index + 1) }


fun GameRoundEntity.secretOriginalAsset(): AudioAsset? {
    val path = secretOriginalPath ?: return null
    if (status.toRoundStatus() != RoundStatus.COMPLETED) return null
    return AudioAsset(
        path = path,
        durationMs = secretDurationMs ?: 0L,
        exists = File(path).exists(),
    )
}

fun GameRoundEntity.secretReversedAsset(): AudioAsset? {
    val path = secretReversedPath ?: return null
    return AudioAsset(
        path = path,
        durationMs = secretDurationMs ?: 0L,
        exists = File(path).exists(),
    )
}
