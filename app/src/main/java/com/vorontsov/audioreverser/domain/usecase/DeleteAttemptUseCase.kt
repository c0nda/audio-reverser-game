package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.repository.RoundRepository
import javax.inject.Inject

class DeleteAttemptUseCase @Inject constructor(
    private val repository: RoundRepository,
) {
    suspend operator fun invoke(attemptId: String) {
        repository.deleteAttempt(attemptId)
    }
}
