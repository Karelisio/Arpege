package com.karelisio.arpege.data

import android.content.Context
import android.util.Log
import com.karelisio.arpege.ArpegeApp
import com.karelisio.arpege.audio.MetronomeController
import com.karelisio.arpege.audio.SoundPlayer
import com.karelisio.arpege.data.db.ArpegeDatabase
import com.karelisio.arpege.reminder.Reminders
import com.karelisio.arpege.update.UpdateManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.karelisio.arpege.widget.ArpegeWidget

/** Injection de dépendances manuelle : un seul conteneur au niveau de l'application. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    /** Travail de fond : une erreur (base, DataStore…) est journalisée, jamais fatale à l'appli. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("Arpege", "Tâche de fond en échec", e) },
    )
    val settings = SettingsRepository(appContext)
    val db: ArpegeDatabase by lazy { ArpegeDatabase.build(appContext) }
    val updates: UpdateManager by lazy { UpdateManager(appContext, settings, scope) }
    val sound: SoundPlayer by lazy { SoundPlayer(appContext) }
    val practice = PracticeRepository(this)
    val stats = StatsRepository(this)
    val metronome: MetronomeController by lazy { MetronomeController(appContext) }

    init {
        // Re-planifie le rappel quotidien à chaque lancement (alarme perdue après un arrêt forcé).
        scope.launch { runCatching { settings.current().let { Reminders.apply(appContext, it.reminderEnabled, it.reminderMinutes) } } }
    }

    /** Appelé après chaque modification du journal (rafraîchit le widget). */
    fun onPracticeChanged() {
        scope.launch { ArpegeWidget.refresh(appContext) }
    }
}

val Context.container: AppContainer get() = (applicationContext as ArpegeApp).container
