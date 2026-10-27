package com.vorontsov.audioreverser.core.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSessionManager @Inject constructor(
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
) {

    val recorderState: StateFlow<RecorderState> get() = recorder.state
    val playbackState: StateFlow<PlaybackState> get() = player.state

    suspend fun startRecording(pcmFile: File) {
        player.stop()
        recorder.start(pcmFile)
    }

    suspend fun stopRecording(): RecordingResult? = recorder.stop()

    suspend fun cancelRecording() = recorder.cancel()

    suspend fun play(assetKey: String, file: File) {
        if (recorder.state.value is RecorderState.Recording) {
            throw AudioCaptureException("Stop recording before starting playback")
        }
        player.play(assetKey, file)
    }

    fun pausePlayback() = player.pause()

    fun resumePlayback() = player.resume()

    fun stopPlayback() = player.stop()

    fun seekPlaybackTo(positionMs: Long) = player.seekTo(positionMs)

    fun shutdown() {
        player.stop()
    }
}
