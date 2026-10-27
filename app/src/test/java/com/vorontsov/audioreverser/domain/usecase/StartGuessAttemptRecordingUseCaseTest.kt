package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.testutil.FakeAudioPlayer
import com.vorontsov.audioreverser.testutil.FakeAudioRecorder
import com.vorontsov.audioreverser.testutil.FakeRoundRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StartGuessAttemptRecordingUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: FakeRoundRepository
    private lateinit var recorder: FakeAudioRecorder
    private lateinit var useCase: StartGuessAttemptRecordingUseCase

    @Before
    fun setUp() {
        repository = FakeRoundRepository()
        recorder = FakeAudioRecorder()
        val sessionManager = AudioSessionManager(recorder, FakeAudioPlayer())
        useCase = StartGuessAttemptRecordingUseCase(
            sessionManager = sessionManager,
            fileStore = RecordingFileStore(tempFolder.root),
            repository = repository,
        )
    }

    @Test
    fun `rejects a missing round`() = runTest {
        val failure = runCatching { useCase("missing") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, recorder.startCalls)
    }

    @Test
    fun `attempts are rejected when the round is completed`() = runTest {
        val round = repository.createRound()
        repository.completeRound(round.id)

        val failure = runCatching { useCase(round.id) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(0, recorder.startCalls)
    }

    @Test
    fun `starts a recording for an active round`() = runTest {
        val round = repository.createRound()

        useCase(round.id)

        assertEquals(1, recorder.startCalls)
    }
}
