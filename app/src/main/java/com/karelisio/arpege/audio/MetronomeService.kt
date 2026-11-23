package com.karelisio.arpege.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.net.toUri
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.karelisio.arpege.MainActivity
import com.karelisio.arpege.R
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.TempoNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Service de premier plan (mediaPlayback) : le métronome continue écran éteint.
 * Sans l'autorisation de notifier (Android 13+), le service tourne quand même : la notification
 * est simplement masquée par le système. Aucune erreur système ne doit faire planter l'appli.
 */
class MetronomeService : LifecycleService() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val controller get() = container.metronome

    override fun onCreate() {
        super.onCreate()
        runCatching { createChannel() }.onFailure { Log.w(TAG, "Canal de notification", it) }
        goForeground()
        lifecycleScope.launch {
            // StateFlow est conflué : au plus ~4 mises à jour par seconde, la dernière gagne.
            controller.state.collect { s ->
                runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(s)) }
                if (s.playing) acquireWakeLock() else releaseWakeLock()
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        when (intent?.action) {
            ACTION_TOGGLE -> controller.toggle()
            ACTION_MINUS -> controller.nudge(-5)
            ACTION_PLUS -> controller.nudge(5)
            ACTION_STOP -> { controller.stop(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        controller.stop()
        super.onDestroy()
    }

    private fun goForeground() {
        runCatching {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(controller.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }.onFailure { Log.w(TAG, "Passage au premier plan refusé", it) }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = runCatching {
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arpege:metronome").apply { acquire(4 * 60 * 60 * 1000L) }
        }.getOrNull()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_metronome), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun action(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, MetronomeService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(s: MetronomeState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW, "arpege://metronome".toUri(), this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cfg = s.config
        val text = when {
            !s.playing -> "En pause"
            s.trainerStartBpm != null && cfg.bpm < s.trainer.maxBpm -> "${TempoNames.of(cfg.bpm)} · accélération jusqu'à ${s.trainer.maxBpm}"
            else -> TempoNames.of(cfg.bpm)
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${cfg.bpm} BPM · ${cfg.meter.label}")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(s.playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "−5", action(ACTION_MINUS))
            .addAction(0, if (s.playing) "Pause" else "Lecture", action(ACTION_TOGGLE))
            .addAction(0, "+5", action(ACTION_PLUS))
            .addAction(0, "Fermer", action(ACTION_STOP))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
        // Minuteur de séance : compte à rebours natif, sans mise à jour par seconde.
        val end = s.timerEndsAt
        if (s.playing && end != null) b.setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        else b.setShowWhen(false)
        return b.build()
    }

    companion object {
        private const val TAG = "MetronomeService"
        private const val CHANNEL = "metronome"
        private const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "com.karelisio.arpege.metronome.TOGGLE"
        const val ACTION_MINUS = "com.karelisio.arpege.metronome.MINUS"
        const val ACTION_PLUS = "com.karelisio.arpege.metronome.PLUS"
        const val ACTION_STOP = "com.karelisio.arpege.metronome.STOP"
    }
}
