package com.vorontsov.audioreverser.presentation.round

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorontsov.audioreverser.core.audio.AudioCaptureException
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.audio.PlaybackException
import com.vorontsov.audioreverser.core.audio.PlaybackState
import com.vorontsov.audioreverser.core.audio.RecorderState
import com.vorontsov.audioreverser.core.audio.AudioProcessingException
import com.vorontsov.audioreverser.domain.model.Attempt
import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.model.RoundDetails
import com.vorontsov.audioreverser.domain.usecase.CancelRecordingUseCase
import com.vorontsov.audioreverser.domain.usecase.CompleteRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.DeleteAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.GetRoundDetailsUseCase
import com.vorontsov.audioreverser.domain.usecase.RecordingTooShortException
import com.vorontsov.audioreverser.domain.usecase.RenameAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.RenameRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.SaveGuessAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.SaveSecretPhraseUseCase
import com.vorontsov.audioreverser.domain.usecase.ShareAudioUseCase
import com.vorontsov.audioreverser.domain.usecase.StartGuessAttemptRecordingUseCase
import com.vorontsov.audioreverser.domain.usecase.StartSecretRecordingUseCase
import com.vorontsov.audioreverser.presentation.components.UiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingTarget { NONE, SECRET, ATTEMPT }

data class RoundUiState(
    val isLoading: Boolean = true,
    val round: GameRound? = null,
    val attempts: List<Attempt> = emptyList(),
    val secretReversedPath: String? = null,
    val secretReversedDurationMs: Long = 0L,
    val secretOriginalPath: String? = null,
    val secretOriginalDurationMs: Long = 0L,
    val secretOriginalAvailable: Boolean = false,
    val hasSecretRecording: Boolean = false,
    val secretExists: Boolean = false,
    val recordingTarget: RecordingTarget = RecordingTarget.NONE,
    val recordingDurationMs: Long = 0L,
    val isProcessing: Boolean = false,
    val playingAssetKey: String? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val error: UiError? = null,
    val showFinishConfirm: Boolean = false,
    val renameRoundTarget: Boolean = false,
    val renameAttemptId: String? = null,
    val deleteAttemptId: String? = null,
) {
    val isRecording: Boolean get() = recordingTarget != RecordingTarget.NONE
}

sealed interface RoundEffect {
    data class LaunchShare(val uriString: String, val mimeType: String, val title: String) : RoundEffect
    data object NavigateBack : RoundEffect
}

