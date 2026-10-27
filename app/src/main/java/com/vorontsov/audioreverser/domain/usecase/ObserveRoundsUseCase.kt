package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRoundsUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    operator fun invoke(): Flow<List<RoundSummary>> = repository.observeRounds()
}
