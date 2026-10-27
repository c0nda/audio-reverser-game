package com.vorontsov.audioreverser.core.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val startedAtMs: Long) : RecorderState
}

data class RecordingResult(
    val pcmFile: File,
    val durationMs: Long,
)

interface AudioRecorder {
    val state: StateFlow<RecorderState>

    suspend fun start(pcmFile: File)

    suspend fun stop(): RecordingResult?

    suspend fun cancel()
}

class AudioCaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
