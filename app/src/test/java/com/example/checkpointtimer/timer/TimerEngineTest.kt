package com.example.checkpointtimer.timer

import com.example.checkpointtimer.data.SoundChoice
import com.example.checkpointtimer.data.TimerCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine takes its clock as a parameter, so these tests drive time by hand instead of
 * sleeping. Every timestamp below is an `elapsedRealtime` reading, which is what makes the
 * timer immune to wall-clock changes.
 */
class TimerEngineTest {

    private val fiveMinute = checkpoint(id = 1L, label = "5 min", triggerMs = 5_000L)
    private val tenMinute = checkpoint(id = 2L, label = "10 min", triggerMs = 10_000L)

    @Test
    fun `checkpoints fire once and in order`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)

        assertTrue(engine.update(4_999L).fired.isEmpty())

        assertEquals(listOf("5 min"), engine.update(5_000L).fired.map { it.label })
        // Re-reporting the same instant must not fire the checkpoint a second time.
        assertTrue(engine.update(5_000L).fired.isEmpty())
        assertTrue(engine.update(5_500L).fired.isEmpty())

        assertEquals(listOf("10 min"), engine.update(10_000L).fired.map { it.label })
        assertTrue(engine.update(20_000L).fired.isEmpty())
    }

    @Test
    fun `end fires after all earlier checkpoints`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)

        val result = engine.update(60_000L)

        assertEquals(listOf("5 min", "10 min"), result.fired.map { it.label })
        assertTrue(result.finished)
        assertEquals(TimerEngine.Status.FINISHED, engine.status)
    }

    @Test
    fun `long tick in the background still fires every checkpoint in order`() {
        val engine = engine(totalMs = 3_600_000L)
        engine.start(now = 0L)

        // The app was backgrounded for half an hour; one tick now reports everything crossed.
        val result = engine.update(1_800_000L)

        assertEquals(listOf("5 min", "10 min"), result.fired.map { it.label })
        assertFalse(result.finished)
    }

    @Test
    fun `pause and resume preserve the correct remaining time`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)

        engine.update(20_000L)
        assertEquals(40_000L, engine.remainingMs)

        engine.pause(now = 20_000L)
        // Two minutes of wall-clock pass while paused; none of it should count.
        engine.update(140_000L)
        assertEquals(40_000L, engine.remainingMs)
        assertEquals(TimerEngine.Status.PAUSED, engine.status)

        engine.resume(now = 140_000L)
        engine.update(160_000L)
        assertEquals(20_000L, engine.remainingMs)

        engine.update(200_000L)
        assertEquals(0L, engine.remainingMs)
        assertEquals(TimerEngine.Status.FINISHED, engine.status)
    }

    @Test
    fun `checkpoints do not fire while paused`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.update(4_000L)

        engine.pause(now = 4_000L)
        assertTrue(engine.update(30_000L).fired.isEmpty())
        assertFalse(engine.update(30_000L).finished)

        engine.resume(now = 30_000L)
        // 4s of running time were banked, so the 5s checkpoint is one second of running away.
        assertTrue(engine.update(30_999L).fired.isEmpty())
        assertEquals(listOf("5 min"), engine.update(31_000L).fired.map { it.label })
    }

    @Test
    fun `end does not fire while paused even after the total duration has passed`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.pause(now = 1_000L)

        assertFalse(engine.update(10_000_000L).finished)
        assertEquals(TimerEngine.Status.PAUSED, engine.status)
    }

    @Test
    fun `disabled checkpoints never fire`() {
        val engine = TimerEngine(
            totalMs = 60_000L,
            checkpoints = listOf(fiveMinute, tenMinute.copy(enabled = false)),
        )
        engine.start(now = 0L)

        val result = engine.update(60_000L)

        assertEquals(listOf("5 min"), result.fired.map { it.label })
        assertTrue(result.finished)
    }

    @Test
    fun `checkpoints sharing a trigger time all fire in one tick`() {
        val engine = TimerEngine(
            totalMs = 60_000L,
            checkpoints = listOf(tenMinute.copy(triggerMs = 5_000L), fiveMinute),
        )
        engine.start(now = 0L)

        val result = engine.update(5_000L)

        assertEquals(2, result.fired.size)
    }

    @Test
    fun `checkpoint landing on the end tick is reported alongside the end`() {
        val engine = TimerEngine(
            totalMs = 60_000L,
            checkpoints = listOf(checkpoint(id = 3L, label = "almost", triggerMs = 59_900L)),
        )
        engine.start(now = 0L)

        val result = engine.update(60_000L)

        assertEquals(listOf("almost"), result.fired.map { it.label })
        assertTrue(result.finished)
    }

    @Test
    fun `the end fires exactly once`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)

        assertTrue(engine.update(60_000L).finished)
        assertFalse(engine.update(60_500L).finished)
        assertFalse(engine.update(90_000L).finished)
    }

    @Test
    fun `remaining time never goes negative after the end`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.update(600_000L)

        assertEquals(0L, engine.remainingMs)
        assertEquals(60_000L, engine.elapsedMs)
        assertEquals(1f, engine.progress, 0.0001f)
    }

    @Test
    fun `reset returns to the beginning and keeps running`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.update(30_000L)

        engine.reset(now = 30_000L)

        assertEquals(TimerEngine.Status.RUNNING, engine.status)
        assertEquals(0L, engine.elapsedMs)
        assertEquals(60_000L, engine.remainingMs)
        // Checkpoints become available again after a reset.
        assertEquals(listOf("5 min"), engine.update(35_000L).fired.map { it.label })
    }

    @Test
    fun `reset while paused stays paused at full duration`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.update(10_000L)
        engine.pause(now = 10_000L)

        engine.reset(now = 10_000L)

        assertEquals(TimerEngine.Status.PAUSED, engine.status)
        assertEquals(0L, engine.elapsedMs)
        assertTrue(engine.update(90_000L).fired.isEmpty())
    }

    @Test
    fun `next checkpoint reports label and time remaining until it`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)
        engine.update(1_000L)

        assertEquals("5 min", engine.nextCheckpoint?.label)
        assertEquals(4_000L, engine.msUntilNextCheckpoint)

        engine.update(6_000L)
        assertEquals("10 min", engine.nextCheckpoint?.label)
        assertEquals(4_000L, engine.msUntilNextCheckpoint)

        engine.update(11_000L)
        assertEquals(null, engine.nextCheckpoint)
        assertEquals(null, engine.msUntilNextCheckpoint)
    }

    @Test
    fun `progress tracks elapsed fraction of the total`() {
        val engine = engine(totalMs = 60_000L)
        engine.start(now = 0L)

        assertEquals(0f, engine.progress, 0.0001f)
        engine.update(15_000L)
        assertEquals(0.25f, engine.progress, 0.0001f)
        engine.update(45_000L)
        assertEquals(0.75f, engine.progress, 0.0001f)
    }

    @Test
    fun `an update before start reports nothing`() {
        val engine = engine(totalMs = 60_000L)

        val result = engine.update(1_000L)

        assertTrue(result.fired.isEmpty())
        assertFalse(result.finished)
        assertEquals(TimerEngine.Status.IDLE, engine.status)
    }

    private fun engine(totalMs: Long) = TimerEngine(totalMs, listOf(fiveMinute, tenMinute))

    private fun checkpoint(id: Long, label: String, triggerMs: Long) = TimerCheckpoint(
        id = id,
        label = label,
        triggerMs = triggerMs,
        sound = SoundChoice.Beep,
        vibrate = true,
    )
}
