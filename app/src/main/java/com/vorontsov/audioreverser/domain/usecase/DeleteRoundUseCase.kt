package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class DeleteRoundUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String) {
        repository.deleteRound(roundId)
    }
}
