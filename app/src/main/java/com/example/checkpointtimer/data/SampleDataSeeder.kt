package com.example.checkpointtimer.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Installs the bundled example timers the first time the app runs.
 *
 * The flag is written even when the database already had rows, so deleting every template
 * does not bring the samples back on the next launch.
 */
class SampleDataSeeder(
    private val repository: TemplateRepository,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
) {

    fun seedIfNeeded() {
        if (preferences.getBoolean(KEY_SAMPLES_SEEDED, false)) return
        scope.launch {
            if (repository.templateCount() == 0) {
                SAMPLE_TEMPLATES.forEach { repository.saveTemplate(it) }
            }
            preferences.edit().putBoolean(KEY_SAMPLES_SEEDED, true).apply()
        }
    }

    private companion object {
        const val KEY_SAMPLES_SEEDED = "sample_templates_seeded"
        const val MINUTE = 60_000L

        val SAMPLE_TEMPLATES = listOf(
            TimerTemplate(
                name = "10 min with 5 and 8 min checkpoints",
                totalDurationMs = 10 * MINUTE,
                endSound = SoundChoice.Alarm,
                endVibrate = true,
                checkpoints = listOf(
                    TimerCheckpoint(
                        label = "5 min checkpoint",
                        triggerMs = 5 * MINUTE,
                        sound = SoundChoice.Beep,
                        vibrate = true,
                    ),
                    TimerCheckpoint(
                        label = "8 min checkpoint",
                        triggerMs = 8 * MINUTE,
                        sound = SoundChoice.Beep,
                        vibrate = true,
                    ),
                ),
            ),
            TimerTemplate(
                name = "Pomodoro 25 min with checkpoint at 20 min",
                totalDurationMs = 25 * MINUTE,
                endSound = SoundChoice.Alarm,
                endVibrate = true,
                checkpoints = listOf(
                    TimerCheckpoint(
                        label = "20 min checkpoint",
                        triggerMs = 20 * MINUTE,
                        sound = SoundChoice.Notification,
                        vibrate = false,
                    ),
                ),
            ),
            TimerTemplate(
                name = "5 min simple timer",
                totalDurationMs = 5 * MINUTE,
                endSound = SoundChoice.Alarm,
                endVibrate = true,
                checkpoints = emptyList(),
            ),
        )
    }
}
