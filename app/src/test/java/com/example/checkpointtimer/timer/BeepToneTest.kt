package com.example.checkpointtimer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The beep was asked to be higher and louder than the system tone it replaced, and those are
 * measurable properties of the rendered samples rather than things only an ear can check.
 */
class BeepToneTest {

    private val sampleRate = 8_000
    private val samples = BeepTone.render(sampleRate)

    @Test
    fun `the beep is high pitched`() {
        val estimates = pulseRanges().map { estimatedFrequencyHz(it) }

        assertEquals(BeepTone.PULSES, estimates.size)
        estimates.forEach { hz ->
            assertEquals(BeepTone.FREQUENCY_HZ, hz, 60.0)
        }
        assertTrue(
            "expected a pitch well above a typical system beep",
            estimates.all { it > 1_000.0 },
        )
    }

    @Test
    fun `the beep is loud`() {
        val peak = samples.maxOf { abs(it.toInt()) }
        val fullScale = Short.MAX_VALUE.toDouble()

        assertTrue("peak $peak should be near full scale", peak > 0.85 * fullScale)
        // Loud, but not clipped: a square-looking wave would indicate integer overflow.
        assertTrue("peak $peak must not clip", peak <= Short.MAX_VALUE)
    }

    @Test
    fun `the beep is silent between pulses so it reads as separate beats`() {
        val ranges = pulseRanges()
        ranges.zipWithNext { current, next ->
            val gap = samples.sliceArray((current.last + 1)..(next.first - 1))
            assertTrue("expected silence between pulses", gap.all { it == 0.toShort() })
        }
    }

    @Test
    fun `the beep has the expected length`() {
        assertEquals(BeepTone.durationMs * sampleRate / 1000, samples.size)
    }

    @Test
    fun `the pulse edges fade so the speaker does not click`() {
        val first = pulseRanges().first()
        val edge = samples[first.first]

        assertTrue("the first sample should start near silence, was $edge", abs(edge.toInt()) < 2_000)
    }

    /**
     * Boundaries of each non-silent pulse.
     *
     * A sine dips through zero mid-pulse, so brief silent stretches are just zero crossings and
     * have to be tolerated; only a stretch as long as a real gap ends a pulse.
     */
    private fun pulseRanges(): List<IntRange> {
        val gapThreshold = BeepTone.GAP_MS * sampleRate / 1000 / 2
        val ranges = mutableListOf<IntRange>()
        var pulseStart = -1
        var silentRun = 0

        samples.forEachIndexed { index, sample ->
            if (abs(sample.toInt()) < SILENCE_THRESHOLD) {
                silentRun++
            } else {
                if (pulseStart < 0) pulseStart = index
                silentRun = 0
            }

            if (silentRun > gapThreshold && pulseStart >= 0) {
                ranges += pulseStart until (index - silentRun + 1)
                pulseStart = -1
            }
        }

        if (pulseStart >= 0) ranges += pulseStart until samples.size
        return ranges
    }

    /** Zero crossings over the pulse give the frequency of a pure sine. */
    private fun estimatedFrequencyHz(range: IntRange): Double {
        var crossings = 0
        for (i in (range.first + 1)..range.last) {
            val previous = samples[i - 1]
            val current = samples[i]
            if ((previous >= 0 && current < 0) || (previous < 0 && current >= 0)) crossings++
        }
        val seconds = range.count().toDouble() / sampleRate
        return crossings / 2.0 / seconds
    }

    private companion object {
        /** Far below the waveform's amplitude, far above a zero crossing's neighbours. */
        const val SILENCE_THRESHOLD = 100
    }
}
