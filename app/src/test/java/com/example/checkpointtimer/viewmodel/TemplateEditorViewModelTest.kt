package com.example.checkpointtimer.viewmodel

import com.example.checkpointtimer.data.SoundChoice
import com.example.checkpointtimer.data.TemplateRepository
import com.example.checkpointtimer.testing.FakeTemplateDao
import com.example.checkpointtimer.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Covers the save path end to end: validation, persistence, and the completion signal. */
class TemplateEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao = FakeTemplateDao()
    private val repository = TemplateRepository(dao)

    private fun newTemplateEditor() = TemplateEditorViewModel(repository, templateId = 0L)

    @Test
    fun `saving a valid template persists it and reports success`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Tea timer")
        viewModel.setTotalDuration(hours = 0, minutes = 10, seconds = 0)
        viewModel.addCheckpoint()
        viewModel.save()

        val state = viewModel.uiState.value
        assertFalse(state.errors.hasErrors)
        assertTrue("save should signal completion so the screen can close", state.isSaved)

        val stored = dao.templates.single()
        assertEquals("Tea timer", stored.name)
        assertEquals(600_000L, stored.totalDurationMs)
        assertEquals(SoundChoice.Alarm.toStorageString(), stored.endSound)

        val checkpoint = dao.checkpoints.single()
        assertEquals(60_000L, checkpoint.triggerMs)
        assertEquals("1:00 checkpoint", checkpoint.label)
        assertEquals(0, checkpoint.orderIndex)
    }

    @Test
    fun `saving without a name reports the error and persists nothing`() {
        val viewModel = newTemplateEditor()

        viewModel.setTotalDuration(hours = 0, minutes = 10, seconds = 0)
        viewModel.save()

        val state = viewModel.uiState.value
        assertNotNull(state.errors.name)
        assertFalse(state.isSaved)
        assertTrue(dao.templates.isEmpty())
    }

    @Test
    fun `a rejected save names the field that needs attention`() {
        val viewModel = newTemplateEditor()

        viewModel.save()

        // The name field's error may be scrolled out of sight, so the screen needs to be told
        // which row to jump to and what to say.
        val rejection = viewModel.uiState.value.saveRejection
        assertNotNull(rejection)
        assertEquals(EditorField.Name, rejection!!.field)
        assertNotNull(rejection.message)
    }

    @Test
    fun `a rejected checkpoint save points at the checkpoint that failed`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Overrun")
        viewModel.setTotalDuration(hours = 0, minutes = 5, seconds = 0)
        viewModel.addCheckpoint()
        viewModel.updateCheckpoint(viewModel.uiState.value.checkpoints.single().key) {
            it.copy(minutes = 9)
        }
        viewModel.save()

        val rejection = viewModel.uiState.value.saveRejection
        assertEquals(EditorField.Checkpoint, rejection?.field)
        assertEquals(0, rejection?.checkpointIndex)
    }

    @Test
    fun `every rejected save is a distinct signal so the message reappears`() {
        val viewModel = newTemplateEditor()

        viewModel.save()
        val first = viewModel.uiState.value.saveRejection?.id
        viewModel.save()
        val second = viewModel.uiState.value.saveRejection?.id

        assertNotNull(first)
        assertNotNull(second)
        assertTrue("a repeat save must produce a new id", first != second)
    }

    @Test
    fun `saving a zero length timer reports the error and persists nothing`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Nothing")
        viewModel.setTotalDuration(hours = 0, minutes = 0, seconds = 0)
        viewModel.save()

        assertNotNull(viewModel.uiState.value.errors.totalDuration)
        assertFalse(viewModel.uiState.value.isSaved)
        assertTrue(dao.templates.isEmpty())
    }

    @Test
    fun `a checkpoint at or beyond the total duration blocks the save`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Too long")
        viewModel.setTotalDuration(hours = 0, minutes = 5, seconds = 0)
        viewModel.addCheckpoint()
        viewModel.updateCheckpoint(viewModel.uiState.value.checkpoints.single().key) {
            it.copy(minutes = 5, seconds = 0)
        }
        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(1, state.errors.checkpoints.size)
        assertFalse(state.isSaved)
        assertTrue(dao.templates.isEmpty())
    }

    @Test
    fun `duplicate checkpoint times are rejected`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Clashing")
        viewModel.setTotalDuration(hours = 0, minutes = 10, seconds = 0)
        viewModel.addCheckpoint()
        viewModel.addCheckpoint()
        // Both checkpoints default to a minute apart; pull the second one back onto the first.
        val second = viewModel.uiState.value.checkpoints[1]
        viewModel.updateCheckpoint(second.key) { it.copy(minutes = 1, seconds = 0) }
        viewModel.save()

        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(1, viewModel.uiState.value.errors.checkpoints.size)
    }

    @Test
    fun `errors clear as soon as the user fixes them`() {
        val viewModel = newTemplateEditor()

        viewModel.save()
        assertNotNull(viewModel.uiState.value.errors.name)

        viewModel.setName("Fixed")

        assertNull(viewModel.uiState.value.errors.name)
    }

    @Test
    fun `a disabled checkpoint does not block the save`() {
        val viewModel = newTemplateEditor()

        viewModel.setName("Disabled checkpoint")
        viewModel.setTotalDuration(hours = 0, minutes = 5, seconds = 0)
        viewModel.addCheckpoint()
        viewModel.updateCheckpoint(viewModel.uiState.value.checkpoints.single().key) {
            it.copy(enabled = false, minutes = 30, seconds = 0)
        }
        viewModel.save()

        val state = viewModel.uiState.value
        assertFalse(state.errors.hasErrors)
        assertTrue(state.isSaved)
        assertEquals(1, dao.templates.size)
    }

    @Test
    fun `editing an existing template updates it rather than creating a second one`() {
        val created = newTemplateEditor().apply {
            setName("Original")
            setTotalDuration(0, 10, 0)
            save()
        }
        assertTrue(created.uiState.value.isSaved)
        val id = dao.templates.single().id

        val editor = TemplateEditorViewModel(repository, templateId = id)
        assertEquals("Original", editor.uiState.value.name)

        editor.setName("Renamed")
        editor.save()

        assertEquals(1, dao.templates.size)
        assertEquals("Renamed", dao.templates.single().name)
        assertEquals(id, dao.templates.single().id)
    }

    @Test
    fun `loading a template populates the duration fields`() {
        val created = newTemplateEditor().apply {
            setName("Ninety")
            setTotalDuration(hours = 1, minutes = 30, seconds = 5)
            addCheckpoint()
            save()
        }
        assertTrue(created.uiState.value.isSaved)

        val editor = TemplateEditorViewModel(repository, templateId = dao.templates.single().id)

        val state = editor.uiState.value
        assertEquals(1, state.hours)
        assertEquals(30, state.minutes)
        assertEquals(5, state.seconds)
        assertFalse(state.isLoading)
        assertEquals(1, state.checkpoints.size)
        // 1h30m05s stored as milliseconds reads back as the same three fields.
        assertEquals(5_405_000L, state.totalDurationMs)
    }

    @Test
    fun `reordering moves a checkpoint within the list`() {
        val viewModel = newTemplateEditor()
        viewModel.setName("Ordered")
        viewModel.addCheckpoint()
        viewModel.addCheckpoint()

        val keys = viewModel.uiState.value.checkpoints.map { it.key }
        viewModel.moveCheckpoint(keys[1], delta = -1)

        assertEquals(keys.reversed(), viewModel.uiState.value.checkpoints.map { it.key })
    }
}
