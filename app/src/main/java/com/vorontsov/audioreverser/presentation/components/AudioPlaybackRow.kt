package com.vorontsov.audioreverser.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vorontsov.audioreverser.R
import com.vorontsov.audioreverser.presentation.util.formatDuration

@Composable
fun AudioPlaybackRow(
    label: String,
    isPlaying: Boolean,
    isEnabled: Boolean,
    isAvailable: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val actionDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play)

            IconButton(
                onClick = onToggle,
                enabled = isEnabled && isAvailable,
                modifier = Modifier.semantics { contentDescription = "$actionDescription: $label" },
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(120.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = if (isAvailable) {
                    "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
                } else {
                    stringResource(R.string.attempt_file_missing)
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
