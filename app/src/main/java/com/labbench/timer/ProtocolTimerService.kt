package com.labbench.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.labbench.MainActivity
import com.labbench.R
import com.labbench.data.LabDatabase
import com.labbench.data.ProtocolStep
import com.labbench.data.TimerRun
import com.labbench.data.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs one protocol at a time as a foreground service.
 *
 * The countdown is derived from a wall-clock end timestamp held in the database,
 * never from an in-memory tick counter. If Android kills the process mid-step,
 * the remaining time is still correct when it comes back — which is the entire
 * reason a bench timer can be trusted with a 45-second heat shock.
 */
class ProtocolTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private lateinit var db: LabDatabase

    override fun onCreate() {
        super.onCreate()
        db = LabDatabase.get(this)
        createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val protocolId = intent.getStringExtra(EXTRA_PROTOCOL_ID)
                val cultureId = intent.getStringExtra(EXTRA_CULTURE_ID)
                val adHocSeconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Timer"
                scope.launch {
                    if (protocolId != null) startProtocol(protocolId, cultureId) else startAdHoc(label, adHocSeconds)
                }
            }
            ACTION_NEXT -> scope.launch { advance(manual = true) }
            ACTION_PAUSE -> scope.launch { togglePause() }
            ACTION_STOP -> scope.launch { stopRun() }
        }
        startForegroundSafely(placeholderNotification())
        startTicking()
        return START_STICKY
    }

    private suspend fun startProtocol(protocolId: String, cultureId: String?) {
        val protocol = db.protocols().protocol(protocolId) ?: return
        val steps = db.protocols().stepsOnce(protocolId)
        if (steps.isEmpty()) return
        db.timers().clearActive()
        db.timers().upsert(runFor(protocol.name, protocolId, steps.first(), 0, cultureId))
    }

    private suspend fun startAdHoc(label: String, seconds: Int) {
        if (seconds <= 0) return
        db.timers().clearActive()
        db.timers().upsert(
            TimerRun(
                id = newId(),
                protocolId = null,
                protocolName = label,
                stepIndex = 0,
                stepTitle = label,
                stepDurationSeconds = seconds,
                stepEndsAt = System.currentTimeMillis() + seconds * 1000L
            )
        )
    }

    private fun runFor(
        protocolName: String,
        protocolId: String,
        step: ProtocolStep,
        index: Int,
        cultureId: String?
    ) = TimerRun(
        id = newId(),
        protocolId = protocolId,
        protocolName = protocolName,
        stepIndex = index,
        stepTitle = step.title,
        stepDurationSeconds = step.durationSeconds,
        stepEndsAt = System.currentTimeMillis() + step.durationSeconds * 1000L,
        cultureId = cultureId
    )

    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                val run = db.timers().activeRunOnce()
                if (run == null) {
                    stopSelf()
                    return@launch
                }
                val remaining = remainingSeconds(run)
                if (!run.paused && remaining <= 0) {
                    advance(manual = false)
                } else {
                    notify(buildRunNotification(run, remaining))
                }
                delay(500)
            }
        }
    }

    /** Move to the next step, or finish the protocol if this was the last one. */
    private suspend fun advance(manual: Boolean) {
        val run = db.timers().activeRunOnce() ?: return
        val protocolId = run.protocolId

        if (!manual) alertStepComplete(run)

        if (protocolId == null) {
            finish(run, manual)
            return
        }
        val steps = db.protocols().stepsOnce(protocolId)
        val nextIndex = run.stepIndex + 1
        if (nextIndex >= steps.size) {
            finish(run, manual)
            return
        }
        db.timers().update(run.copy(active = false))
        db.timers().upsert(runFor(run.protocolName, protocolId, steps[nextIndex], nextIndex, run.cultureId))
    }

    private suspend fun finish(run: TimerRun, manual: Boolean) {
        db.timers().update(run.copy(active = false))
        notifyCompletion(run.protocolName)
        stopForegroundCompat()
        stopSelf()
    }

    private suspend fun togglePause() {
        val run = db.timers().activeRunOnce() ?: return
        if (run.paused) {
            db.timers().update(
                run.copy(
                    paused = false,
                    stepEndsAt = System.currentTimeMillis() + run.pausedRemainingSeconds * 1000L
                )
            )
        } else {
            db.timers().update(run.copy(paused = true, pausedRemainingSeconds = remainingSeconds(run)))
        }
    }

    private suspend fun stopRun() {
        db.timers().clearActive()
        stopForegroundCompat()
        stopSelf()
    }

    // --- Notifications ------------------------------------------------------

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(label: String, action: String, code: Int) = NotificationCompat.Action(
        0, label,
        PendingIntent.getBroadcast(
            this, code,
            Intent(this, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    private fun placeholderNotification() = NotificationCompat.Builder(this, CHANNEL_RUNNING)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle("Lab timer")
        .setContentText("Starting…")
        .setOngoing(true)
        .setContentIntent(contentIntent())
        .build()

    private fun buildRunNotification(run: TimerRun, remaining: Int) =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(run.stepTitle)
            .setContentText(
                if (run.paused) "Paused · ${formatClock(run.pausedRemainingSeconds)} left"
                else "${formatClock(remaining)} · ${run.protocolName}"
            )
            .setProgress(
                run.stepDurationSeconds.coerceAtLeast(1),
                (run.stepDurationSeconds - remaining).coerceIn(0, run.stepDurationSeconds),
                false
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent())
            .addAction(action(if (run.paused) "Resume" else "Pause", ACTION_PAUSE, 1))
            .addAction(action("Next step", ACTION_NEXT, 2))
            .addAction(action("Stop", ACTION_STOP, 3))
            .build()

    private fun alertStepComplete(run: TimerRun) {
        vibrate(longArrayOf(0, 400, 200, 400))
        NotificationManagerCompat.from(this).notify(
            ALERT_ID,
            NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Step finished: ${run.stepTitle}")
                .setContentText(run.protocolName)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(contentIntent())
                .build()
        )
    }

    private fun notifyCompletion(protocolName: String) {
        vibrate(longArrayOf(0, 600, 300, 600, 300, 600))
        NotificationManagerCompat.from(this).notify(
            ALERT_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("$protocolName complete")
                .setContentText("All steps finished.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent())
                .build()
        )
    }

    private fun notify(notification: android.app.Notification) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(FOREGROUND_ID, notification)
        }
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.labbench.timer.START"
        const val ACTION_NEXT = "com.labbench.timer.NEXT"
        const val ACTION_PAUSE = "com.labbench.timer.PAUSE"
        const val ACTION_STOP = "com.labbench.timer.STOP"
        const val EXTRA_PROTOCOL_ID = "protocolId"
        const val EXTRA_CULTURE_ID = "cultureId"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"

        const val CHANNEL_RUNNING = "timer_running"
        const val CHANNEL_ALERTS = "timer_alerts"
        const val CHANNEL_REMINDERS = "lab_reminders"
        private const val FOREGROUND_ID = 1001
        private const val ALERT_ID = 2001

        fun startProtocol(context: Context, protocolId: String, cultureId: String? = null) {
            context.startForegroundService(
                Intent(context, ProtocolTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PROTOCOL_ID, protocolId)
                    .putExtra(EXTRA_CULTURE_ID, cultureId)
            )
        }

        fun startQuickTimer(context: Context, label: String, seconds: Int) {
            context.startForegroundService(
                Intent(context, ProtocolTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_SECONDS, seconds)
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, ProtocolTimerService::class.java).setAction(action))
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The live countdown for the protocol you're running."
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Step alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Fires the moment a timed step ends."
                    enableVibration(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, "Culture and stock reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Feeds due, passage limits, expiring reagents, equipment service."
                }
            )
        }
    }
}

fun remainingSeconds(run: TimerRun): Int =
    if (run.paused) run.pausedRemainingSeconds
    else (((run.stepEndsAt - System.currentTimeMillis()) / 1000.0).toInt()).coerceAtLeast(0)

fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

/** Turns notification button taps into service commands. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.action?.let { ProtocolTimerService.send(context, it) }
    }
}
