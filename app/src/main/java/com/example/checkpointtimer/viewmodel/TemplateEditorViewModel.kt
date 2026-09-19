package com.example.checkpointtimer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkpointtimer.data.SoundChoice
import com.example.checkpointtimer.data.TemplateRepository
import com.example.checkpointtimer.data.TimerCheckpoint
import com.example.checkpointtimer.data.TimerTemplate
import com.example.checkpointtimer.timer.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One checkpoint as it is being edited. [key] is a stable identity for list rendering and
 * error lookup; it is not the database id, which only exists after a save.
 */
data class CheckpointDraft(
    val key: Long,
    val id: Long = 0L,
    val label: String = "",
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val sound: SoundChoice = SoundChoice.DEFAULT,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
) {
    val triggerMs: Long get() = ((hours * 60L + minutes) * 60L + seconds) * 1000L
}

data class EditorErrors(
    val name: String? = null,
    val totalDuration: String? = null,
    /** Keyed by [CheckpointDraft.key]. */
    val checkpoints: Map<Long, String> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = name != null || totalDuration != null || checkpoints.isNotEmpty()
}

/** The field a rejected save needs the user to look at. */
enum class EditorField { Name, TotalDuration, Checkpoint }

/**
 * Raised when a save is rejected. A blank name shows its error on a field that may be scrolled
 * out of sight, so the screen surfaces the message itself and jumps to the offending row.
 */
data class SaveRejection(
    /** Monotonic, so the UI can react to each rejected save exactly once. */
    val id: Long,
    val message: String,
    val field: EditorField,
    /** Index into the checkpoint list when [field] is [EditorField.Checkpoint]. */
    val checkpointIndex: Int = 0,
)

