package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class RenameRoundUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(roundId: String, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Round title must not be blank" }
        repository.renameRound(roundId, trimmed)
    }
}
