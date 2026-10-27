package com.vorontsov.audioreverser.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vorontsov.audioreverser.data.local.dao.GameRoundDao
import com.vorontsov.audioreverser.data.local.dao.GuessAttemptDao
import com.vorontsov.audioreverser.data.local.entity.GameRoundEntity
import com.vorontsov.audioreverser.data.local.entity.GuessAttemptEntity

@Database(
    entities = [GameRoundEntity::class, GuessAttemptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AudioReverserDatabase : RoomDatabase() {
    abstract fun gameRoundDao(): GameRoundDao
    abstract fun guessAttemptDao(): GuessAttemptDao

    companion object {
        const val NAME = "audio_reverser.db"
    }
}
