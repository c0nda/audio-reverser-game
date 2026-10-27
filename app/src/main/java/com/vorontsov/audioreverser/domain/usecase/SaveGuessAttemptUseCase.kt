package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioReverser
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.model.GuessAttempt
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import java.util.UUID
import javax.inject.Inject

class SaveGuessAttemptUseCase @Inject constructor(
    private val sessionManager: AudioSessionManager,
    private val reverser: AudioReverser,
    private val fileStore: RecordingFileStore,
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String): GuessAttempt {
        val result = sessionManager.stopRecording()
            ?: throw RecordingTooShortException("No recording in progress")
        if (result.durationMs < MIN_RECORDING_DURATION_MS) {
            fileStore.deleteQuietly(result.pcmFile)
            throw RecordingTooShortException("Recording is too short")
        }
        val attemptId = UUID.randomUUID().toString()
        val processed = reverser.reverse(roundId, "attempt_$attemptId", result.pcmFile)

        return repository.addAttempt(
            attemptId = attemptId,
            roundId = roundId,
            originalPath = processed.originalFile.absolutePath,
            reversedPath = processed.reversedFile.absolutePath,
            durationMs = processed.durationMs,
        )
    }
}
