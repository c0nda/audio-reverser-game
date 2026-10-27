package com.vorontsov.audioreverser.core.audio

import android.media.MediaPlayer
import com.vorontsov.audioreverser.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class AndroidAudioPlayer @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioPlayer {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentAssetKey: String? = null

    override suspend fun play(assetKey: String, file: File) {
        withContext(ioDispatcher) {
            synchronized(lock) {
                releasePlayerLocked()

                if (!file.exists()) {
                    throw PlaybackException("Audio file is missing")
                }

                val player = MediaPlayer()

                try {
                    player.setDataSource(file.absolutePath)

                    player.setOnCompletionListener {
                        scope.launch { stop() }
                    }

                    player.setOnErrorListener { _, _, _ ->
                        scope.launch { stop() }
                        true
                    }

                    player.prepare()
                    player.start()

                    mediaPlayer = player
                    currentAssetKey = assetKey

                    val duration = player.duration.toLong().coerceAtLeast(0L)
                    _state.value = PlaybackState.Playing(assetKey, 0L, duration)
                    startProgressUpdates(player, assetKey, duration)
                } catch (e: IOException) {
                    player.release()
                    throw PlaybackException("Unable to play this recording", e)
                } catch (e: IllegalArgumentException) {
                    player.release()
                    throw PlaybackException("Audio file is corrupted", e)
                } catch (e: SecurityException) {
                    player.release()
                    throw PlaybackException("Audio file is inaccessible", e)
                }
            }
        }
    }

    private fun startProgressUpdates(player: MediaPlayer, assetKey: String, durationMs: Long) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(PROGRESS_UPDATE_INTERVAL_MS.milliseconds)
                val position = synchronized(lock) {
                    val current: MediaPlayer? = mediaPlayer
                    if (current == null || current !== player || !current.isPlaying) {
                        null
                    } else {
                        current.currentPosition.toLong().coerceIn(0L, durationMs)
                    }
                } ?: break
                _state.value = PlaybackState.Playing(assetKey, position, durationMs)
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            if (!player.isPlaying) return
            player.pause()
            val key = currentAssetKey ?: return
            val position = player.currentPosition.toLong()
            val duration = player.duration.toLong().coerceAtLeast(0L)
            progressJob?.cancel()
            _state.value = PlaybackState.Paused(key, position, duration)
        }
    }

    override fun resume() {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            val key = currentAssetKey ?: return
            if (player.isPlaying) return

            try {
                player.start()
            } catch (e: IllegalStateException) {
                releasePlayerLocked()
                _state.value = PlaybackState.Idle
                return
            }

            val duration = player.duration.toLong().coerceAtLeast(0L)
            val position = player.currentPosition.toLong()
            _state.value = PlaybackState.Playing(key, position, duration)
            startProgressUpdates(player, key, duration)
        }
    }

    override fun stop() {
        synchronized(lock) {
            releasePlayerLocked()
            _state.value = PlaybackState.Idle
        }
    }

    override fun seekTo(positionMs: Long) {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            val key = currentAssetKey ?: return
            val duration = player.duration.toLong().coerceAtLeast(0L)
            val target = positionMs.coerceIn(0L, duration)
            try {
                player.seekTo(target.toInt())
            } catch (e: IllegalStateException) {
                return
            }
            _state.value = if (player.isPlaying) {
                PlaybackState.Playing(key, target, duration)
            } else {
                PlaybackState.Paused(key, target, duration)
            }
        }
    }

    private fun releasePlayerLocked() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (e: IllegalStateException) {
                // Player was already in a terminal state.
            }
            player.release()
        }
        mediaPlayer = null
        currentAssetKey = null
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
}
