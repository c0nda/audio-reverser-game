package com.vorontsov.audioreverser.core.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface PlaybackState {
    data object Idle : PlaybackState

    data class Playing(
        val assetKey: String,
        val positionMs: Long,
        val durationMs: Long,
    ) : PlaybackState

    data class Paused(
        val assetKey: String,
        val positionMs: Long,
        val durationMs: Long,
    ) : PlaybackState
}

interface AudioPlayer {
    val state: StateFlow<PlaybackState>

    suspend fun play(assetKey: String, file: File)

    fun pause()

    fun resume()

    fun stop()

    fun seekTo(positionMs: Long)
}

class PlaybackException(message: String, cause: Throwable? = null) : Exception(message, cause)
