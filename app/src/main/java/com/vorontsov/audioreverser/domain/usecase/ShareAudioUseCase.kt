package com.vorontsov.audioreverser.domain.usecase

import javax.inject.Inject

data class ShareableAudio(
    val uriString: String,
    val displayName: String,
    val mimeType: String = "audio/wav",
)

interface AudioSharer {
    suspend fun prepareShare(sourcePath: String, displayName: String): ShareableAudio
}

class ShareAudioUseCase @Inject constructor(
    private val audioSharer: AudioSharer,
) {
    suspend operator fun invoke(sourcePath: String, displayName: String): ShareableAudio {
        require(sourcePath.isNotBlank()) { "Source path must not be blank" }
        return audioSharer.prepareShare(sourcePath, displayName)
    }
}
