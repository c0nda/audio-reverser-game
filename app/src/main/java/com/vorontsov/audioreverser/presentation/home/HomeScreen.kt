package com.vorontsov.audioreverser.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vorontsov.audioreverser.R
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.presentation.components.UiError
import com.vorontsov.audioreverser.presentation.components.asMessage
import com.vorontsov.audioreverser.presentation.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onOpenRound: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            NewRoundCard(
                onNewRound = { onIntent(HomeIntent.CreateRound) },
                onContinue = { state.unfinishedRoundId?.let(onOpenRound) },
                hasUnfinished = state.hasUnfinishedRound,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_recent_rounds),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.home_open_history))
                }
            }
            if (state.rounds.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.history_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rounds, key = { it.round.id }) { summary ->
                        RecentRoundCard(summary = summary, onClick = { onOpenRound(summary.round.id) })
                    }
                }
            }
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(error)
            }
        }
    }
}

@Composable
private fun NewRoundCard(
    onNewRound: () -> Unit,
    onContinue: () -> Unit,
    hasUnfinished: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.home_new_round_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewRound, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.home_new_round))
                }
                if (hasUnfinished) {
                    Button(onClick = onContinue) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRoundCard(summary: RoundSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.round.title.ifBlank { stringResource(R.string.round_default_name) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatTimestamp(summary.round.createdAt)} · ${summary.attemptCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(completed = summary.round.isCompleted)
        }
    }
}

@Composable
private fun StatusChip(completed: Boolean) {
    val label = if (completed) {
        stringResource(R.string.round_status_completed)
    } else {
        stringResource(R.string.home_unfinished_badge)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (completed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    )
}

@Composable
private fun ErrorBanner(error: UiError) {
    Text(
        text = error.asMessage(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
