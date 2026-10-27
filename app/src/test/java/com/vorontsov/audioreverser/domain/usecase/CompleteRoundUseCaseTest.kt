package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.domain.model.RoundStatus
import com.vorontsov.audioreverser.testutil.FakeRoundRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteRoundUseCaseTest {

    private val repository = FakeRoundRepository()
    private val useCase = CompleteRoundUseCase(repository)

    @Test
    fun `completing a round without a secret phrase is rejected`() = runTest {
        val round = repository.createRound()
        val failure = runCatching { useCase(round.id) }.exceptionOrNull()
        assertTrue(failure is CompleteRoundException)
    }

    @Test
    fun `completing a round flips its status and unlocks the original`() = runTest {
        val round = repository.createRound()
        repository.saveSecretAudio(round.id, "orig.wav", "rev.wav", 1500L)

        // During an active round the original is locked.
        assertNull(firstDetails(round.id).secretOriginal)

        val completed = useCase(round.id)

        assertEquals(RoundStatus.COMPLETED, completed.status)
        assertEquals("orig.wav", firstDetails(round.id).secretOriginal?.path)
    }

    @Test
    fun `completing a round twice is rejected`() = runTest {
        val round = repository.createRound()
        repository.saveSecretAudio(round.id, "orig.wav", "rev.wav", 1500L)
        useCase(round.id)
        val failure = runCatching { useCase(round.id) }.exceptionOrNull()
        assertTrue(failure is CompleteRoundException)
    }

    private suspend fun firstDetails(roundId: String) =
        requireNotNull(repository.observeRoundDetails(roundId).first())
}
