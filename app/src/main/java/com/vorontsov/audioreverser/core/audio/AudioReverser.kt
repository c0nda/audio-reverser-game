package com.vorontsov.audioreverser.core.audio

import com.vorontsov.audioreverser.core.common.IoDispatcher
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

class AudioProcessingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

@Singleton
class AudioReverser @Inject constructor(
    private val fileStore: RecordingFileStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun reverse(
        roundId: String,
        assetFileNamePrefix: String,
        pcmFile: File,
    ): ProcessedAudio = withContext(ioDispatcher) {
        val originalTmp = fileStore.newTempWavFile()
        val reversedTmp = fileStore.newTempWavFile()
        try {
            val dataLength = pcmFile.length()
            writeWavFromPcm(pcmFile, originalTmp, reverse = false, dataLength = dataLength)
            writeWavFromPcm(pcmFile, reversedTmp, reverse = true, dataLength = dataLength)
            pcmFile.delete()

            val original = fileStore.moveIntoRound(
                originalTmp, roundId, "${assetFileNamePrefix}_original.wav",
            )

            val reversed = fileStore.moveIntoRound(
                reversedTmp, roundId, "${assetFileNamePrefix}_reversed.wav",
            )

            ProcessedAudio(
                originalFile = original,
                reversedFile = reversed,
                durationMs = WavCodec.durationMs(dataLength),
            )
        } catch (e: Exception) {
            fileStore.deleteQuietly(originalTmp)
            fileStore.deleteQuietly(reversedTmp)
            pcmFile.delete()
            throw e as? AudioProcessingException ?: AudioProcessingException(
                    "Failed to process recording", e,
                )
        }
    }

    private fun writeWavFromPcm(pcmFile: File, wavFile: File, reverse: Boolean, dataLength: Long) {
        try {
            BufferedOutputStream(FileOutputStream(wavFile)).use { out ->
                WavCodec.writeHeader(out, dataLength)
                if (reverse) {
                    RandomAccessFile(pcmFile, "r").use { raf ->
                        val frame = ByteArray(WavCodec.FRAME_SIZE_BYTES)
                        var position = dataLength
                        while (position >= WavCodec.FRAME_SIZE_BYTES) {
                            position -= WavCodec.FRAME_SIZE_BYTES
                            raf.seek(position)
                            raf.readFully(frame)
                            out.write(frame)
                        }
                    }
                } else {
                    BufferedInputStream(FileInputStream(pcmFile)).use { input ->
                        input.copyTo(out, bufferSize = WavCodec.BUFFER_SIZE_BYTES * 4)
                    }
                }
            }
        } catch (e: IOException) {
            throw AudioProcessingException("Failed to write WAV file", e)
        }
    }
}

data class ProcessedAudio(
    val originalFile: File,
    val reversedFile: File,
    val durationMs: Long,
)
