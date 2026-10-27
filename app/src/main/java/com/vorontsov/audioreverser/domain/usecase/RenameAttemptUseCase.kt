package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class RenameAttemptUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(attemptId: String, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Attempt title must not be blank" }
        repository.renameAttempt(attemptId, trimmed)
    }
}
