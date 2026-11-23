package com.karelisio.arpege.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.karelisio.arpege.MainActivity
import com.karelisio.arpege.R
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.practice.PracticeStats
import com.karelisio.arpege.theory.practice.ReminderPlan
import com.karelisio.arpege.theory.practice.SessionLite
import com.karelisio.arpege.ui.permissions.AppPermission
import com.karelisio.arpege.ui.permissions.isGranted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Rappel quotidien de pratique : une alarme AlarmManager (sans dépendance), re-planifiée
 * à chaque déclenchement, au démarrage du téléphone, au changement d'heure et au lancement de l'appli.
 */
object Reminders {
    const val ACTION_REMIND = "com.karelisio.arpege.action.REMIND"
    private const val CHANNEL = "practice_reminder"
    private const val NOTIF_ID = 2001

    /** Planifie le prochain rappel (ou l'annule). [from] : instant de référence. */
    // SCHEDULE_EXACT_ALARM n'existe qu'à partir d'Android 12 : l'alarme exacte n'est utilisée qu'avant.
    @SuppressLint("MissingPermission")
    fun apply(context: Context, enabled: Boolean, minuteOfDay: Int, from: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        if (!enabled) return
        val at = ReminderPlan.next(from, minuteOfDay).toInstant().toEpochMilli()
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Avant Android 12, l'alarme exacte ne demande aucune autorisation.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // Inexacte mais autorisée en veille (Doze) ; Android 12+ la livre en moins d'une heure.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    /** Re-planifie selon les réglages enregistrés. */
    suspend fun sync(context: Context, from: ZonedDateTime = ZonedDateTime.now()) {
        val s = context.container.settings.current()
        apply(context, s.reminderEnabled, s.reminderMinutes, from)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Affiche le rappel, sauf si l'on a déjà pratiqué aujourd'hui. */
    suspend fun remind(context: Context) {
        val c = context.container
        val sessions = c.db.practice().since(System.currentTimeMillis() - 400L * 86_400_000L)
            .map { SessionLite(it.startEpochMs, it.durationSec, it.instrument, it.activity) }
        val stats = PracticeStats(sessions)
        val today = LocalDate.now()
        if (stats.practicedOn(today) || !AppPermission.NOTIFICATIONS.isGranted(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_reminder), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val streak = stats.streak(today)
        val goal = c.settings.current().weeklyGoalMinutes
        val text = if (streak > 0) {
            "Série de $streak jour${if (streak > 1) "s" else ""} : une séance aujourd'hui pour la prolonger !"
        } else {
            "Quelques minutes suffisent pour lancer une nouvelle série."
        }
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("C'est l'heure de pratiquer")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nCette semaine : ${stats.weekMinutes(today)} / $goal min."))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notification)
    }
}

/** Déclenchement du rappel, et re-planification après redémarrage, mise à jour ou changement d'heure. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        app.container.scope.launch {
            try {
                if (intent.action == Reminders.ACTION_REMIND) {
                    runCatching { Reminders.remind(app) }
                    // Une minute de marge : jamais deux rappels pour la même échéance.
                    runCatching { Reminders.sync(app, ZonedDateTime.now().plusMinutes(1)) }
                } else {
                    runCatching { Reminders.sync(app) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
