package com.example.checkpointtimer.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A snapshot of the running timer. The UI renders this and nothing else, so it keeps working
 * across configuration changes and while the app is backgrounded.
 */
data class TimerState(
    val templateId: Long = 0L,
    val templateName: String = "",
    val totalMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val remainingMs: Long = 0L,
    val progress: Float = 0f,
    val status: TimerEngine.Status = TimerEngine.Status.IDLE,
    val nextCheckpointLabel: String? = null,
    val msUntilNextCheckpoint: Long? = null,
    val alert: TimerAlert? = null,
) {
    val isActive: Boolean
        get() = status == TimerEngine.Status.RUNNING || status == TimerEngine.Status.PAUSED
}

/** Emitted whenever a checkpoint or the end of the timer fires. */
data class TimerAlert(
    /** Monotonic, so the UI can react to each alert exactly once. */
    val id: Long,
    val label: String,
    val isFinal: Boolean,
)

/**
 * The single source of truth shared by [TimerService] and the UI. The service publishes,
 * ViewModels observe; neither holds a reference to the other.
 */
object TimerStateHolder {

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var alertCounter = 0L

    /**
     * Replaces the snapshot while carrying any pending alert across, so that a checkpoint
     * alert raised in the same tick as a state update is not dropped before the UI sees it.
     */
    fun publish(state: TimerState) {
        _state.update { state.copy(alert = it.alert) }
    }

    fun publishAlert(label: String, isFinal: Boolean) {
        val alert = TimerAlert(id = ++alertCounter, label = label, isFinal = isFinal)
        _state.update { it.copy(alert = alert) }
    }

    fun clearAlert() {
        _state.update { it.copy(alert = null) }
    }

    fun clear() {
        _state.update { TimerState() }
    }
}
