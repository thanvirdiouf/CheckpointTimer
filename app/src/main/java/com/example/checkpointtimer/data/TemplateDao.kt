package com.example.checkpointtimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TemplateWithCheckpoints>>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateWithCheckpoints?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Insert
    suspend fun insertCheckpoints(checkpoints: List<CheckpointEntity>)

    @Query("DELETE FROM checkpoints WHERE templateId = :templateId")
    suspend fun deleteCheckpointsFor(templateId: Long)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    /**
     * Writes a template and replaces its checkpoints in one transaction. Pass a template
     * with `id == 0` to create a new row. Returns the template id.
     */
    @Transaction
    suspend fun upsert(template: TemplateEntity, checkpoints: List<CheckpointEntity>): Long {
        val templateId = if (template.id == 0L) {
            insertTemplate(template)
        } else {
            updateTemplate(template)
            template.id
        }
        deleteCheckpointsFor(templateId)
        insertCheckpoints(checkpoints.map { it.copy(id = 0L, templateId = templateId) })
        return templateId
    }
}
