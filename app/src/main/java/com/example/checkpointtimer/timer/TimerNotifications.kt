package com.example.checkpointtimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.checkpointtimer.R
import com.example.checkpointtimer.ui.MainActivity

/**
 * Builds the foreground notification and the checkpoint/finish alerts.
 *
 * Both channels are deliberately silent and vibration-free: the app plays its own sounds
 * through [SoundPlayer], so channel-level alerts would double up on every checkpoint.
 */
class TimerNotifications(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Running timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing timer with remaining time and controls"
            setShowBadge(false)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            "Checkpoint alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fires when a checkpoint or the end of the timer is reached"
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(listOf(status, alerts))
    }

    fun buildRunningNotification(state: TimerState): Notification {
        val paused = state.status == TimerEngine.Status.PAUSED
        val text = if (paused) {
            "Paused at ${formatDuration(state.remainingMs)} remaining"
        } else {
            "${formatDuration(state.remainingMs)} remaining"
        }

        val toggleAction = if (paused) {
            NotificationCompat.Action(
                R.drawable.ic_notification_timer,
                "Resume",
                serviceIntent(REQUEST_RESUME, TimerService.resumeIntent(context)),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_notification_timer,
                "Pause",
                serviceIntent(REQUEST_PAUSE, TimerService.pauseIntent(context)),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(state.templateName)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(100, (state.progress * 100).toInt(), false)
            .addAction(toggleAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_timer,
                    "Cancel",
                    serviceIntent(REQUEST_STOP, TimerService.stopIntent(context)),
                ),
            )
            .build()
    }

    /** Used for the brief moment before the template has been read out of the database. */
    fun buildStartingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("Starting timer…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    fun showCheckpointAlert(templateName: String, label: String, id: Int) {
        post(id, templateName, label)
    }

    fun showFinishedAlert(templateName: String) {
        post(NOTIFICATION_ID_FINISHED, templateName, "Time's up")
    }

    fun cancelAlerts() {
        val manager = NotificationManagerCompat.from(context)
        runCatching { manager.cancel(NOTIFICATION_ID_FINISHED) }
    }

    private fun post(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        // POST_NOTIFICATIONS may have been denied; a missing alert must not kill the timer.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_STATUS = "timer_status"
        const val CHANNEL_ALERTS = "timer_alerts"

        const val NOTIFICATION_ID_RUNNING = 1
        const val NOTIFICATION_ID_FINISHED = 2

        private const val REQUEST_CONTENT = 100
        private const val REQUEST_PAUSE = 101
        private const val REQUEST_RESUME = 102
        private const val REQUEST_STOP = 103
    }
}
