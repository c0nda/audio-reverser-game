package com.vorontsov.audioreverser.core.files

import java.io.File

class RecordingFileStore(filesDir: File) {

    private val recordingsDir = File(filesDir, "recordings").apply { mkdirsOrThrow() }
    private val tmpDir = File(filesDir, "tmp").apply { mkdirsOrThrow() }

    fun newTempPcmFile(): File = File(tmpDir, "${System.nanoTime()}_${java.util.UUID.randomUUID()}.pcm")

    fun newTempWavFile(): File = File(tmpDir, "${System.nanoTime()}_${java.util.UUID.randomUUID()}.wav")

    fun roundDir(roundId: String): File = File(recordingsDir, roundId)

    fun moveIntoRound(tmpFile: File, roundId: String, assetFileName: String): File {
        val dir = roundDir(roundId)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create round directory")
        }
        val target = File(dir, assetFileName)
        if (target.exists()) target.delete()
        if (!tmpFile.renameTo(target)) {
            tmpFile.copyTo(target, overwrite = true)
            tmpFile.delete()
        }
        return target
    }

    fun deleteQuietly(file: File?): Boolean {
        if (file == null) return true
        return try {
            !file.exists() || file.delete()
        } catch (e: SecurityException) {
            false
        }
    }

    fun deleteRoundDir(roundId: String): Boolean {
        val dir = roundDir(roundId)
        if (!dir.exists()) return true
        var clean = true
        dir.listFiles()?.forEach { child ->
            if (!deleteQuietly(child)) clean = false
        }
        if (clean && !dir.delete()) clean = false
        return clean
    }

    fun cleanupTempDir(maxAgeMs: Long = MAX_TEMP_FILE_AGE_MS) {
        val now = System.currentTimeMillis()
        tmpDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                deleteQuietly(file)
            }
        }
    }

    fun cleanupOrphanRoundDirs(knownRoundIds: Set<String>) {
        recordingsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in knownRoundIds) {
                deleteRoundDir(dir.name)
            }
        }
    }

    private fun File.mkdirsOrThrow(): File {
        check(exists() || mkdirs()) { "Unable to create storage directory $absolutePath" }
        return this
    }

    companion object {
        private const val MAX_TEMP_FILE_AGE_MS = 24L * 60 * 60 * 1000
    }
}
