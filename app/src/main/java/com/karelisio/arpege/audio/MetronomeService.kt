package com.karelisio.arpege.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.karelisio.arpege.MainActivity
import com.karelisio.arpege.R
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.TempoNames
import kotlinx.coroutines.launch

/** Service de premier plan (mediaPlayback) : le métronome continue écran éteint. */
class MetronomeService : LifecycleService() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val controller get() = container.metronome

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(controller.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        lifecycleScope.launch {
            controller.state.collect { s ->
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
                if (s.playing) acquireWakeLock() else releaseWakeLock()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(controller.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arpege:metronome").apply { acquire(4 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
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
            Intent(Intent.ACTION_VIEW, Uri.parse("arpege://metronome"), this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cfg = s.config
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${cfg.bpm} BPM · ${cfg.meter.label}")
            .setContentText(if (s.playing) TempoNames.of(cfg.bpm) else "En pause")
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
            .build()
    }

    companion object {
        private const val CHANNEL = "metronome"
        private const val NOTIF_ID = 42
        const val ACTION_TOGGLE = "com.karelisio.arpege.metronome.TOGGLE"
        const val ACTION_MINUS = "com.karelisio.arpege.metronome.MINUS"
        const val ACTION_PLUS = "com.karelisio.arpege.metronome.PLUS"
        const val ACTION_STOP = "com.karelisio.arpege.metronome.STOP"
    }
}
