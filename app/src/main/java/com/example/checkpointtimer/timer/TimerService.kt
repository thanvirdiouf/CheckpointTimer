package com.example.checkpointtimer.timer

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.checkpointtimer.CheckpointTimerApp
import com.example.checkpointtimer.data.TemplateRepository
import com.example.checkpointtimer.data.TimerTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the timer as a foreground service so it survives the app being backgrounded or the
 * screen being turned off.
 *
 * The service owns the [TimerEngine] and publishes snapshots into [TimerStateHolder]; the UI
 * observes that flow. Nothing here touches wall-clock time — everything is measured against
 * `SystemClock.elapsedRealtime()`, so changing the system time cannot affect a running timer.
 */
class TimerService : LifecycleService() {

    private lateinit var repository: TemplateRepository
    private lateinit var soundPlayer: SoundPlayer
    private lateinit var notifications: TimerNotifications

    private var engine: TimerEngine? = null
    private var template: TimerTemplate? = null
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Runs after the end sound has had time to play, then shuts the service down. */
    private var shutdownJob: Job? = null
    private var alertNotificationCounter = 0

    /** Remaining time is only shown to the second, so notifications are throttled to match. */
    private var lastNotifiedSecond: Long = -1L

    override fun onCreate() {
        super.onCreate()
        repository = (application as CheckpointTimerApp).container.templateRepository
        soundPlayer = SoundPlayer(this, lifecycleScope)
        notifications = TimerNotifications(this).also { it.ensureChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startTimer(intent.getLongExtra(EXTRA_TEMPLATE_ID, 0L))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_RESET -> reset()
            ACTION_STOP -> cancelTimer()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        tickJob?.cancel()
        releaseWakeLock()
        soundPlayer.release()
        super.onDestroy()
    }

    private fun startTimer(templateId: Long) {
        // The database read is asynchronous, so go foreground immediately with a placeholder
        // to stay inside the five second window the system allows.
        goForeground(notifications.buildStartingNotification())

        // Replacing a running timer should not leave its wakelock, sound, or a pending
        // shutdown behind.
        shutdownJob?.cancel()
        tickJob?.cancel()
        soundPlayer.stop()

        lifecycleScope.launch {
            val loaded = repository.getTemplate(templateId)
            if (loaded == null) {
                cancelTimer()
                return@launch
            }
            template = loaded
            engine = TimerEngine(
                totalMs = loaded.totalDurationMs,
                checkpoints = loaded.checkpoints,
            ).also { it.start(SystemClock.elapsedRealtime()) }

            lastNotifiedSecond = -1L
            acquireWakeLock()
            publishState()
            updateNotification(force = true)
            startTicking()
        }
    }

    private fun pause() {
        val engine = engine ?: return
        engine.pause(SystemClock.elapsedRealtime())
        releaseWakeLock()
        publishState()
        updateNotification(force = true)
    }

    private fun resume() {
        val engine = engine ?: return
        engine.resume(SystemClock.elapsedRealtime())
        acquireWakeLock()
        publishState()
        updateNotification(force = true)
    }

    private fun reset() {
        val engine = engine ?: return
        // Restarting a finished timer must cancel the shutdown that the finish scheduled.
        shutdownJob?.cancel()
        shutdownJob = null
        engine.reset(SystemClock.elapsedRealtime())
        soundPlayer.stop()
        if (engine.status == TimerEngine.Status.RUNNING) acquireWakeLock() else releaseWakeLock()
        publishState()
        updateNotification(force = true)
        if (tickJob?.isActive != true) startTicking()
    }

    private fun cancelTimer() {
        tickJob?.cancel()
        tickJob = null
        shutdownJob?.cancel()
        shutdownJob = null
        soundPlayer.stop()
        soundPlayer.cancelVibration()
        releaseWakeLock()
        notifications.cancelAlerts()
        engine = null
        template = null
        lastNotifiedSecond = -1L
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        TimerStateHolder.clear()
        stopSelf()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = lifecycleScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val engine = engine ?: return
        val template = template ?: return
        val result = engine.update(SystemClock.elapsedRealtime())

        result.fired.forEach { checkpoint ->
            notifications.showCheckpointAlert(
                templateName = template.name,
                label = checkpoint.label,
                // Rotating ids keep consecutive checkpoint alerts from replacing each other.
                id = NOTIFICATION_ID_CHECKPOINT_BASE + alertNotificationCounter++ % 50,
            )
        }
        if (result.fired.isNotEmpty()) {
            TimerStateHolder.publishAlert(result.fired.last().label, isFinal = false)
        }

        if (result.finished) {
            onFinished(template)
            return
        }

        // A checkpoint that lands on the same tick as the end of the timer is reported in the
        // UI but stays silent, so its sound cannot collide with the end sound.
        result.fired.lastOrNull()?.let { checkpoint ->
            soundPlayer.play(checkpoint.sound)
            if (checkpoint.vibrate) soundPlayer.vibrate(SoundPlayer.CHECKPOINT_VIBRATION)
        }

        publishState()
        updateNotification()
    }

    private fun onFinished(template: TimerTemplate) {
        tickJob?.cancel()
        tickJob = null
        releaseWakeLock()

        notifications.showFinishedAlert(template.name)
        TimerStateHolder.publishAlert("Time's up", isFinal = true)
        soundPlayer.play(template.endSound)
        if (template.endVibrate) soundPlayer.vibrate(SoundPlayer.END_VIBRATION)

        publishState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        // Stay alive briefly so the end sound is not cut off by the process going away. The
        // job is tracked so that restarting the timer cancels this shutdown.
        shutdownJob = lifecycleScope.launch {
            delay(SOUND_TAIL_MS)
            soundPlayer.stop()
            stopSelf()
        }
    }

    private fun publishState() {
        val engine = engine ?: return
        val template = template ?: return
        TimerStateHolder.publish(
            TimerState(
                templateId = template.id,
                templateName = template.name,
                totalMs = engine.totalMs,
                elapsedMs = engine.elapsedMs,
                remainingMs = engine.remainingMs,
                progress = engine.progress,
                status = engine.status,
                nextCheckpointLabel = engine.nextCheckpoint?.label,
                msUntilNextCheckpoint = engine.msUntilNextCheckpoint,
            ),
        )
    }

    private fun updateNotification(force: Boolean = false) {
        val engine = engine ?: return
        if (engine.status != TimerEngine.Status.RUNNING && engine.status != TimerEngine.Status.PAUSED) {
            return
        }
        val second = engine.remainingMs / 1000L
        if (!force && second == lastNotifiedSecond) return
        lastNotifiedSecond = second
        goForeground(notifications.buildRunningNotification(TimerStateHolder.state.value))
    }

    private fun goForeground(notification: android.app.Notification) {
        // A foreground service type may only be passed from API 34, where specialUse exists.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, TimerNotifications.NOTIFICATION_ID_RUNNING, notification, type)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = wakeLock ?: powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .also { wakeLock = it }
        // The timeout is a leak guard, not a limit on timer length.
        runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    companion object {
        private const val ACTION_START = "com.example.checkpointtimer.action.START"
        private const val ACTION_PAUSE = "com.example.checkpointtimer.action.PAUSE"
        private const val ACTION_RESUME = "com.example.checkpointtimer.action.RESUME"
        private const val ACTION_RESET = "com.example.checkpointtimer.action.RESET"
        private const val ACTION_STOP = "com.example.checkpointtimer.action.STOP"

        private const val EXTRA_TEMPLATE_ID = "extra_template_id"

        private const val NOTIFICATION_ID_CHECKPOINT_BASE = 100

        /** Fast enough that the progress ring looks smooth, cheap enough to run for hours. */
        private const val TICK_MS = 100L
        private const val SOUND_TAIL_MS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L
        private const val WAKE_LOCK_TAG = "CheckpointTimer::Timer"

        /** Only called while the app is in the foreground, so a peer start is allowed. */
        fun start(context: Context, templateId: Long) {
            ContextCompat.startForegroundService(context, actionIntent(context, ACTION_START).putExtra(EXTRA_TEMPLATE_ID, templateId))
        }

        fun pause(context: Context) = context.startService(actionIntent(context, ACTION_PAUSE))

        fun resume(context: Context) = context.startService(actionIntent(context, ACTION_RESUME))

        fun reset(context: Context) = context.startService(actionIntent(context, ACTION_RESET))

        fun stop(context: Context) = context.startService(actionIntent(context, ACTION_STOP))

        /** Intents handed to notification actions, which the system delivers for us. */
        fun pauseIntent(context: Context): Intent = actionIntent(context, ACTION_PAUSE)

        fun resumeIntent(context: Context): Intent = actionIntent(context, ACTION_RESUME)

        fun stopIntent(context: Context): Intent = actionIntent(context, ACTION_STOP)

        private fun actionIntent(context: Context, action: String): Intent =
            Intent(context, TimerService::class.java).setAction(action)
    }
}
