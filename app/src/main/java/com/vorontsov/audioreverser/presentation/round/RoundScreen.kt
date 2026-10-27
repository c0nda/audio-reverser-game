package com.vorontsov.audioreverser.presentation.round

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.vorontsov.audioreverser.R
import com.vorontsov.audioreverser.domain.model.Attempt
import com.vorontsov.audioreverser.presentation.components.AudioPlaybackRow
import com.vorontsov.audioreverser.presentation.components.ConfirmDialog
import com.vorontsov.audioreverser.presentation.components.RenameDialog
import com.vorontsov.audioreverser.presentation.components.asMessage
import com.vorontsov.audioreverser.presentation.util.formatDuration
import com.vorontsov.audioreverser.presentation.util.formatTimestamp
import com.vorontsov.audioreverser.presentation.util.shareAudio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundScreen(
    onNavigateBack: () -> Unit,
    viewModel: RoundViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRecordingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoundEffect.NavigateBack -> onNavigateBack()
                is RoundEffect.LaunchShare -> context.shareAudio(
                    uriString = effect.uriString,
                    mimeType = effect.mimeType,
                    title = effect.title,
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingRecordingAction?.invoke()
        } else {
            // A second denial means the system will no longer show the prompt, so the
            // only way forward is the app settings screen.
            permissionPermanentlyDenied = activity?.let {
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.RECORD_AUDIO,
                )
            } ?: false
        }
        pendingRecordingAction = null
    }

    val requestPermissionOrRun: (() -> Unit) -> Unit = { action ->
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED -> action()

            activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.RECORD_AUDIO,
                )
            } == true -> {
                pendingRecordingAction = action
                showPermissionRationale = true
            }

            else -> {
                pendingRecordingAction = action
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.round?.title?.ifBlank {
                                stringResource(R.string.round_default_name)
                            } ?: stringResource(R.string.round_title_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.round?.let {
                            Text(
                                text = if (it.isCompleted) {
                                    stringResource(R.string.round_status_completed)
                                } else {
                                    stringResource(R.string.round_status_in_progress)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(RoundIntent.NavigateBack) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (state.round?.isCompleted == false) {
                        IconButton(onClick = { viewModel.onIntent(RoundIntent.RequestRenameRound) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.round_rename))
                        }
                        TextButton(onClick = { viewModel.onIntent(RoundIntent.RequestFinishRound) }) {
                            Text(stringResource(R.string.round_finish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                state.error?.let { error ->
                    ErrorBanner(error.asMessage()) { viewModel.onIntent(RoundIntent.DismissError) }
                }
            }
            if (state.round?.isCompleted == true) {
                item { CompletedBanner() }
            }
            if (state.isProcessing) {
                item { ProcessingIndicator() }
            }
            item {
                SecretSection(
                    state = state,
                    onStartRecording = {
                        requestPermissionOrRun {
                            viewModel.onIntent(RoundIntent.StartSecretRecording)
                        }
                    },
                    onStopRecording = { viewModel.onIntent(RoundIntent.StopSecretRecording) },
                    onCancelRecording = { viewModel.onIntent(RoundIntent.CancelSecretRecording) },
                    onToggleReversed = {
                        state.secretReversedPath?.let { path ->
                            viewModel.onIntent(RoundIntent.TogglePlayback("secret_reversed", path))
                        }
                    },
                    onToggleOriginal = {
                        state.secretOriginalPath?.let { path ->
                            viewModel.onIntent(RoundIntent.TogglePlayback("secret_original", path))
                        }
                    },
                )
            }
            item {
                AttemptsHeader(
                    canRecord = state.round?.isCompleted == false,
                    isBusy = state.isRecording || state.isProcessing,
                    onRecordAttempt = {
                        requestPermissionOrRun {
                            viewModel.onIntent(RoundIntent.StartGuessRecording)
                        }
                    },
                    onStopRecording = { viewModel.onIntent(RoundIntent.StopGuessRecording) },
                    onCancelRecording = { viewModel.onIntent(RoundIntent.CancelSecretRecording) },
                    recordingDurationMs = state.recordingDurationMs,
                    recordingTarget = state.recordingTarget,
                )
            }
            if (state.attempts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.attempts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.attempts, key = { it.attempt.id }) { item ->
                    AttemptCard(
                        item = item,
                        state = state,
                        onToggleOriginal = {
                            viewModel.onIntent(
                                RoundIntent.TogglePlayback(
                                    assetKey = "attempt_${item.attempt.id}_original",
                                    path = item.attempt.originalPath,
                                ),
                            )
                        },
                        onToggleReversed = {
                            viewModel.onIntent(
                                RoundIntent.TogglePlayback(
                                    assetKey = "attempt_${item.attempt.id}_reversed",
                                    path = item.attempt.reversedPath,
                                ),
                            )
                        },
                        onRename = {
                            viewModel.onIntent(RoundIntent.RequestRenameAttempt(item.attempt.id))
                        },
                        onDelete = {
                            viewModel.onIntent(RoundIntent.RequestDeleteAttempt(item.attempt.id))
                        },
                        onShareOriginal = {
                            viewModel.onIntent(
                                RoundIntent.ShareAsset(
                                    path = item.attempt.originalPath,
                                    displayName = attemptShareName(item, false),
                                ),
                            )
                        },
                        onShareReversed = {
                            viewModel.onIntent(
                                RoundIntent.ShareAsset(
                                    path = item.attempt.reversedPath,
                                    displayName = attemptShareName(item, true),
                                ),
                            )
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (state.showFinishConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.round_finish_confirm_title),
            message = stringResource(R.string.round_finish_confirm_message),
            confirmLabel = stringResource(R.string.round_finish_confirm_yes),
            dismissLabel = stringResource(R.string.round_finish_confirm_no),
            onConfirm = { viewModel.onIntent(RoundIntent.ConfirmFinishRound) },
            onDismiss = { viewModel.onIntent(RoundIntent.DismissFinishConfirm) },
        )
    }

    if (state.renameRoundTarget) {
        RenameDialog(
            title = stringResource(R.string.rename_title),
            initialValue = state.round?.title.orEmpty(),
            onSave = { viewModel.onIntent(RoundIntent.ConfirmRenameRound(it)) },
            onDismiss = { viewModel.onIntent(RoundIntent.DismissRenameRound) },
        )
    }

    state.renameAttemptId?.let { attemptId ->
        val attempt = state.attempts.firstOrNull { it.attempt.id == attemptId }
        RenameDialog(
            title = stringResource(R.string.rename_title),
            initialValue = attempt?.attempt?.title.orEmpty(),
            onSave = { viewModel.onIntent(RoundIntent.ConfirmRenameAttempt(attemptId, it)) },
            onDismiss = { viewModel.onIntent(RoundIntent.DismissRenameAttempt) },
        )
    }

    state.deleteAttemptId?.let { attemptId ->
        ConfirmDialog(
            title = stringResource(R.string.attempt_delete_confirm_title),
            message = stringResource(R.string.attempt_delete_confirm_message),
            confirmLabel = stringResource(R.string.attempt_delete_confirm_yes),
            onConfirm = { viewModel.onIntent(RoundIntent.ConfirmDeleteAttempt(attemptId)) },
            onDismiss = { viewModel.onIntent(RoundIntent.DismissDeleteAttempt) },
        )
    }

    if (showPermissionRationale) {
        ConfirmDialog(
            title = stringResource(R.string.recording_permission_needed),
            message = stringResource(R.string.recording_permission_rationale),
            confirmLabel = stringResource(R.string.play),
            onConfirm = {
                showPermissionRationale = false
                pendingRecordingAction?.let { action ->
                    pendingRecordingAction = null
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    pendingRecordingAction = action
                }
            },
            onDismiss = {
                showPermissionRationale = false
                pendingRecordingAction = null
            },
        )
    }

    if (permissionPermanentlyDenied) {
        ConfirmDialog(
            title = stringResource(R.string.recording_permission_denied),
            message = stringResource(R.string.recording_permission_rationale),
            confirmLabel = stringResource(R.string.recording_permission_open_settings),
            onConfirm = {
                permissionPermanentlyDenied = false
                context.openAppSettings()
            },
            onDismiss = { permissionPermanentlyDenied = false },
        )
    }
}

@Composable
private fun SecretSection(
    state: RoundUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onToggleReversed: () -> Unit,
    onToggleOriginal: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.secret_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            when {
                !state.hasSecretRecording -> {
                    if (state.recordingTarget == RecordingTarget.SECRET) {
                        RecordingIndicator(state.recordingDurationMs)
                        Spacer(Modifier.height(12.dp))
                        RecordingButtons(onStopRecording, onCancelRecording)
                    } else {
                        Text(
                            text = stringResource(R.string.secret_not_recorded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onStartRecording, enabled = !state.isProcessing) {
                            Icon(Icons.Filled.Mic, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (state.round?.isCompleted == true) {
                                    stringResource(R.string.secret_rerecord)
                                } else {
                                    stringResource(R.string.secret_record)
                                },
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.secret_reversed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AudioPlaybackRow(
                        label = stringResource(R.string.attempt_reversed),
                        isPlaying = state.isPlaying &&
                            state.playingAssetKey == "secret_reversed",
                        isEnabled = !state.isRecording,
                        isAvailable = state.secretExists,
                        positionMs = if (state.playingAssetKey == "secret_reversed") {
                            state.playbackPositionMs
                        } else {
                            0L
                        },
                        durationMs = if (state.playingAssetKey == "secret_reversed") {
                            state.playbackDurationMs
                        } else {
                            state.secretReversedDurationMs
                        },
                        onToggle = onToggleReversed,
                    )
                    Spacer(Modifier.height(4.dp))
                    SecretOriginalControl(
                        available = state.secretOriginalAvailable,
                        isPlaying = state.isPlaying &&
                            state.playingAssetKey == "secret_original",
                        isEnabled = !state.isRecording,
                        positionMs = if (state.playingAssetKey == "secret_original") {
                            state.playbackPositionMs
                        } else {
                            0L
                        },
                        durationMs = if (state.playingAssetKey == "secret_original") {
                            state.playbackDurationMs
                        } else {
                            state.secretOriginalDurationMs
                        },
                        onToggle = onToggleOriginal,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.recordingTarget == RecordingTarget.SECRET) {
                        RecordingIndicator(state.recordingDurationMs)
                        Spacer(Modifier.height(12.dp))
                        RecordingButtons(onStopRecording, onCancelRecording)
                    } else {
                        OutlinedButton(
                            onClick = onStartRecording,
                            enabled = !state.isProcessing &&
                                state.round?.isCompleted == false,
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.secret_rerecord))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretOriginalControl(
    available: Boolean,
    isPlaying: Boolean,
    isEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
) {
    if (available) {
        AudioPlaybackRow(
            label = stringResource(R.string.attempt_original),
            isPlaying = isPlaying,
            isEnabled = isEnabled,
            isAvailable = true,
            positionMs = positionMs,
            durationMs = durationMs,
            onToggle = onToggle,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = stringResource(R.string.secret_original_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.secret_locked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttemptsHeader(
    canRecord: Boolean,
    isBusy: Boolean,
    onRecordAttempt: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    recordingDurationMs: Long,
    recordingTarget: RecordingTarget,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.attempts_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (recordingTarget == RecordingTarget.ATTEMPT) {
            RecordingIndicator(recordingDurationMs)
            Spacer(Modifier.height(8.dp))
            RecordingButtons(onStopRecording, onCancelRecording)
        } else {
            Button(
                onClick = onRecordAttempt,
                enabled = canRecord && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.attempt_record))
            }
        }
    }
}

@Composable
private fun AttemptCard(
    item: Attempt,
    state: RoundUiState,
    onToggleOriginal: () -> Unit,
    onToggleReversed: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShareOriginal: () -> Unit,
    onShareReversed: () -> Unit,
) {
    val attempt = item.attempt
    val originalKey = "attempt_${attempt.id}_original"
    val reversedKey = "attempt_${attempt.id}_reversed"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${item.number}.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attempt.title.ifBlank {
                            "${stringResource(R.string.attempt_default_name)} ${item.number}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatTimestamp(attempt.createdAt)} · ${formatDuration(attempt.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_title))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.attempt_delete))
                }
            }
            Spacer(Modifier.height(8.dp))
            AudioPlaybackRow(
                label = stringResource(R.string.attempt_original),
                isPlaying = state.isPlaying && state.playingAssetKey == originalKey,
                isEnabled = !state.isRecording,
                isAvailable = attempt.originalExists,
                positionMs = if (state.playingAssetKey == originalKey) state.playbackPositionMs else 0L,
                durationMs = if (state.playingAssetKey == originalKey) state.playbackDurationMs else attempt.durationMs,
                onToggle = onToggleOriginal,
            )
            AudioPlaybackRow(
                label = stringResource(R.string.attempt_reversed),
                isPlaying = state.isPlaying && state.playingAssetKey == reversedKey,
                isEnabled = !state.isRecording,
                isAvailable = attempt.reversedExists,
                positionMs = if (state.playingAssetKey == reversedKey) state.playbackPositionMs else 0L,
                durationMs = if (state.playingAssetKey == reversedKey) state.playbackDurationMs else attempt.durationMs,
                onToggle = onToggleReversed,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShareOriginal, enabled = attempt.originalExists) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.attempt_share),
                        Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("${stringResource(R.string.share_action)} · ${stringResource(R.string.attempt_original)}")
                }
                TextButton(onClick = onShareReversed, enabled = attempt.reversedExists) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.attempt_share),
                        Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("${stringResource(R.string.share_action)} · ${stringResource(R.string.attempt_reversed)}")
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(durationMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.Red, shape = MaterialTheme.shapes.small),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.recording_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun RecordingButtons(onStop: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onStop, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Stop, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.recording_stop))
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.recording_cancel))
        }
    }
}

@Composable
private fun ProcessingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.recording_processing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CompletedBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.round_completed_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rename_cancel))
            }
        }
    }
}

private fun attemptShareName(item: Attempt, reversed: Boolean): String {
    val base = item.attempt.title.ifBlank { "attempt_${item.number}" }
    return if (reversed) "${base}_reversed" else "${base}_original"
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
