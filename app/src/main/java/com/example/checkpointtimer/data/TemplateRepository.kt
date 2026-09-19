package com.example.checkpointtimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pure data access, with no Android dependencies, so it can be driven by a fake DAO in tests. */
class TemplateRepository(private val dao: TemplateDao) {

    fun observeTemplates(): Flow<List<TimerTemplate>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getTemplate(id: Long): TimerTemplate? = dao.getById(id)?.toDomain()

    suspend fun templateCount(): Int = dao.count()

    /** Creates the template when [TimerTemplate.id] is 0, otherwise replaces it. Returns the id. */
    suspend fun saveTemplate(template: TimerTemplate): Long = dao.upsert(
        template = TemplateEntity(
            id = template.id,
            name = template.name.trim(),
            totalDurationMs = template.totalDurationMs,
            endSound = template.endSound.toStorageString(),
            endVibrate = template.endVibrate,
        ),
        checkpoints = template.checkpoints.mapIndexed { index, checkpoint ->
            CheckpointEntity(
                id = 0L,
                templateId = template.id,
                label = checkpoint.label.trim(),
                triggerMs = checkpoint.triggerMs,
                sound = checkpoint.sound.toStorageString(),
                vibrate = checkpoint.vibrate,
                enabled = checkpoint.enabled,
                orderIndex = index,
            )
        },
    )

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    /** Returns the id of the copy, or null if the source no longer exists. */
    suspend fun duplicateTemplate(id: Long): Long? {
        val source = dao.getById(id)?.toDomain() ?: return null
        return saveTemplate(
            source.copy(
                id = 0L,
                name = "${source.name} copy",
                checkpoints = source.checkpoints.map { it.copy(id = 0L) },
            ),
        )
    }
}
