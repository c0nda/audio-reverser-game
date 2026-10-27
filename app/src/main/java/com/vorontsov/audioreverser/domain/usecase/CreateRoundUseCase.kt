package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class CreateRoundUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(): GameRound = repository.createRound()
}
