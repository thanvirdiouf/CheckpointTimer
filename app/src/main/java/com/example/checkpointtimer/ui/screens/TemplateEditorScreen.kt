package com.example.checkpointtimer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checkpointtimer.data.SoundChoice
import com.example.checkpointtimer.timer.formatDuration
import com.example.checkpointtimer.ui.components.DurationInput
import com.example.checkpointtimer.ui.components.SoundPicker
import com.example.checkpointtimer.viewmodel.AppViewModelProvider
import com.example.checkpointtimer.viewmodel.CheckpointDraft
import com.example.checkpointtimer.viewmodel.EditorField
import com.example.checkpointtimer.viewmodel.SaveRejection
import com.example.checkpointtimer.viewmodel.TemplateEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: Long,
    onDone: () -> Unit,
    viewModel: TemplateEditorViewModel = viewModel(
        key = "template-editor-$templateId",
        factory = AppViewModelProvider.editorFactory(templateId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    // A rejected save used to look like nothing happening: the message lives on a field that may
    // be scrolled out of view. Say what is wrong and bring the offending row into sight.
    LaunchedEffect(state.saveRejection?.id) {
        val rejection = state.saveRejection ?: return@LaunchedEffect
        listState.animateScrollToItem(itemIndexFor(rejection))
        snackbarHostState.showSnackbar(rejection.message)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == 0L) "New template" else "Edit template") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Discard")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The window is edge-to-edge, so the bar has to move itself clear of
                        // the navigation bar and, while a field is focused, the keyboard.
                        // Scaffold only insets its content, not the bars.
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) {
                        Text("Save")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Template name") },
                    singleLine = true,
                    isError = state.errors.name != null,
                    supportingText = state.errors.name?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SectionHeader("Total duration")
                DurationInput(
                    hours = state.hours,
                    minutes = state.minutes,
                    seconds = state.seconds,
                    onDurationChange = viewModel::setTotalDuration,
                    isError = state.errors.totalDuration != null,
                )
                state.errors.totalDuration?.let { ErrorText(it) }
                Text(
                    text = "Runs for ${formatDuration(state.totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionHeader("End of timer")
                SoundPicker(
                    label = "End sound",
                    sound = state.endSound,
                    onSoundChange = viewModel::setEndSound,
                )
                ToggleRow(
                    label = "Vibrate at the end",
                    checked = state.endVibrate,
                    onCheckedChange = viewModel::setEndVibrate,
                )
            }

            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("Checkpoints", modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::addCheckpoint) { Text("Add") }
                }
            }

            if (state.checkpoints.isEmpty()) {
                item {
                    Text(
                        text = "No checkpoints. The timer will just ring at the end.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.checkpoints, key = { it.key }) { checkpoint ->
                CheckpointCard(
                    draft = checkpoint,
                    error = state.errors.checkpoints[checkpoint.key],
                    canMoveUp = state.checkpoints.firstOrNull()?.key != checkpoint.key,
                    canMoveDown = state.checkpoints.lastOrNull()?.key != checkpoint.key,
                    onLabelChange = { label ->
                        viewModel.updateCheckpoint(checkpoint.key) { it.copy(label = label) }
                    },
                    onDurationChange = { hours, minutes, seconds ->
                        viewModel.updateCheckpoint(checkpoint.key) {
                            it.copy(hours = hours, minutes = minutes, seconds = seconds)
                        }
                    },
                    onSoundChange = { sound ->
                        viewModel.updateCheckpoint(checkpoint.key) { it.copy(sound = sound) }
                    },
                    onVibrateChange = { vibrate ->
                        viewModel.updateCheckpoint(checkpoint.key) { it.copy(vibrate = vibrate) }
                    },
                    onEnabledChange = { enabled ->
                        viewModel.updateCheckpoint(checkpoint.key) { it.copy(enabled = enabled) }
                    },
                    onMoveUp = { viewModel.moveCheckpoint(checkpoint.key, -1) },
                    onMoveDown = { viewModel.moveCheckpoint(checkpoint.key, 1) },
                    onDelete = { viewModel.deleteCheckpoint(checkpoint.key) },
                )
            }
        }
    }
}

/**
 * Row order above the checkpoints: name, total duration, end of timer, checkpoints header.
 * The empty-state row that follows only exists when there are no checkpoints, and a rejected
 * save can only point at a checkpoint when there is at least one.
 */
private const val ROWS_BEFORE_CHECKPOINTS = 4

private fun itemIndexFor(rejection: SaveRejection): Int = when (rejection.field) {
    EditorField.Name -> 0
    EditorField.TotalDuration -> 1
    EditorField.Checkpoint -> ROWS_BEFORE_CHECKPOINTS + rejection.checkpointIndex
}

@Composable
private fun CheckpointCard(
    draft: CheckpointDraft,
    error: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLabelChange: (String) -> Unit,
    onDurationChange: (Int, Int, Int) -> Unit,
    onSoundChange: (SoundChoice) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .alpha(if (draft.enabled) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fires at ${formatDuration(draft.triggerMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete checkpoint")
                }
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = onLabelChange,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DurationInput(
                hours = draft.hours,
                minutes = draft.minutes,
                seconds = draft.seconds,
                onDurationChange = onDurationChange,
                isError = error != null,
            )

            SoundPicker(
                label = "Sound",
                sound = draft.sound,
                onSoundChange = onSoundChange,
            )

            ToggleRow(
                label = "Vibrate",
                checked = draft.vibrate,
                onCheckedChange = onVibrateChange,
            )
            ToggleRow(
                label = "Enabled",
                checked = draft.enabled,
                onCheckedChange = onEnabledChange,
            )

            error?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
