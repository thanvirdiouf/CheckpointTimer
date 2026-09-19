package com.example.checkpointtimer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checkpointtimer.timer.TimerEngine
import com.example.checkpointtimer.timer.formatDuration
import com.example.checkpointtimer.viewmodel.AppViewModelProvider
import com.example.checkpointtimer.viewmodel.TimerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onExit: () -> Unit,
    viewModel: TimerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leave the screen once the timer it was showing is gone — cancelled here or from the
    // notification — rather than stranding the user on an empty timer.
    var sawTimer by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status != TimerEngine.Status.IDLE) {
            sawTimer = true
        } else if (sawTimer) {
            onExit()
        }
    }

    // Checkpoint alerts flash briefly, then are consumed so they are not replayed.
    LaunchedEffect(state.alert?.id) {
        if (state.alert != null) {
            delay(ALERT_VISIBLE_MS)
            viewModel.consumeAlert()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.templateName.ifBlank { "Timer" }) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AnimatedVisibility(visible = state.alert != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = state.alert?.label.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(260.dp),
                    strokeWidth = 14.dp,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDuration(state.remainingMs),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (state.status == TimerEngine.Status.PAUSED) {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        text = "of ${formatDuration(state.totalMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (state.status) {
                TimerEngine.Status.FINISHED -> {
                    Text(
                        text = "Time's up",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TimerEngine.Status.IDLE -> {
                    Text(
                        text = "Starting…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    NextCheckpointCard(
                        label = state.nextCheckpointLabel,
                        msUntil = state.msUntilNextCheckpoint,
                    )
                }
            }

            Controls(
                state = state.status,
                onPauseResume = {
                    if (state.status == TimerEngine.Status.PAUSED) viewModel.resume() else viewModel.pause()
                },
                onReset = viewModel::reset,
                onCancel = viewModel::stop,
                onDone = onExit,
            )
        }
    }
}

@Composable
private fun NextCheckpointCard(label: String?, msUntil: Long?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (label == null) {
                Text(
                    text = "No checkpoints left",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Next: $label",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "in ${formatDuration(msUntil ?: 0L)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: TimerEngine.Status,
    onPauseResume: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state == TimerEngine.Status.FINISHED) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Run again")
            }
        } else {
            Button(onClick = onPauseResume, modifier = Modifier.fillMaxWidth()) {
                Text(if (state == TimerEngine.Status.PAUSED) "Resume" else "Pause")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private const val ALERT_VISIBLE_MS = 2_500L
