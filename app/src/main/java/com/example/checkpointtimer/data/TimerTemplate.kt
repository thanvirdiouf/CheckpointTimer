package com.example.checkpointtimer.data

/** A saved timer configuration, decoupled from how Room happens to store it. */
data class TimerTemplate(
    val id: Long = 0L,
    val name: String,
    val totalDurationMs: Long,
    val endSound: SoundChoice,
    val endVibrate: Boolean,
    val checkpoints: List<TimerCheckpoint> = emptyList(),
)

data class TimerCheckpoint(
    val id: Long = 0L,
    val label: String,
    val triggerMs: Long,
    val sound: SoundChoice,
    val vibrate: Boolean,
    val enabled: Boolean = true,
)

fun TemplateWithCheckpoints.toDomain(): TimerTemplate = TimerTemplate(
    id = template.id,
    name = template.name,
    totalDurationMs = template.totalDurationMs,
    endSound = SoundChoice.fromStorageString(template.endSound),
    endVibrate = template.endVibrate,
    // orderIndex is what the user arranged in the editor; triggerMs breaks ties so that
    // rows written before ordering existed still come back deterministically.
    checkpoints = checkpoints
        .sortedWith(compareBy({ it.orderIndex }, { it.triggerMs }))
        .map { it.toDomain() },
)

fun CheckpointEntity.toDomain(): TimerCheckpoint = TimerCheckpoint(
    id = id,
    label = label,
    triggerMs = triggerMs,
    sound = SoundChoice.fromStorageString(sound),
    vibrate = vibrate,
    enabled = enabled,
)
