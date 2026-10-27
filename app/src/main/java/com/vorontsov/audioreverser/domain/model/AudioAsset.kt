package com.vorontsov.audioreverser.domain.model

data class AudioAsset(
    val path: String,
    val durationMs: Long,
    val exists: Boolean,
)
