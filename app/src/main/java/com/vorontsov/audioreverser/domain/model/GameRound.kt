package com.vorontsov.audioreverser.domain.model

data class GameRound(
    val id: String,
    val title: String,
    val status: RoundStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val secretDurationMs: Long?,
    val hasSecretRecording: Boolean,
) {
    val isCompleted: Boolean get() = status == RoundStatus.COMPLETED
}
