package com.example.checkpointtimer.testing

import com.example.checkpointtimer.data.CheckpointEntity
import com.example.checkpointtimer.data.TemplateDao
import com.example.checkpointtimer.data.TemplateEntity
import com.example.checkpointtimer.data.TemplateWithCheckpoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-memory stand-in for the Room DAO. It deliberately mirrors Room's behaviour for the parts
 * the app relies on: generated ids, and checkpoints disappearing with their template the way
 * `ON DELETE CASCADE` removes them.
 *
 * `upsert` is not overridden — the real transaction body from [TemplateDao] is exercised.
 */
class FakeTemplateDao : TemplateDao {

    private val storedTemplates = LinkedHashMap<Long, TemplateEntity>()
    private val storedCheckpoints = mutableListOf<CheckpointEntity>()
    private var nextTemplateId = 1L
    private var nextCheckpointId = 1L

    val templates: List<TemplateEntity> get() = storedTemplates.values.toList()
    val checkpoints: List<CheckpointEntity> get() = storedCheckpoints.toList()

    override fun observeAll(): Flow<List<TemplateWithCheckpoints>> = flow {
        emit(
            storedTemplates.values
                .sortedBy { it.name.lowercase() }
                .map { TemplateWithCheckpoints(it, checkpointsFor(it.id)) },
        )
    }

    override suspend fun getById(id: Long): TemplateWithCheckpoints? =
        storedTemplates[id]?.let { TemplateWithCheckpoints(it, checkpointsFor(id)) }

    override suspend fun count(): Int = storedTemplates.size

    override suspend fun insertTemplate(template: TemplateEntity): Long {
        val id = nextTemplateId++
        storedTemplates[id] = template.copy(id = id)
        return id
    }

    override suspend fun updateTemplate(template: TemplateEntity) {
        storedTemplates[template.id] = template
    }

    override suspend fun insertCheckpoints(checkpoints: List<CheckpointEntity>) {
        checkpoints.forEach { storedCheckpoints += it.copy(id = nextCheckpointId++) }
    }

    override suspend fun deleteCheckpointsFor(templateId: Long) {
        storedCheckpoints.removeAll { it.templateId == templateId }
    }

    override suspend fun deleteTemplate(id: Long) {
        storedTemplates.remove(id)
        deleteCheckpointsFor(id)
    }

    private fun checkpointsFor(templateId: Long) =
        storedCheckpoints.filter { it.templateId == templateId }.sortedBy { it.orderIndex }
}
