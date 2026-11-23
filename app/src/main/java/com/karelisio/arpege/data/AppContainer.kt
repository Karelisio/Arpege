package com.karelisio.arpege.data

import android.content.Context
import com.karelisio.arpege.ArpegeApp
import com.karelisio.arpege.audio.MetronomeController
import com.karelisio.arpege.audio.SoundPlayer
import com.karelisio.arpege.data.db.ArpegeDatabase
import com.karelisio.arpege.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Injection de dépendances manuelle : un seul conteneur au niveau de l'application. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(appContext)
    val db: ArpegeDatabase by lazy { ArpegeDatabase.build(appContext) }
    val updates: UpdateManager by lazy { UpdateManager(appContext, settings, scope) }
    val sound: SoundPlayer by lazy { SoundPlayer(appContext) }
    val practice = PracticeRepository(this)
    val metronome: MetronomeController by lazy { MetronomeController(appContext) }

    /** Appelé après chaque modification du journal (rafraîchit le widget). */
    fun onPracticeChanged() = Unit
}

val Context.container: AppContainer get() = (applicationContext as ArpegeApp).container
