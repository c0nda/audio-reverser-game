package com.vorontsov.audioreverser.data.audio

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.vorontsov.audioreverser.core.common.IoDispatcher
import com.vorontsov.audioreverser.domain.usecase.ShareableAudio
import com.vorontsov.audioreverser.domain.usecase.AudioSharer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileProviderAudioSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioSharer {

    private val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }

    init {
        val cutoff = System.currentTimeMillis() - MAX_SHARE_COPY_AGE_MS
        sharedDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    override suspend fun prepareShare(sourcePath: String, displayName: String): ShareableAudio =
        withContext(ioDispatcher) {
            val source = File(sourcePath)
            if (!source.exists() || !source.isFile || source.length() == 0L) {
                throw IOException("Recording file is missing or empty")
            }

            val target = File(sharedDir, "${System.nanoTime()}_${sanitize(displayName)}")

            try {
                source.copyTo(target, overwrite = true)
            } catch (e: IOException) {
                throw IOException("Unable to prepare the recording for sharing", e)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            ShareableAudio(
                uriString = uri.toString(),
                displayName = displayName,
            )
        }

    private fun sanitize(name: String): String {
        val base = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "recording" }
        return if (base.endsWith(".wav")) base else "$base.wav"
    }

    companion object {
        private const val MAX_SHARE_COPY_AGE_MS = 24L * 60 * 60 * 1000
    }
}
