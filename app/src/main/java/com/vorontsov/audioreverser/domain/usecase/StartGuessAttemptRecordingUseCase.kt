package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class StartGuessAttemptRecordingUseCase @Inject constructor(
    private val sessionManager: AudioSessionManager,
    private val fileStore: RecordingFileStore,
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String) {
        val round = repository.getRound(roundId)
            ?: throw IllegalArgumentException("Round $roundId does not exist")
        if (round.isCompleted) {
            throw IllegalStateException("Cannot add attempts to a completed round")
        }
        sessionManager.startRecording(fileStore.newTempPcmFile())
    }
}
