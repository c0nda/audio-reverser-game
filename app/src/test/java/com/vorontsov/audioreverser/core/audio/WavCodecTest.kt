package com.vorontsov.audioreverser.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavCodecTest {

    @Test
    fun `reverses a minimal valid sequence of frames`() {
        // Two mono 16-bit frames: 0x0102 and 0x0304 (little-endian byte order).
        val pcm = byteArrayOf(0x02, 0x01, 0x04, 0x03)
        val reversed = WavCodec.reverseFrames(pcm)
        assertArrayEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), reversed)
    }

    @Test
    fun `reverses multiple frames and preserves sample count`() {
        val frames = (0 until 10).flatMap { listOf(it.toByte(), 0) }
        val pcm = frames.toByteArray()
        val reversed = WavCodec.reverseFrames(pcm)
        assertEquals(pcm.size, reversed.size)
        // First output frame must equal the last input frame.
        assertArrayEquals(byteArrayOf(9, 0), reversed.copyOfRange(0, 2))
        assertArrayEquals(byteArrayOf(0, 0), reversed.copyOfRange(reversed.size - 2, reversed.size))
    }

    @Test
    fun `preserves channel ordering within stereo frames`() {
        // Two stereo frames (L,R): [10,20] and [30,40].
        val pcm = byteArrayOf(10, 20, 30, 40)
        val reversed = WavCodec.reverseFrames(pcm, frameSizeBytes = 2)
        // Frames swap order, but L stays before R inside each frame.
        assertArrayEquals(byteArrayOf(30, 40, 10, 20), reversed)
    }

    @Test
    fun `empty input produces empty output`() {
        assertArrayEquals(ByteArray(0), WavCodec.reverseFrames(ByteArray(0)))
    }

    @Test
    fun `incomplete trailing frame is dropped`() {
        // 3 bytes with a 2-byte frame size: one complete frame plus one stray byte.
        val pcm = byteArrayOf(1, 2, 3)
        val reversed = WavCodec.reverseFrames(pcm)
        assertArrayEquals(byteArrayOf(1, 2), reversed)
    }

    @Test
    fun `invalid frame size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WavCodec.reverseFrames(byteArrayOf(1, 2), frameSizeBytes = 0)
        }
    }

    @Test
    fun `wav header contains correct format fields`() {
        val dataLength = 400L
        val out = ByteArrayOutputStream()
        WavCodec.writeHeader(out, dataLength)
        val bytes = out.toByteArray()

        assertEquals(44, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals(16, readIntLe(bytes, 16))
        assertEquals(1, readShortLe(bytes, 20)) // PCM
        assertEquals(1, readShortLe(bytes, 22)) // mono
        assertEquals(44_100, readIntLe(bytes, 24))
        assertEquals(88_200, readIntLe(bytes, 28)) // byte rate
        assertEquals(2, readShortLe(bytes, 32)) // block align
        assertEquals(16, readShortLe(bytes, 34)) // bits per sample
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(dataLength.toInt(), readIntLe(bytes, 40))
        assertEquals(36 + dataLength, readIntLe(bytes, 4).toLong())
    }

    @Test
    fun `duration is derived from byte rate`() {
        assertEquals(1000L, WavCodec.durationMs(88_200L))
        assertEquals(500L, WavCodec.durationMs(44_100L))
        assertEquals(0L, WavCodec.durationMs(0L))
        assertEquals(0L, WavCodec.durationMs(-5L))
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}
