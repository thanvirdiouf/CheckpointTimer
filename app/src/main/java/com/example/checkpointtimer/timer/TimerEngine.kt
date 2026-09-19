package com.example.checkpointtimer.timer

import com.example.checkpointtimer.data.TimerCheckpoint

/** What happened between two consecutive [TimerEngine.update] calls. */
data class TickResult(
    /** Checkpoints crossed during this tick, in trigger order. */
    val fired: List<TimerCheckpoint> = emptyList(),
    val finished: Boolean = false,
)

/**
 * The timing core. It is plain arithmetic over elapsed-realtime milliseconds with no Android
 * dependencies, so unit tests can drive it with a fake clock.
 *
 * The caller owns the clock: every mutating call takes the current reading of
 * `SystemClock.elapsedRealtime()` and the engine works out what has happened since the
 * previous call. That is what makes the timer immune to wall-clock changes and to the app
 * being backgrounded — no tick is required for time to pass, ticks only report it.
 */
class TimerEngine(
    val totalMs: Long,
    checkpoints: List<TimerCheckpoint>,
) {

    enum class Status { IDLE, RUNNING, PAUSED, FINISHED }

    /** Disabled checkpoints never reach the engine; the rest fire in trigger order. */
    private val schedule: List<TimerCheckpoint> = checkpoints
        .filter { it.enabled }
        .sortedBy { it.triggerMs }

    private var now: Long = 0L
    private var anchor: Long = 0L
    private var accumulatedMs: Long = 0L
    private var firedCount: Int = 0

    var status: Status = Status.IDLE
        private set

    val elapsedMs: Long
        get() {
            val raw = if (status == Status.RUNNING) accumulatedMs + (now - anchor) else accumulatedMs
            return raw.coerceIn(0L, totalMs)
        }

    val remainingMs: Long get() = (totalMs - elapsedMs).coerceAtLeast(0L)

    val progress: Float
        get() = if (totalMs <= 0L) 1f else (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)

    val nextCheckpoint: TimerCheckpoint? get() = schedule.getOrNull(firedCount)

    val msUntilNextCheckpoint: Long?
        get() = nextCheckpoint?.let { (it.triggerMs - elapsedMs).coerceAtLeast(0L) }

    fun start(now: Long) {
        this.now = now
        anchor = now
        accumulatedMs = 0L
        firedCount = 0
        status = Status.RUNNING
    }

    fun pause(now: Long) {
        if (status != Status.RUNNING) return
        this.now = now
        accumulatedMs = elapsedMs
        status = Status.PAUSED
    }

    fun resume(now: Long) {
        if (status != Status.PAUSED) return
        this.now = now
        anchor = now
        status = Status.RUNNING
    }

    /**
     * Returns the timer to zero. A paused timer stays paused (at full duration); anything
     * else ends up running, so that a reset after the timer finished starts it again.
     */
    fun reset(now: Long) {
        this.now = now
        anchor = now
        accumulatedMs = 0L
        firedCount = 0
        if (status == Status.IDLE || status == Status.FINISHED) {
            status = Status.RUNNING
        }
    }

    /**
     * Advances the engine to [now]. Checkpoints crossed since the previous call are returned
     * in order, each exactly once, and [TickResult.finished] is set on the tick that reaches
     * the total duration — after any checkpoints that came before it.
     */
    fun update(now: Long): TickResult {
        this.now = now
        if (status != Status.RUNNING) return TickResult()

        val elapsed = elapsedMs
        val fired = buildList {
            while (firedCount < schedule.size && schedule[firedCount].triggerMs <= elapsed) {
                add(schedule[firedCount])
                firedCount++
            }
        }

        val finished = elapsed >= totalMs
        if (finished) {
            // Bank the full duration first: once the status leaves RUNNING, elapsed time is
            // read from accumulatedMs, and leaving it stale would report time still remaining.
            accumulatedMs = totalMs
            status = Status.FINISHED
        }
        return TickResult(fired = fired, finished = finished)
    }
}
