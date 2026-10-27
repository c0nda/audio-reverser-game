package com.vorontsov.audioreverser.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vorontsov.audioreverser.R

enum class UiError {
    RECORDING_FAILED,
    PROCESSING_FAILED,
    SAVING_FAILED,
    SHARE_FAILED,
    PLAYBACK_FAILED,
    ROUND_MISSING,
    COMPLETED_ROUND,
    DELETE_FAILED,
    RECORDING_TOO_SHORT,
    MICROPHONE_UNAVAILABLE,
    GENERIC,
}

@Composable
fun UiError.asMessage(): String = stringResource(
    when (this) {
        UiError.RECORDING_FAILED -> R.string.error_recording_failed
        UiError.PROCESSING_FAILED -> R.string.error_processing_failed
        UiError.SAVING_FAILED -> R.string.error_saving_failed
        UiError.SHARE_FAILED -> R.string.error_share_failed
        UiError.PLAYBACK_FAILED -> R.string.playback_error
        UiError.ROUND_MISSING -> R.string.error_round_missing
        UiError.COMPLETED_ROUND -> R.string.error_completed_round
        UiError.DELETE_FAILED -> R.string.error_delete_failed
        UiError.RECORDING_TOO_SHORT -> R.string.recording_too_short
        UiError.MICROPHONE_UNAVAILABLE -> R.string.recording_unavailable
        UiError.GENERIC -> R.string.error_generic
    },
)
