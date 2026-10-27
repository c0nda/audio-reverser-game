package com.vorontsov.audioreverser.domain.usecase

import com.vorontsov.audioreverser.core.audio.AudioReverser
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.core.audio.RecordingResult
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.testutil.FakeAudioPlayer
import com.vorontsov.audioreverser.testutil.FakeAudioRecorder
import com.vorontsov.audioreverser.testutil.FakeRoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveGuessAttemptUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: FakeRoundRepository
    private lateinit var recorder: FakeAudioRecorder
    private lateinit var sessionManager: AudioSessionManager
    private lateinit var fileStore: RecordingFileStore
    private lateinit var startRecording: StartGuessAttemptRecordingUseCase
    private lateinit var saveAttempt: SaveGuessAttemptUseCase

    @Before
    fun setUp() {
        repository = FakeRoundRepository()
        recorder = FakeAudioRecorder()
        sessionManager = AudioSessionManager(recorder, FakeAudioPlayer())
        fileStore = RecordingFileStore(tempFolder.root)
        startRecording = StartGuessAttemptRecordingUseCase(sessionManager, fileStore, repository)
        saveAttempt = SaveGuessAttemptUseCase(
            sessionManager = sessionManager,
            reverser = AudioReverser(fileStore, Dispatchers.Unconfined),
            fileStore = fileStore,
            repository = repository,
        )
    }

    @Test
    fun `saving adds an ordered attempt to the round`() = runTest {
        val round = repository.createRound()
        recorder.nextResult = RecordingResult(createPcmFile(), durationMs = 1000L)

        startRecording(round.id)
        val saved = saveAttempt(round.id)

        assertEquals(round.id, saved.roundId)
        val details = requireNotNull(repository.observeRoundDetails(round.id).first())
        assertEquals(1, details.attempts.size)
        assertEquals(1, details.attempts.first().number)
    }

    @Test
    fun `too short recordings are rejected`() = runTest {
        val round = repository.createRound()
        recorder.nextResult = RecordingResult(createPcmFile(), durationMs = 100L)

        startRecording(round.id)
        val failure = runCatching { saveAttempt(round.id) }.exceptionOrNull()

        assertTrue(failure is RecordingTooShortException)
        val details = requireNotNull(repository.observeRoundDetails(round.id).first())
        assertTrue(details.attempts.isEmpty())
    }

    @Test
    fun `saving without an active recording is rejected`() = runTest {
        val round = repository.createRound()

        val failure = runCatching { saveAttempt(round.id) }.exceptionOrNull()

        assertTrue(failure is RecordingTooShortException)
    }

    private fun createPcmFile(): File {
        val file = fileStore.newTempPcmFile()
        // 22050 samples of mono 16-bit PCM = 1000 ms.
        file.writeBytes(ByteArray(22050 * 2))
        return file
    }
}
