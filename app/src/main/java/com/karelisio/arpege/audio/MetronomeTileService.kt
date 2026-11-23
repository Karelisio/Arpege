package com.karelisio.arpege.audio

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.karelisio.arpege.MainActivity
import com.karelisio.arpege.R
import com.karelisio.arpege.data.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Tuile Réglages rapides « Métronome » : lance ou arrête le métronome, état reflété en direct. */
class MetronomeTileService : TileService() {
    private var listening: Job? = null
    private val controller get() = container.metronome

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = container.scope.launch(Dispatchers.Main) { controller.state.collect { render(it) } }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        container.scope.launch(Dispatchers.Main) {
            controller.ready()
            val m = controller
            when {
                m.state.value.playing -> m.stop()
                // Service de premier plan refusé (restrictions système) : on ouvre l'écran à la place.
                !m.start(requireService = true) -> openMetronome()
            }
            render(m.state.value)
        }
    }

    private fun render(s: MetronomeState) {
        val tile = qsTile ?: return
        runCatching {
            tile.label = getString(R.string.tile_metronome)
            tile.state = if (s.playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (s.playing) "${s.config.bpm} BPM" else "Arrêté"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = if (s.playing) "En lecture, ${s.config.bpm} BPM" else "Arrêté"
            }
            tile.updateTile()
        }.onFailure { Log.w(TAG, "Mise à jour de la tuile", it) }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openMetronome() {
        val intent = Intent(Intent.ACTION_VIEW, "arpege://metronome".toUri(), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(intent)
            }
        }.onFailure { Log.w(TAG, "Ouverture du métronome", it) }
    }

    private companion object { const val TAG = "MetronomeTile" }
}
