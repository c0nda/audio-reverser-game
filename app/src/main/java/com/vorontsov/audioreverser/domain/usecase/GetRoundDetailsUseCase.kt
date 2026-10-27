package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.model.RoundDetails
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRoundDetailsUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    operator fun invoke(roundId: String): Flow<RoundDetails?> =
        repository.observeRoundDetails(roundId)
}
