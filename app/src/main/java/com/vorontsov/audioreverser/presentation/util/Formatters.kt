package com.vorontsov.audioreverser.presentation.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun formatTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
