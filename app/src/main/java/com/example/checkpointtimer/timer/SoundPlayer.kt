package com.example.checkpointtimer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.checkpointtimer.data.SoundChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the audible and haptic alerts. Everything here is best-effort: a device without a
 * vibrator, a ringtone that has been uninstalled, or a busy audio stream must never take the
 * timer down with it.
 */
class SoundPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var beepTrack: AudioTrack? = null
    private var beepStopJob: Job? = null
    private var beepSamples: ShortArray? = null

    private var ringtone: Ringtone? = null
    private var ringtoneStopJob: Job? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Stops whatever is playing, then plays [choice]. */
    fun play(choice: SoundChoice) {
        stop()
        when (choice) {
            SoundChoice.Beep -> beep()
            SoundChoice.Alarm -> playRingtone(defaultRingtoneUri(RingtoneManager.TYPE_ALARM))
            SoundChoice.Notification ->
                playRingtone(defaultRingtoneUri(RingtoneManager.TYPE_NOTIFICATION))
            SoundChoice.Silent -> Unit
            is SoundChoice.Ringtone -> playRingtone(choice.uri.toUriOrNull())
        }
    }

    fun vibrate(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching { device.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    fun stop() {
        stopBeep()
        ringtoneStopJob?.cancel()
        ringtoneStopJob = null
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    /** Cuts short a waveform that is still running, e.g. when the user cancels the timer. */
    fun cancelVibration() {
        runCatching { vibrator?.cancel() }
    }

    fun release() {
        stop()
    }

    private fun beep() {
        // Rendering is cheap, but the timer may beep many times over its life.
        val samples = beepSamples ?: BeepTone.render().also { beepSamples = it }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(BeepTone.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        beepTrack = track
        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
        }

        // MODE_STATIC holds the whole clip, so the track is released once it has finished.
        beepStopJob = scope.launch {
            delay(BeepTone.durationMs + RELEASE_GRACE_MS)
            stopBeep()
        }
    }

    private fun stopBeep() {
        beepStopJob?.cancel()
        beepStopJob = null
        val track = beepTrack ?: return
        beepTrack = null
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private fun defaultRingtoneUri(type: Int): Uri? =
        runCatching { RingtoneManager.getDefaultUri(type) }.getOrNull()

    private fun playRingtone(uri: Uri?) {
        if (uri == null) return
        val played = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: return
        played.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone = played
        runCatching { played.play() }

        // A ringtone loops until it is stopped, so an alert would otherwise never end.
        ringtoneStopJob = scope.launch {
            delay(RINGTONE_MAX_MS)
            runCatching { played.stop() }
        }
    }

    private fun String.toUriOrNull(): Uri? =
        runCatching { Uri.parse(this) }.getOrNull()?.takeIf { it.scheme != null }

    companion object {
        /** Matches the service's post-finish tail, so a ringing alert is never cut short. */
        private const val RINGTONE_MAX_MS = 5_000L
        private const val RELEASE_GRACE_MS = 100L

        val CHECKPOINT_VIBRATION = longArrayOf(0L, 180L, 120L, 180L)
        val END_VIBRATION = longArrayOf(0L, 400L, 200L, 400L, 200L, 700L)
    }
}
