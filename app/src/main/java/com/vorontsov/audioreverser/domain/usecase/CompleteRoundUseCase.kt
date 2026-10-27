package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.model.GameRound
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class CompleteRoundException(message: String) : Exception(message)

class CompleteRoundUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String): GameRound {
        val round = repository.getRound(roundId)
            ?: throw CompleteRoundException("Round does not exist")

        if (round.isCompleted) {
            throw CompleteRoundException("Round is already completed")
        }

        if (!round.hasSecretRecording) {
            throw CompleteRoundException("Secret phrase has not been recorded")
        }

        repository.completeRound(roundId)

        return repository.getRound(roundId)
            ?: throw CompleteRoundException("Round disappeared while completing")
    }
}
