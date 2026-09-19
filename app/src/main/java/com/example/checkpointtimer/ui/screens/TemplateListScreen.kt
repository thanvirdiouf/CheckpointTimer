package com.example.checkpointtimer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.checkpointtimer.data.TimerTemplate
import com.example.checkpointtimer.timer.TimerEngine
import com.example.checkpointtimer.timer.TimerStateHolder
import com.example.checkpointtimer.timer.formatDuration
import com.example.checkpointtimer.viewmodel.AppViewModelProvider
import com.example.checkpointtimer.viewmodel.TemplateListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    onStartTemplate: (Long) -> Unit,
    onOpenRunningTimer: () -> Unit,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (Long) -> Unit,
    viewModel: TemplateListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerState by TimerStateHolder.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TimerTemplate?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Checkpoint Timer") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTemplate) {
                Icon(Icons.Default.Add, contentDescription = "New template")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (timerState.status != TimerEngine.Status.IDLE) {
                RunningTimerBanner(
                    title = timerState.templateName,
                    subtitle = when (timerState.status) {
                        TimerEngine.Status.PAUSED ->
                            "Paused · ${formatDuration(timerState.remainingMs)} left"
                        TimerEngine.Status.FINISHED -> "Time's up"
                        else -> "${formatDuration(timerState.remainingMs)} left"
                    },
                    onClick = onOpenRunningTimer,
                )
            }

            if (uiState.templates.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No templates yet.\nTap + to build your first timer.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    // The extra bottom padding keeps the last card clear of the FAB.
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.templates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { onStartTemplate(template.id) },
                            onEdit = { onEditTemplate(template.id) },
                            onDuplicate = { viewModel.duplicate(template.id) },
                            onDelete = { pendingDelete = template },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete template?") },
            text = { Text("\"${template.name}\" will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(template.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: TimerTemplate,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(formatDuration(template.totalDurationMs))
                        append(" · ")
                        append(
                            when (template.checkpoints.size) {
                                0 -> "no checkpoints"
                                1 -> "1 checkpoint"
                                else -> "${template.checkpoints.size} checkpoints"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Template options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningTimerBanner(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.ifBlank { "Timer" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
