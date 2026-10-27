package com.vorontsov.audioreverser.data.local.dao

import androidx.room.ColumnInfo

data class RoundAttemptCount(
    @ColumnInfo(name = "round_id")
    val roundId: String,
    val count: Int,
)
