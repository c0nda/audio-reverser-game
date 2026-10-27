package com.vorontsov.audioreverser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "guess_attempts",
    foreignKeys = [
        ForeignKey(
            entity = GameRoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["round_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["round_id"]),
    ],
)
data class GuessAttemptEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "round_id")
    val roundId: String,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "original_audio_path")
    val originalAudioPath: String,
    @ColumnInfo(name = "reversed_audio_path")
    val reversedAudioPath: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "pending_deletion", defaultValue = "0")
    val pendingDeletion: Boolean = false,
)
