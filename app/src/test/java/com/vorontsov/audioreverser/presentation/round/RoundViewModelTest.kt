package com.vorontsov.audioreverser.presentation.round

import androidx.lifecycle.SavedStateHandle
import com.vorontsov.audioreverser.core.audio.AudioReverser
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.audio.RecordingResult
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.domain.usecase.CancelRecordingUseCase
import com.vorontsov.audioreverser.domain.usecase.CompleteRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.DeleteAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.GetRoundDetailsUseCase
import com.vorontsov.audioreverser.domain.usecase.RenameAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.RenameRoundUseCase
import com.vorontsov.audioreverser.domain.usecase.SaveGuessAttemptUseCase
import com.vorontsov.audioreverser.domain.usecase.SaveSecretPhraseUseCase
import com.vorontsov.audioreverser.domain.usecase.ShareAudioUseCase
import com.vorontsov.audioreverser.domain.usecase.StartGuessAttemptRecordingUseCase
import com.vorontsov.audioreverser.domain.usecase.StartSecretRecordingUseCase
import com.vorontsov.audioreverser.domain.usecase.AudioSharer
import com.vorontsov.audioreverser.domain.usecase.ShareableAudio
import com.vorontsov.audioreverser.testutil.FakeAudioPlayer
import com.vorontsov.audioreverser.testutil.FakeAudioRecorder
import com.vorontsov.audioreverser.testutil.FakeRoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RoundViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads the round`() = runTest(dispatcher) {
        val env = environment()
        val round = env.repository.createRound()
        val viewModel = env.viewModel(round.id)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(round.id, viewModel.uiState.value.round?.id)
        assertTrue(viewModel.uiState.value.attempts.isEmpty())
    }

    @Test
    fun `original secret stays locked during an active round`() = runTest(dispatcher) {
        val env = environment()
        val round = env.repository.createRound()
        env.repository.saveSecretAudio(round.id, "orig.wav", "rev.wav", 1500L)
        val viewModel = env.viewModel(round.id)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasSecretRecording)
        assertFalse(viewModel.uiState.value.secretOriginalAvailable)
        assertNull(viewModel.uiState.value.secretOriginalPath)
    }

    @Test
    fun `recording an attempt adds it to the state`() = runTest(dispatcher) {
        val env = environment()
        val round = env.repository.createRound()
        env.recorder.nextResult = RecordingResult(env.pcmFile(), 1000L)
        val viewModel = env.viewModel(round.id)
        advanceUntilIdle()

        viewModel.onIntent(RoundIntent.StartGuessRecording)
        advanceTimeBy(50L)
        viewModel.onIntent(RoundIntent.StopGuessRecording)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.attempts.size)
        assertEquals(1, viewModel.uiState.value.attempts.first().number)
    }

    @Test
    fun `finishing a round unlocks the original`() = runTest(dispatcher) {
        val env = environment()
        val round = env.repository.createRound()
        env.repository.saveSecretAudio(round.id, "orig.wav", "rev.wav", 1500L)
        val viewModel = env.viewModel(round.id)
        advanceUntilIdle()

        viewModel.onIntent(RoundIntent.RequestFinishRound)
        viewModel.onIntent(RoundIntent.ConfirmFinishRound)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.round?.isCompleted == true)
        assertTrue(viewModel.uiState.value.secretOriginalAvailable)
        assertEquals("orig.wav", viewModel.uiState.value.secretOriginalPath)
    }

    @Test
    fun `adding attempts to a completed round is rejected`() = runTest(dispatcher) {
        val env = environment()
        val round = env.repository.createRound()
        env.repository.saveSecretAudio(round.id, "orig.wav", "rev.wav", 1500L)
        env.repository.completeRound(round.id)
        val viewModel = env.viewModel(round.id)
        advanceUntilIdle()

        viewModel.onIntent(RoundIntent.StartGuessRecording)
        advanceTimeBy(50L)

        assertEquals(0, env.recorder.startCalls)
    }

    private fun environment(): Environment {
        val repository = FakeRoundRepository()
        val recorder = FakeAudioRecorder()
        val sessionManager = AudioSessionManager(recorder, FakeAudioPlayer())
        val fileStore = RecordingFileStore(tempFolder.root)
        val reverser = AudioReverser(fileStore, Dispatchers.Unconfined)
        val sharer = object : AudioSharer {
            override suspend fun prepareShare(sourcePath: String, displayName: String) =
                ShareableAudio("content://test/$sourcePath", displayName)
        }
        return Environment(repository, recorder, fileStore, sessionManager, reverser, sharer)
    }

    private inner class Environment(
        val repository: FakeRoundRepository,
        val recorder: FakeAudioRecorder,
        val fileStore: RecordingFileStore,
        private val sessionManager: AudioSessionManager,
        private val reverser: AudioReverser,
        private val sharer: AudioSharer,
    ) {
        fun viewModel(roundId: String): RoundViewModel {
            val handle = SavedStateHandle(mapOf(ARG_ROUND_ID to roundId))
            return RoundViewModel(
                savedStateHandle = handle,
                getRoundDetails = GetRoundDetailsUseCase(repository),
                startSecretRecordingUseCase = StartSecretRecordingUseCase(sessionManager, fileStore, repository),
                saveSecretPhrase = SaveSecretPhraseUseCase(sessionManager, reverser, fileStore, repository),
                startGuessAttemptRecordingUseCase = StartGuessAttemptRecordingUseCase(sessionManager, fileStore, repository),
                saveGuessAttempt = SaveGuessAttemptUseCase(sessionManager, reverser, fileStore, repository),
                cancelRecordingUseCase = CancelRecordingUseCase(sessionManager),
                completeRound = CompleteRoundUseCase(repository),
                renameRound = RenameRoundUseCase(repository),
                deleteAttempt = DeleteAttemptUseCase(repository),
                renameAttempt = RenameAttemptUseCase(repository),
                shareAudio = ShareAudioUseCase(sharer),
                audioSessionManager = sessionManager,
            )
        }

        fun pcmFile(): File {
            val file = fileStore.newTempPcmFile()
            file.writeBytes(ByteArray(22050 * 2))
            return file
        }
    }

    companion object {
        private const val ARG_ROUND_ID = "roundId"
    }
}
