package com.karelisio.arpege.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.arpege.data.db.PracticeSession
import kotlinx.coroutines.launch

/** Activités de pratique suivies par le journal. */
enum class PracticeActivity(val label: String) {
    TUNER("Accordeur"), METRONOME("Métronome"), TABS("Tablatures"), CHORDS("Accords"),
    SCALES("Gammes"), CIRCLE("Cercle des quintes"), EAR("Oreille"), RHYTHM("Rythme"),
    SOLFEGE("Solfège"), FREE("Pratique libre");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.name == key } ?: FREE
    }
}

class PracticeRepository(private val container: AppContainer) {
    /** Enregistre une séance si elle dure au moins une minute (auto) ; met à jour le widget. */
    fun record(activity: PracticeActivity, startMs: Long, durationMs: Long, auto: Boolean = true) {
        if (auto && durationMs < 60_000) return
        container.scope.launch {
            val ins = container.settings.current().instrument
            container.db.practice().insert(
                PracticeSession(
                    startEpochMs = startMs, durationSec = (durationMs / 1000).toInt(),
                    instrument = ins.name, activity = activity.name, auto = auto,
                ),
            )
            container.onPracticeChanged()
        }
    }
}

/** Compte le temps passé (au premier plan) sur un écran et l'enregistre au-delà d'une minute. */
@Composable
fun TrackPractice(activity: PracticeActivity) {
    val c = LocalContext.current.container
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle) {
        val firstStart = System.currentTimeMillis()
        var resumedAt = if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) firstStart else 0L
        var accumulated = 0L
        val observer = LifecycleEventObserver { _, e ->
            val now = System.currentTimeMillis()
            when (e) {
                Lifecycle.Event.ON_RESUME -> resumedAt = now
                Lifecycle.Event.ON_PAUSE -> if (resumedAt > 0) { accumulated += now - resumedAt; resumedAt = 0 }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumedAt > 0) accumulated += System.currentTimeMillis() - resumedAt
            c.practice.record(activity, firstStart, accumulated)
        }
    }
}
