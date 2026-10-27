package com.vorontsov.audioreverser

import android.app.Application
import com.vorontsov.audioreverser.core.audio.AudioSessionManager
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AudioReverserApplication : Application() {

    @Inject
    lateinit var repository: RoundRepository

    @Inject
    lateinit var audioSessionManager: AudioSessionManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            runCatching { repository.cleanupInterruptedDeletions() }
        }
    }

    override fun onTerminate() {
        // Only ever called in the emulator; real devices rely on process death.
        audioSessionManager.shutdown()
        super.onTerminate()
    }
}
