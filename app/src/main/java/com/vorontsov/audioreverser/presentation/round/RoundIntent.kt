package com.vorontsov.audioreverser.presentation.round

/** Every user action on the round screen funnels through this single entry point. */
sealed interface RoundIntent {
    data object StartSecretRecording : RoundIntent
    data object StopSecretRecording : RoundIntent
    data object CancelSecretRecording : RoundIntent
    data object StartGuessRecording : RoundIntent
    data object StopGuessRecording : RoundIntent
    data object StopPlayback : RoundIntent

    data class TogglePlayback(
        val assetKey: String,
        val path: String,
    ) : RoundIntent

    data class SeekPlayback(val positionMs: Long) : RoundIntent

    data object RequestFinishRound : RoundIntent
    data object ConfirmFinishRound : RoundIntent
    data object DismissFinishConfirm : RoundIntent

    data object RequestRenameRound : RoundIntent
    data object DismissRenameRound : RoundIntent
    data class ConfirmRenameRound(val title: String) : RoundIntent

    data class RequestRenameAttempt(val attemptId: String) : RoundIntent
    data object DismissRenameAttempt : RoundIntent
    data class ConfirmRenameAttempt(val attemptId: String, val title: String) : RoundIntent

    data class RequestDeleteAttempt(val attemptId: String) : RoundIntent
    data object DismissDeleteAttempt : RoundIntent
    data class ConfirmDeleteAttempt(val attemptId: String) : RoundIntent

    data class ShareAsset(
        val path: String,
        val displayName: String,
    ) : RoundIntent

    data object DismissError : RoundIntent
    data object NavigateBack : RoundIntent
}
