package com.vorontsov.audioreverser.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.vorontsov.audioreverser.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@Singleton
class AndroidAudioRecorder @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()

    private var captureJob: Job? = null
    private var currentFile: File? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var captureFailure: Exception? = null

    @SuppressLint("MissingPermission")
    override suspend fun start(pcmFile: File) {
        synchronized(lock) {
            if (captureJob?.isActive == true) {
                throw AudioCaptureException("A recording session is already active")
            }

            val minBuffer = AudioRecord.getMinBufferSize(
                WavCodec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
                throw AudioCaptureException("PCM recording is not supported on this device")
            }

            val bufferSize = maxOf(minBuffer, WavCodec.BUFFER_SIZE_BYTES * 4)
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    WavCodec.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (e: IllegalArgumentException) {
                throw AudioCaptureException("Unable to initialize the microphone", e)
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw AudioCaptureException(
                    "Microphone is unavailable or in use by another application",
                )
            }

            cancelRequested = false
            captureFailure = null
            currentFile = pcmFile
            captureJob = scope.launch { capture(record, pcmFile) }
            _state.value = RecorderState.Recording(startedAtMs = SystemClock.elapsedRealtime())
        }
    }

    private suspend fun capture(record: AudioRecord, pcmFile: File) {
        val samples = ShortArray(WavCodec.SAMPLES_PER_CHUNK)
        val bytes = ByteArray(samples.size * 2)
        try {
            record.startRecording()
            withContext(Dispatchers.IO) {
                BufferedOutputStream(
                    FileOutputStream(pcmFile),
                    WavCodec.BUFFER_SIZE_BYTES * 4
                ).use { out ->
                    while (coroutineContext.isActive && !cancelRequested) {
                        val read = record.read(samples, 0, samples.size)
                        if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                            read == AudioRecord.ERROR_BAD_VALUE
                        ) {
                            throw AudioCaptureException("Recording failed while reading audio data")
                        }

                        if (read <= 0) continue

                        for (i in 0 until read) {
                            val sample = samples[i].toInt()
                            bytes[i * 2] = (sample and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }
                        out.write(bytes, 0, read * 2)
                    }
                }
            }
        } catch (e: Exception) {
            captureFailure = e
            pcmFile.delete()
        } finally {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // Already stopped; nothing to release beyond this point.
            }
            record.release()
        }
    }

    override suspend fun stop(): RecordingResult? {
        val job = synchronized(lock) {
            val job = captureJob ?: return null
            captureJob = null
            job
        }
        val file = currentFile ?: return null
        currentFile = null
        job.cancel()
        withContext(ioDispatcher) { job.join() }
        _state.value = RecorderState.Idle

        captureFailure?.let { failure ->
            captureFailure = null
            file.delete()
            throw failure as? AudioCaptureException ?: AudioCaptureException("Recording failed", failure)
        }

        if (cancelRequested) {
            file.delete()
            return null
        }

        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return null
        }

        return RecordingResult(
            pcmFile = file,
            durationMs = file.length() * 1000 / WavCodec.BYTE_RATE,
        )
    }

    override suspend fun cancel() {
        val job = synchronized(lock) {
            val job = captureJob ?: return
            captureJob = null
            job
        }
        cancelRequested = true
        job.cancel()
        withContext(ioDispatcher) { job.join() }
        currentFile?.let { file ->
            try {
                file.delete()
            } catch (e: SecurityException) {
                // Best effort; the temp sweeper removes leftovers on next launch.
            }
        }
        currentFile = null
        captureFailure = null
        _state.value = RecorderState.Idle
    }
}
