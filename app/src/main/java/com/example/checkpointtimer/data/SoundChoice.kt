package com.example.checkpointtimer.data

/**
 * A sound the user can attach to a checkpoint or to the end of a timer.
 *
 * Persisted as a single string so that a user-picked ringtone URI survives in Room
 * without needing a second column or a separate table.
 */
sealed interface SoundChoice {

    /** Synthesised with [android.media.ToneGenerator]. The default checkpoint sound. */
    data object Beep : SoundChoice

    /** The device's default alarm ringtone. */
    data object Alarm : SoundChoice

    /** The device's default notification ringtone. */
    data object Notification : SoundChoice

    data object Silent : SoundChoice

    /** A specific ringtone the user picked through the system picker. */
    data class Ringtone(val uri: String) : SoundChoice

    fun toStorageString(): String = when (this) {
        Beep -> BEEP
        Alarm -> ALARM
        Notification -> NOTIFICATION
        Silent -> SILENT
        is Ringtone -> URI_PREFIX + uri
    }

    companion object {
        const val BEEP = "beep"
        const val ALARM = "alarm"
        const val NOTIFICATION = "notification"
        const val SILENT = "silent"
        private const val URI_PREFIX = "uri:"

        val DEFAULT: SoundChoice = Beep

        /** Unknown or missing values fall back to [DEFAULT] rather than crashing. */
        fun fromStorageString(raw: String?): SoundChoice = when {
            raw == null -> DEFAULT
            raw == BEEP -> Beep
            raw == ALARM -> Alarm
            raw == NOTIFICATION -> Notification
            raw == SILENT -> Silent
            raw.startsWith(URI_PREFIX) -> Ringtone(raw.removePrefix(URI_PREFIX))
            else -> DEFAULT
        }
    }
}
