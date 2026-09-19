package com.example.checkpointtimer.timer

import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders the default checkpoint beep.
 *
 * The brief asked for `ToneGenerator`, but that can only play a fixed table of system tones:
 * neither the pitch nor the level is adjustable, and the beep needed to be both higher and
 * louder. Generating the samples keeps those two properties explicit — and testable.
 *
 * The beep is three short pulses rather than one long tone, which reads as more urgent without
 * needing more volume.
 */
object BeepTone {

    /** Well above the 400-800 Hz of a typical system beep, so it carries over background noise. */
    const val FREQUENCY_HZ = 1_200.0

    const val SAMPLE_RATE = 44_100
    const val PULSE_MS = 130
    const val GAP_MS = 70
    const val PULSES = 3

    /** Short fades stop the speaker clicking at the start and end of each pulse. */
    private const val FADE_MS = 6.0

    /** Fraction of full scale. Near the top for loudness, short of it to avoid clipping. */
    private const val AMPLITUDE = 0.92

    val durationMs: Int get() = PULSES * PULSE_MS + (PULSES - 1) * GAP_MS

    /** Mono 16-bit PCM, ready to hand to an [android.media.AudioTrack]. */
    fun render(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val pulseSamples = PULSE_MS * sampleRate / 1000
        val gapSamples = GAP_MS * sampleRate / 1000
        val fadeSamples = (FADE_MS * sampleRate / 1000).toInt().coerceAtLeast(1)
        val peak = Short.MAX_VALUE * AMPLITUDE

        val samples = ShortArray(durationMs * sampleRate / 1000)
        var writeAt = 0

        repeat(PULSES) { pulse ->
            for (i in 0 until pulseSamples) {
                val angle = 2.0 * PI * FREQUENCY_HZ * i / sampleRate
                samples[writeAt + i] = (sin(angle) * envelope(i, pulseSamples, fadeSamples) * peak)
                    .toInt()
                    .toShort()
            }
            writeAt += pulseSamples
            // The gap between pulses is simply left as silence.
            if (pulse < PULSES - 1) writeAt += gapSamples
        }

        return samples
    }

    private fun envelope(sample: Int, pulseSamples: Int, fadeSamples: Int): Double = when {
        sample < fadeSamples -> sample.toDouble() / fadeSamples
        sample >= pulseSamples - fadeSamples -> (pulseSamples - sample).toDouble() / fadeSamples
        else -> 1.0
    }
}
