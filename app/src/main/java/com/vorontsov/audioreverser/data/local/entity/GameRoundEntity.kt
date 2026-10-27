package com.vorontsov.audioreverser.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_rounds")
data class GameRoundEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "secret_original_path")
    val secretOriginalPath: String?,
    @ColumnInfo(name = "secret_reversed_path")
    val secretReversedPath: String?,
    @ColumnInfo(name = "secret_duration_ms")
    val secretDurationMs: Long?,
    /**
     * Set when a deletion has been requested but file cleanup may still be pending.
     * Rows marked for deletion are hidden from queries and removed by the startup
     * sweeper if the process dies before file cleanup finishes.
     */
    @ColumnInfo(name = "pending_deletion", defaultValue = "0")
    val pendingDeletion: Boolean = false,
)
