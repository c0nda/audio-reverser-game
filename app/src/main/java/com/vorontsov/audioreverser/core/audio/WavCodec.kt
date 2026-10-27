package com.vorontsov.audioreverser.core.audio

import java.io.OutputStream


object WavCodec {

    const val SAMPLE_RATE = 44_100
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    const val FRAME_SIZE_BYTES = CHANNEL_COUNT * BYTES_PER_SAMPLE
    const val HEADER_SIZE_BYTES = 44
    const val BYTE_RATE = SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE
    const val BLOCK_ALIGN = CHANNEL_COUNT * BYTES_PER_SAMPLE
    const val SAMPLES_PER_CHUNK = 1024
    const val BUFFER_SIZE_BYTES = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE

    fun writeHeader(out: OutputStream, dataLengthBytes: Long) {
        require(dataLengthBytes >= 0) { "dataLengthBytes must be non-negative" }
        val riffChunkSize = 36L + dataLengthBytes
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeLeInt(riffChunkSize.toInt())
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeLeInt(16) // PCM fmt chunk size
        out.writeLeShort(1) // PCM format tag
        out.writeLeShort(CHANNEL_COUNT)
        out.writeLeInt(SAMPLE_RATE)
        out.writeLeInt(BYTE_RATE)
        out.writeLeShort(BLOCK_ALIGN)
        out.writeLeShort(BITS_PER_SAMPLE)
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeLeInt(dataLengthBytes.toInt())
    }

    fun reverseFrames(pcmData: ByteArray, frameSizeBytes: Int = FRAME_SIZE_BYTES): ByteArray {
        require(frameSizeBytes > 0) { "frameSizeBytes must be positive" }
        val frameCount = pcmData.size / frameSizeBytes
        val out = ByteArray(frameCount * frameSizeBytes)
        for (i in 0 until frameCount) {
            val src = i * frameSizeBytes
            val dst = (frameCount - 1 - i) * frameSizeBytes
            pcmData.copyInto(out, dst, src, src + frameSizeBytes)
        }
        return out
    }

    fun durationMs(dataLengthBytes: Long): Long {
        if (dataLengthBytes <= 0) return 0L
        return dataLengthBytes * 1000 / BYTE_RATE
    }

    private fun OutputStream.writeLeInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun OutputStream.writeLeShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