@HiltViewModel
class RoundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRoundDetails: GetRoundDetailsUseCase,
    private val startSecretRecordingUseCase: StartSecretRecordingUseCase,
    private val saveSecretPhrase: SaveSecretPhraseUseCase,
    private val startGuessAttemptRecordingUseCase: StartGuessAttemptRecordingUseCase,
    private val saveGuessAttempt: SaveGuessAttemptUseCase,
    private val cancelRecordingUseCase: CancelRecordingUseCase,
    private val completeRound: CompleteRoundUseCase,
    private val renameRound: RenameRoundUseCase,
    private val deleteAttempt: DeleteAttemptUseCase,
    private val renameAttempt: RenameAttemptUseCase,
    private val shareAudio: ShareAudioUseCase,
    private val audioSessionManager: AudioSessionManager,
) : ViewModel() {

    private val roundId: String = checkNotNull(savedStateHandle[ARG_ROUND_ID]) {
        "Round id is required"
    }

    private val _uiState = MutableStateFlow(RoundUiState())
    val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RoundEffect>(Channel.BUFFERED)
    val effects: Flow<RoundEffect> = _effects.receiveAsFlow()

    private var latestDetails: RoundDetails? = null

    init {
        observeRound()
        observeRecorder()
        observePlayback()
    }

    private fun observeRound() {
        viewModelScope.launch {
            getRoundDetails(roundId)
                .onEach { details ->
                    if (details == null) {
                        _uiState.update {
                            it.copy(isLoading = false, error = UiError.ROUND_MISSING)
                        }
                        return@onEach
                    }
                    latestDetails = details
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            round = details.round,
                            attempts = details.attempts,
                            hasSecretRecording = details.round.hasSecretRecording,
                            secretReversedPath = details.secretReversed?.path,
                            secretReversedDurationMs = details.secretReversed?.durationMs ?: 0L,
                            secretExists = details.secretReversed?.exists ?: false,
                            secretOriginalPath = details.secretOriginal?.path,
                            secretOriginalDurationMs = details.secretOriginal?.durationMs ?: 0L,
                            secretOriginalAvailable = details.secretOriginal != null,
                        )
                    }
                }
                .catch {
                    _uiState.update { it.copy(isLoading = false, error = UiError.GENERIC) }
                }
                .collect {}
        }
    }

    private fun observeRecorder() {
        viewModelScope.launch {
            audioSessionManager.recorderState.collect { recorderState ->
                when (recorderState) {
                    is RecorderState.Recording -> {
                        _uiState.update { it.copy(recordingDurationMs = 0L) }
                        trackRecordingDuration(recorderState.startedAtMs)
                    }

                    RecorderState.Idle -> Unit
                }
            }
        }
    }

    private fun trackRecordingDuration(startedAtMs: Long) {
        viewModelScope.launch {
            while (audioSessionManager.recorderState.value is RecorderState.Recording) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAtMs
                _uiState.update { it.copy(recordingDurationMs = elapsed) }
                delay(DURATION_TICK_MS)
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            audioSessionManager.playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> _uiState.update {
                        it.copy(
                            playingAssetKey = state.assetKey,
                            isPlaying = true,
                            playbackPositionMs = state.positionMs,
                            playbackDurationMs = state.durationMs,
                        )
                    }

                    is PlaybackState.Paused -> _uiState.update {
                        it.copy(
                            playingAssetKey = state.assetKey,
                            isPlaying = false,
                            playbackPositionMs = state.positionMs,
                            playbackDurationMs = state.durationMs,
                        )
                    }

                    PlaybackState.Idle -> _uiState.update {
                        it.copy(playingAssetKey = null, isPlaying = false, playbackPositionMs = 0L)
                    }
                }
            }
        }
    }

    fun onIntent(intent: RoundIntent) {
        when (intent) {
            RoundIntent.StartSecretRecording -> startSecretRecording()
            RoundIntent.StopSecretRecording -> stopSecretRecording()
            RoundIntent.CancelSecretRecording -> cancelRecording()
            RoundIntent.StartGuessRecording -> startGuessRecording()
            RoundIntent.StopGuessRecording -> stopGuessRecording()
            RoundIntent.StopPlayback -> audioSessionManager.stopPlayback()
            is RoundIntent.TogglePlayback -> togglePlayback(intent)
            is RoundIntent.SeekPlayback -> audioSessionManager.seekPlaybackTo(intent.positionMs)
            RoundIntent.RequestFinishRound -> _uiState.update { it.copy(showFinishConfirm = true) }
            RoundIntent.ConfirmFinishRound -> finishRound()
            RoundIntent.DismissFinishConfirm -> _uiState.update { it.copy(showFinishConfirm = false) }
            RoundIntent.RequestRenameRound -> _uiState.update { it.copy(renameRoundTarget = true) }
            RoundIntent.DismissRenameRound -> _uiState.update { it.copy(renameRoundTarget = false) }
            is RoundIntent.ConfirmRenameRound -> confirmRenameRound(intent.title)
            is RoundIntent.RequestRenameAttempt ->
                _uiState.update { it.copy(renameAttemptId = intent.attemptId) }

            RoundIntent.DismissRenameAttempt -> _uiState.update { it.copy(renameAttemptId = null) }
            is RoundIntent.ConfirmRenameAttempt -> confirmRenameAttempt(intent.attemptId, intent.title)
            is RoundIntent.RequestDeleteAttempt ->
                _uiState.update { it.copy(deleteAttemptId = intent.attemptId) }

            RoundIntent.DismissDeleteAttempt -> _uiState.update { it.copy(deleteAttemptId = null) }
            is RoundIntent.ConfirmDeleteAttempt -> confirmDeleteAttempt(intent.attemptId)
            is RoundIntent.ShareAsset -> shareAsset(intent)
            RoundIntent.DismissError -> _uiState.update { it.copy(error = null) }
            RoundIntent.NavigateBack -> viewModelScope.launch {
                if (_uiState.value.isRecording) {
                    cancelRecording()
                }
                audioSessionManager.stopPlayback()
                _effects.send(RoundEffect.NavigateBack)
            }
        }
    }

    private fun startSecretRecording() {
        if (guardBusy()) return
        viewModelScope.launch {
            try {
                audioSessionManager.stopPlayback()
                startSecretRecordingUseCase(roundId)
                _uiState.update { it.copy(recordingTarget = RecordingTarget.SECRET, error = null) }
            } catch (e: Exception) {
                onRecordingStartFailure(e)
            }
        }
    }

    private fun stopSecretRecording() {
        if (_uiState.value.recordingTarget != RecordingTarget.SECRET) return
        _uiState.update { it.copy(recordingTarget = RecordingTarget.NONE, isProcessing = true) }
        viewModelScope.launch {
            try {
                saveSecretPhrase(roundId)
            } catch (e: RecordingTooShortException) {
                _uiState.update { it.copy(error = UiError.RECORDING_TOO_SHORT) }
            } catch (e: AudioProcessingException) {
                _uiState.update { it.copy(error = UiError.PROCESSING_FAILED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.SAVING_FAILED) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun startGuessRecording() {
        if (guardBusy()) return
        if (_uiState.value.round?.isCompleted == true) {
            _uiState.update { it.copy(error = UiError.COMPLETED_ROUND) }
            return
        }
        viewModelScope.launch {
            try {
                audioSessionManager.stopPlayback()
                startGuessAttemptRecordingUseCase(roundId)
                _uiState.update { it.copy(recordingTarget = RecordingTarget.ATTEMPT, error = null) }
            } catch (e: Exception) {
                onRecordingStartFailure(e)
            }
        }
    }

    private fun stopGuessRecording() {
        if (_uiState.value.recordingTarget != RecordingTarget.ATTEMPT) return
        _uiState.update { it.copy(recordingTarget = RecordingTarget.NONE, isProcessing = true) }
        viewModelScope.launch {
            try {
                saveGuessAttempt(roundId)
            } catch (e: RecordingTooShortException) {
                _uiState.update { it.copy(error = UiError.RECORDING_TOO_SHORT) }
            } catch (e: AudioProcessingException) {
                _uiState.update { it.copy(error = UiError.PROCESSING_FAILED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.SAVING_FAILED) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun cancelRecording() {
        viewModelScope.launch {
            try {
                cancelRecordingUseCase()
            } catch (e: Exception) {
                // Recording cancellation is best effort; the recorder resets regardless.
            }
            _uiState.update { it.copy(recordingTarget = RecordingTarget.NONE, isProcessing = false) }
        }
    }

    private fun onRecordingStartFailure(e: Exception) {
        val error = when (e) {
            is AudioCaptureException -> UiError.MICROPHONE_UNAVAILABLE
            else -> UiError.RECORDING_FAILED
        }
        _uiState.update {
            it.copy(recordingTarget = RecordingTarget.NONE, isProcessing = false, error = error)
        }
    }

    private fun togglePlayback(intent: RoundIntent.TogglePlayback) {
        val state = _uiState.value
        val currentKey = state.playingAssetKey
        if (currentKey == intent.assetKey) {
            if (state.isPlaying) {
                audioSessionManager.pausePlayback()
            } else {
                audioSessionManager.resumePlayback()
            }
            return
        }
        viewModelScope.launch {
            try {
                audioSessionManager.play(intent.assetKey, java.io.File(intent.path))
            } catch (e: PlaybackException) {
                _uiState.update { it.copy(error = UiError.PLAYBACK_FAILED) }
            } catch (e: AudioCaptureException) {
                _uiState.update { it.copy(error = UiError.RECORDING_FAILED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.PLAYBACK_FAILED) }
            }
        }
    }

    private fun finishRound() {
        _uiState.update { it.copy(showFinishConfirm = false, isProcessing = true) }
        viewModelScope.launch {
            try {
                completeRound(roundId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.GENERIC) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun confirmRenameRound(title: String) {
        _uiState.update { it.copy(renameRoundTarget = false) }
        viewModelScope.launch {
            try {
                renameRound(roundId, title)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.GENERIC) }
            }
        }
    }

    private fun confirmRenameAttempt(attemptId: String, title: String) {
        _uiState.update { it.copy(renameAttemptId = null) }
        viewModelScope.launch {
            try {
                renameAttempt(attemptId, title)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.GENERIC) }
            }
        }
    }

    private fun confirmDeleteAttempt(attemptId: String) {
        _uiState.update { it.copy(deleteAttemptId = null) }
        viewModelScope.launch {
            try {
                if (_uiState.value.isPlaying &&
                    _uiState.value.playingAssetKey?.contains(attemptId) == true
                ) {
                    audioSessionManager.stopPlayback()
                }
                deleteAttempt(attemptId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.DELETE_FAILED) }
            }
        }
    }

    private fun shareAsset(intent: RoundIntent.ShareAsset) {
        viewModelScope.launch {
            try {
                val shareable = shareAudio(intent.path, intent.displayName)
                _effects.send(
                    RoundEffect.LaunchShare(
                        uriString = shareable.uriString,
                        mimeType = shareable.mimeType,
                        title = shareable.displayName,
                    ),
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.SHARE_FAILED) }
            }
        }
    }

    private fun guardBusy(): Boolean {
        val state = _uiState.value
        return state.isRecording || state.isProcessing
    }

    override fun onCleared() {
        audioSessionManager.shutdown()
    }

    companion object {
        const val ARG_ROUND_ID = "roundId"
        private val DURATION_TICK_MS = 100L
    }
}
