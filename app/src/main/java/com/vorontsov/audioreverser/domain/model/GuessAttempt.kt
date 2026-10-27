package com.vorontsov.audioreverser.domain.model

data class GuessAttempt(
    val id: String,
    val roundId: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val originalPath: String,
    val reversedPath: String,
    val originalExists: Boolean,
    val reversedExists: Boolean,
)
