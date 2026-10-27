package com.vorontsov.audioreverser.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.domain.usecase.CreateRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.ObserveRoundsUseCase
import com.vorontsov.audioreverser.presentation.components.UiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val rounds: List<RoundSummary> = emptyList(),
    val error: UiError? = null,
) {
    val hasUnfinishedRound: Boolean get() = rounds.any { !it.round.isCompleted }
    val unfinishedRoundId: String? get() =
        rounds.firstOrNull { !it.round.isCompleted }?.round?.id
}

sealed interface HomeIntent {
    data object CreateRound : HomeIntent
    data object DismissError : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToRound(val roundId: String) : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeRounds: ObserveRoundsUseCase,
    private val createRound: CreateRoundUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var isCreating = false

    init {
        viewModelScope.launch {
            observeRounds()
                .map { it.take(RECENT_ROUND_LIMIT) }
                .onEach { rounds ->
                    _uiState.update { it.copy(isLoading = false, rounds = rounds) }
                }
                .catch {
                    _uiState.update { it.copy(isLoading = false, error = UiError.GENERIC) }
                }
                .collect {}
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.CreateRound -> createNewRound()
            HomeIntent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun createNewRound() {
        if (isCreating) return
        isCreating = true
        viewModelScope.launch {
            try {
                val round = createRound()
                _effects.send(HomeEffect.NavigateToRound(round.id))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiError.GENERIC) }
            } finally {
                isCreating = false
            }
        }
    }

    companion object {
        private const val RECENT_ROUND_LIMIT = 5
    }
}
