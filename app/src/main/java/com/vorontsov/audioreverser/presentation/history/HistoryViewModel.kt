package com.vorontsov.audioreverser.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.domain.usecase.DeleteRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.ObserveRoundsUseCase
import com.vorontsov.audioreverser.domain.usecase.RenameRoundUseCase
import com.vorontsov.audioreverser.presentation.components.UiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val rounds: List<RoundSummary> = emptyList(),
    val error: UiError? = null,
    val deleteRoundId: String? = null,
    val renameRoundId: String? = null,
)

sealed interface HistoryIntent {
    data class RequestDelete(val roundId: String) : HistoryIntent
    data object DismissDelete : HistoryIntent
    data class ConfirmDelete(val roundId: String) : HistoryIntent
    data class RequestRename(val roundId: String) : HistoryIntent
    data object DismissRename : HistoryIntent
    data class ConfirmRename(val roundId: String, val title: String) : HistoryIntent
    data object DismissError : HistoryIntent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeRounds: ObserveRoundsUseCase,
    private val deleteRound: DeleteRoundUseCase,
    private val renameRound: RenameRoundUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeRounds()
                .onEach { rounds ->
                    _uiState.update { it.copy(isLoading = false, rounds = rounds) }
                }
                .catch {
                    _uiState.update { it.copy(isLoading = false, error = UiError.GENERIC) }
                }
                .collect {}
        }
    }

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.RequestDelete ->
                _uiState.update { it.copy(deleteRoundId = intent.roundId) }

            HistoryIntent.DismissDelete -> _uiState.update { it.copy(deleteRoundId = null) }
            is HistoryIntent.ConfirmDelete -> confirmDelete(intent.roundId)
            is HistoryIntent.RequestRename ->
                _uiState.update { it.copy(renameRoundId = intent.roundId) }

            HistoryIntent.DismissRename -> _uiState.update { it.copy(renameRoundId = null) }
            is HistoryIntent.ConfirmRename -> confirmRename(intent.roundId, intent.title)
            HistoryIntent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun confirmDelete(roundId: String) {
        _uiState.update { it.copy(deleteRoundId = null) }
        viewModelScope.launch {
            try {
                deleteRound(roundId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.DELETE_FAILED) }
            }
        }
    }

    private fun confirmRename(roundId: String, title: String) {
        _uiState.update { it.copy(renameRoundId = null) }
        viewModelScope.launch {
            try {
                renameRound(roundId, title)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.GENERIC) }
            }
        }
    }
}
