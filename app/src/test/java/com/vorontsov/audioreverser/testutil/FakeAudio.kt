package com.vorontsov.audioreverser.testutil

import com.vorontsov.audioreverser.core.audio.AudioPlayer
import com.vorontsov.audioreverser.core.audio.AudioRecorder
import com.vorontsov.audioreverser.core.audio.PlaybackState
import com.vorontsov.audioreverser.core.audio.RecorderState
import com.vorontsov.audioreverser.core.audio.RecordingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class FakeAudioRecorder : AudioRecorder {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    var nextResult: RecordingResult? = null
    var startCalls = 0
    var stopCalls = 0
    var cancelCalls = 0

    override suspend fun start(pcmFile: File) {
        startCalls++
        _state.value = RecorderState.Recording(startedAtMs = 0L)
    }

    override suspend fun stop(): RecordingResult? {
        stopCalls++
        _state.value = RecorderState.Idle
        return nextResult
    }

    override suspend fun cancel() {
        cancelCalls++
        _state.value = RecorderState.Idle
    }
}

class FakeAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    var stopCalls = 0
    var pauseCalls = 0
    var resumeCalls = 0
    var lastPlayedKey: String? = null

    override suspend fun play(assetKey: String, file: File) {
        lastPlayedKey = assetKey
        _state.value = PlaybackState.Playing(assetKey, 0L, 1000L)
    }

    override fun pause() {
        pauseCalls++
    }

    override fun resume() {
        resumeCalls++
    }

    override fun stop() {
        stopCalls++
        _state.value = PlaybackState.Idle
    }

    override fun seekTo(positionMs: Long) = Unit
}