data class TemplateEditorUiState(
    val templateId: Long = 0L,
    val name: String = "",
    val hours: Int = 0,
    val minutes: Int = 5,
    val seconds: Int = 0,
    val endSound: SoundChoice = SoundChoice.Alarm,
    val endVibrate: Boolean = true,
    val checkpoints: List<CheckpointDraft> = emptyList(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    /** Validation runs on save; before that the editor stays quiet. */
    val showValidation: Boolean = false,
    val errors: EditorErrors = EditorErrors(),
    val saveRejection: SaveRejection? = null,
) {
    val totalDurationMs: Long get() = ((hours * 60L + minutes) * 60L + seconds) * 1000L
}

class TemplateEditorViewModel(
    private val repository: TemplateRepository,
    private val templateId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _uiState.asStateFlow()

    private var nextKey = 1L
    private var rejectionCounter = 0L

    init {
        if (templateId == 0L) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch { load(templateId) }
        }
    }

    private suspend fun load(id: Long) {
        val template = repository.getTemplate(id)
        _uiState.update { current ->
            if (template == null) {
                current.copy(isLoading = false)
            } else {
                val (hours, minutes, seconds) = template.totalDurationMs.toHoursMinutesSeconds()
                current.copy(
                    templateId = template.id,
                    name = template.name,
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds,
                    endSound = template.endSound,
                    endVibrate = template.endVibrate,
                    checkpoints = template.checkpoints.map { it.toDraft() },
                    isLoading = false,
                )
            }
        }
    }

    fun setName(name: String) = edit { it.copy(name = name) }

    fun setTotalDuration(hours: Int, minutes: Int, seconds: Int) =
        edit { it.copy(hours = hours, minutes = minutes, seconds = seconds) }

    fun setEndSound(sound: SoundChoice) = edit { it.copy(endSound = sound) }

    fun setEndVibrate(vibrate: Boolean) = edit { it.copy(endVibrate = vibrate) }

    fun addCheckpoint() = edit { state ->
        // Each new checkpoint lands a minute after the last one, or at one minute for the first.
        val triggerMs = state.checkpoints.lastOrNull()?.let { it.triggerMs + 60_000L }
            ?: DEFAULT_CHECKPOINT_OFFSET_MS
        val (hours, minutes, seconds) = triggerMs.toHoursMinutesSeconds()
        state.copy(
            checkpoints = state.checkpoints + CheckpointDraft(
                key = nextKey++,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
            ),
        )
    }

    fun deleteCheckpoint(key: Long) = edit { state ->
        state.copy(checkpoints = state.checkpoints.filterNot { it.key == key })
    }

    /** Moves a checkpoint one position up ([delta] -1) or down (+1) in the editor list. */
    fun moveCheckpoint(key: Long, delta: Int) = edit { state ->
        val from = state.checkpoints.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in state.checkpoints.indices) {
            state
        } else {
            state.copy(checkpoints = state.checkpoints.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun updateCheckpoint(key: Long, transform: (CheckpointDraft) -> CheckpointDraft) = edit { state ->
        state.copy(
            checkpoints = state.checkpoints.map { if (it.key == key) transform(it) else it },
        )
    }

    fun save() {
        val state = _uiState.value
        val errors = validate(state)
        if (errors.hasErrors) {
            val rejection = errors.toRejection(state)
            _uiState.update {
                it.copy(errors = errors, showValidation = true, saveRejection = rejection)
            }
            return
        }

        viewModelScope.launch {
            repository.saveTemplate(
                TimerTemplate(
                    id = state.templateId,
                    name = state.name,
                    totalDurationMs = state.totalDurationMs,
                    endSound = state.endSound,
                    endVibrate = state.endVibrate,
                    checkpoints = state.checkpoints.map { draft ->
                        TimerCheckpoint(
                            id = draft.id,
                            label = draft.label.ifBlank {
                                "${formatDuration(draft.triggerMs)} checkpoint"
                            },
                            triggerMs = draft.triggerMs,
                            sound = draft.sound,
                            vibrate = draft.vibrate,
                            enabled = draft.enabled,
                        )
                    },
                ),
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** Applies a change and, once the user has tried to save, keeps validation live. */
    private fun edit(transform: (TemplateEditorUiState) -> TemplateEditorUiState) {
        _uiState.update { current ->
            val updated = transform(current)
            if (updated.showValidation) updated.copy(errors = validate(updated)) else updated
        }
    }

    /**
     * Picks the first problem worth interrupting the user about, in the order the fields appear
     * on screen, so the message matches the row the UI scrolls to.
     */
    private fun EditorErrors.toRejection(state: TemplateEditorUiState): SaveRejection {
        val id = ++rejectionCounter
        name?.let { return SaveRejection(id, it, EditorField.Name) }
        totalDuration?.let { return SaveRejection(id, it, EditorField.TotalDuration) }

        val (key, message) = checkpoints.entries.first()
        return SaveRejection(
            id = id,
            message = message,
            field = EditorField.Checkpoint,
            checkpointIndex = state.checkpoints.indexOfFirst { it.key == key }.coerceAtLeast(0),
        )
    }

    private fun validate(state: TemplateEditorUiState): EditorErrors {
        val total = state.totalDurationMs
        val checkpointErrors = mutableMapOf<Long, String>()
        val takenTimes = mutableSetOf<Long>()

        state.checkpoints.forEach { checkpoint ->
            // A disabled checkpoint never fires, so it is not worth blocking a save over.
            if (!checkpoint.enabled) return@forEach
            val trigger = checkpoint.triggerMs
            when {
                trigger <= 0L ->
                    checkpointErrors[checkpoint.key] = "Trigger time must be greater than 0"
                total <= 0L ->
                    checkpointErrors[checkpoint.key] = "Set the total duration first"
                trigger >= total ->
                    checkpointErrors[checkpoint.key] = "Must be less than the total duration"
                !takenTimes.add(trigger) ->
                    checkpointErrors[checkpoint.key] = "Another checkpoint already fires then"
            }
        }

        return EditorErrors(
            name = if (state.name.isBlank()) "Give the template a name" else null,
            totalDuration = if (total <= 0L) "Total duration must be greater than 0" else null,
            checkpoints = checkpointErrors,
        )
    }

    private fun TimerCheckpoint.toDraft(): CheckpointDraft {
        val (hours, minutes, seconds) = triggerMs.toHoursMinutesSeconds()
        return CheckpointDraft(
            key = nextKey++,
            id = id,
            label = label,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            sound = sound,
            vibrate = vibrate,
            enabled = enabled,
        )
    }

    private fun Long.toHoursMinutesSeconds(): Triple<Int, Int, Int> {
        val totalSeconds = this / 1000L
        return Triple(
            (totalSeconds / 3600L).toInt(),
            ((totalSeconds % 3600L) / 60L).toInt(),
            (totalSeconds % 60L).toInt(),
        )
    }

    private companion object {
        const val DEFAULT_CHECKPOINT_OFFSET_MS = 60_000L
    }
}
