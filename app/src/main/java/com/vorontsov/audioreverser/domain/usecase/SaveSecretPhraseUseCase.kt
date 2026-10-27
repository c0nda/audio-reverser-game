package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioReverser
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class RecordingTooShortException(message: String) : Exception(message)

class SaveSecretPhraseUseCase @Inject constructor(
    private val sessionManager: AudioSessionManager,
    private val reverser: AudioReverser,
    private val fileStore: RecordingFileStore,
    private val repository: RoundRepository,
) {

    suspend operator fun invoke(roundId: String) {
        val result = sessionManager.stopRecording()
            ?: throw RecordingTooShortException("No recording in progress")
        if (result.durationMs < MIN_RECORDING_DURATION_MS) {
            fileStore.deleteQuietly(result.pcmFile)
            throw RecordingTooShortException("Recording is too short")
        }
        val processed = reverser.reverse(roundId, SECRET_ASSET_PREFIX, result.pcmFile)
        repository.saveSecretAudio(
            roundId = roundId,
            originalPath = processed.originalFile.absolutePath,
            reversedPath = processed.reversedFile.absolutePath,
            durationMs = processed.durationMs,
        )
    }

    private companion object {
        const val SECRET_ASSET_PREFIX = "secret"
    }
}
