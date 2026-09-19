package com.example.checkpointtimer.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val totalDurationMs: Long,
    /** [SoundChoice] encoded with [SoundChoice.toStorageString]. */
    val endSound: String,
    val endVibrate: Boolean,
)

@Entity(
    tableName = "checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class CheckpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val templateId: Long,
    val label: String,
    /** Milliseconds elapsed since the timer started, not a wall-clock timestamp. */
    val triggerMs: Long,
    /** [SoundChoice] encoded with [SoundChoice.toStorageString]. */
    val sound: String,
    val vibrate: Boolean,
    val enabled: Boolean = true,
    /** Position in the editor list; independent of [triggerMs]. */
    val orderIndex: Int = 0,
)

data class TemplateWithCheckpoints(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val checkpoints: List<CheckpointEntity>,
)
