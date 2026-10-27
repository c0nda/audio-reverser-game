package com.vorontsov.audioreverser.di

import android.content.Context
import androidx.room.Room
import com.vorontsov.audioreverser.core.audio.AndroidAudioPlayer
import com.vorontsov.audioreverser.core.audio.AndroidAudioRecorder
import com.vorontsov.audioreverser.core.audio.AudioPlayer
import com.vorontsov.audioreverser.core.audio.AudioRecorder
import com.vorontsov.audioreverser.core.common.DefaultDispatcher
import com.vorontsov.audioreverser.core.common.IoDispatcher
import com.vorontsov.audioreverser.core.files.RecordingFileStore
import com.vorontsov.audioreverser.data.audio.FileProviderAudioSharer
import com.vorontsov.audioreverser.data.local.AudioReverserDatabase
import com.vorontsov.audioreverser.data.local.dao.GameRoundDao
import com.vorontsov.audioreverser.data.local.dao.GuessAttemptDao
import com.vorontsov.audioreverser.data.repository.RoundRepositoryImpl
import com.vorontsov.audioreverser.domain.repository.RoundRepository
import com.vorontsov.audioreverser.domain.usecase.AudioSharer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideFileStore(@ApplicationContext context: Context): RecordingFileStore =
        RecordingFileStore(context.filesDir)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AudioReverserDatabase =
        Room.databaseBuilder(context, AudioReverserDatabase::class.java, AudioReverserDatabase.NAME)
            .build()

    @Provides
    fun provideRoundDao(database: AudioReverserDatabase): GameRoundDao = database.gameRoundDao()

    @Provides
    fun provideAttemptDao(database: AudioReverserDatabase): GuessAttemptDao = database.guessAttemptDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindRoundRepository(impl: RoundRepositoryImpl): RoundRepository

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(impl: AndroidAudioRecorder): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(impl: AndroidAudioPlayer): AudioPlayer

    @Binds
    @Singleton
    abstract fun bindAudioSharer(impl: FileProviderAudioSharer): AudioSharer
}
