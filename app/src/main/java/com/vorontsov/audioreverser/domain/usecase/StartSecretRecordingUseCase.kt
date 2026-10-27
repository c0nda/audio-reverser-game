package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class StartSecretRecordingUseCase @Inject constructor(
    private val sessionManager: AudioSessionManager,
    private val fileStore: RecordingFileStore,
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String) {
        repository.getRound(roundId)
            ?: throw IllegalArgumentException("Round $roundId does not exist")
        sessionManager.startRecording(fileStore.newTempPcmFile())
    }
}
