package com.vorontsov.audioreverser.core.audio

import com.vorontsov.audioreverser.core.files.RecordingFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioReverserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `produces independent original and reversed WAV files`() = runBlocking {
        val store = RecordingFileStore(tempFolder.root)
        val reverser = AudioReverser(store, Dispatchers.Unconfined)

        val frames = listOf(
            byteArrayOf(0x10, 0x00),
            byteArrayOf(0x20, 0x00),
            byteArrayOf(0x30, 0x00),
        ).flatMap { it.toList() }.toByteArray()
        val pcm = File(tempFolder.root, "sample.pcm").apply { writeBytes(frames) }

        val result = reverser.reverse("round-1", "secret", pcm)

        assertTrue(result.originalFile.exists())
        assertTrue(result.reversedFile.exists())
        assertFalse("PCM temp file must be consumed", pcm.exists())

        val originalData = readDataSection(result.originalFile)
        val reversedData = readDataSection(result.reversedFile)
        assertArrayEquals(frames, originalData)
        assertArrayEquals(
            listOf(0x30, 0x00, 0x20, 0x00, 0x10, 0x00).map { it.toByte() }.toByteArray(),
            reversedData,
        )
        assertEquals(3L * 1000 / (WavCodec.SAMPLE_RATE), result.durationMs)
        assertNotNull(result.originalFile.parentFile)
        assertEquals("round-1", result.originalFile.parentFile!!.name)
    }

    @Test
    fun `reversal of empty pcm still produces valid wav files`() = runBlocking {
        val store = RecordingFileStore(tempFolder.root)
        val reverser = AudioReverser(store, Dispatchers.Unconfined)
        val pcm = File(tempFolder.root, "empty.pcm").apply { createNewFile() }

        val result = reverser.reverse("round-2", "secret", pcm)

        assertTrue(result.originalFile.exists())
        assertTrue(result.reversedFile.exists())
        assertEquals(0, readDataSection(result.originalFile).size)
        assertEquals(0, readDataSection(result.reversedFile).size)
        assertEquals(44L, result.originalFile.length())
    }

    private fun readDataSection(wavFile: File): ByteArray {
        val bytes = wavFile.readBytes()
        val dataLength = (bytes[40].toInt() and 0xFF) or
            ((bytes[41].toInt() and 0xFF) shl 8) or
            ((bytes[42].toInt() and 0xFF) shl 16) or
            ((bytes[43].toInt() and 0xFF) shl 24)
        return bytes.copyOfRange(44, 44 + dataLength)
    }
}
