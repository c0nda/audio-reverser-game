package com.vorontsov.audioreverser.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vorontsov.audioreverser.R
import com.vorontsov.audioreverser.domain.model.RoundSummary
import com.vorontsov.audioreverser.presentation.components.ConfirmDialog
import com.vorontsov.audioreverser.presentation.components.RenameDialog
import com.vorontsov.audioreverser.presentation.util.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onOpenRound: (String) -> Unit,
    onCreateRound: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (state.rounds.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                onCreateRound = onCreateRound,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.size(8.dp)) }
            items(state.rounds, key = { it.round.id }) { summary ->
                HistoryRoundCard(
                    summary = summary,
                    onClick = { onOpenRound(summary.round.id) },
                    onRename = { viewModel.onIntent(HistoryIntent.RequestRename(summary.round.id)) },
                    onDelete = { viewModel.onIntent(HistoryIntent.RequestDelete(summary.round.id)) },
                )
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    state.deleteRoundId?.let { roundId ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            message = stringResource(R.string.history_delete_confirm_message),
            confirmLabel = stringResource(R.string.history_delete_confirm_yes),
            onConfirm = { viewModel.onIntent(HistoryIntent.ConfirmDelete(roundId)) },
            onDismiss = { viewModel.onIntent(HistoryIntent.DismissDelete) },
        )
    }

    state.renameRoundId?.let { roundId ->
        val current = state.rounds.firstOrNull { it.round.id == roundId }
        RenameDialog(
            title = stringResource(R.string.rename_title),
            initialValue = current?.round?.title.orEmpty(),
            onSave = { viewModel.onIntent(HistoryIntent.ConfirmRename(roundId, it)) },
            onDismiss = { viewModel.onIntent(HistoryIntent.DismissRename) },
        )
    }
}

@Composable
private fun HistoryRoundCard(
    summary: RoundSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.round.title.ifBlank {
                        stringResource(R.string.round_default_name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.round_item_attempts,
                        summary.attemptCount,
                        summary.attemptCount,
                    ) + " · " + formatTimestamp(summary.round.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val durationMs = summary.round.secretDurationMs ?: 0L
                Text(
                    text = if (durationMs > 0) {
                        "${stringResource(R.string.secret_section_title)}: " +
                            com.vorontsov.audioreverser.presentation.util.formatDuration(durationMs)
                    } else {
                        stringResource(R.string.secret_not_recorded)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_title))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onCreateRound: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.history_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = onCreateRound) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_new_round))
            }
        }
    }
}
