package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import javax.inject.Inject

class CancelRecordingUseCase @Inject constructor(
    private val sessionManager: AudioSessionManager,
) {
    suspend operator fun invoke() = sessionManager.cancelRecording()
}
