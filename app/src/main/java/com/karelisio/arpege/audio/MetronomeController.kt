package com.karelisio.arpege.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import android.os.Build
import androidx.core.content.ContextCompat
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Accent
import com.karelisio.arpege.theory.dsp.Meter
import com.karelisio.arpege.theory.dsp.MetronomeConfig
import com.karelisio.arpege.theory.dsp.Tick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MetronomeState(val config: MetronomeConfig = MetronomeConfig(), val playing: Boolean = false)

/** Point d'entrée unique du métronome (écran, notification, deep link). */
class MetronomeController(private val context: Context) {
    private val c get() = context.container
    val sampleRate: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    val engine = MetronomeEngine(sampleRate)
    private val _state = MutableStateFlow(MetronomeState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()
    private var watcher: Job? = null
    private var startedAt = 0L

    init {
        c.scope.launch { setBpm(c.settings.current().metronomeBpm, persist = false) }
    }

    fun toggle() = if (_state.value.playing) stop() else start()

    fun start() {
        if (_state.value.playing) return
        engine.start(_state.value.config)
        _state.value = _state.value.copy(playing = true)
        startedAt = System.currentTimeMillis()
        ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        startWatcher()
    }

    fun stop() {
        if (!_state.value.playing) return
        engine.stop()
        watcher?.cancel()
        _state.value = _state.value.copy(playing = false)
        c.practice.record(PracticeActivity.METRONOME, startedAt, System.currentTimeMillis() - startedAt)
    }

    /** Arrêt complet (bouton × de la notification). */
    fun shutdown() {
        stop()
        context.stopService(Intent(context, MetronomeService::class.java))
    }

    fun setBpm(bpm: Int, persist: Boolean = true) = update { it.copy(bpm = bpm.coerceIn(20, 300)) }.also {
        if (persist) c.scope.launch { c.settings.setMetronomeBpm(_state.value.config.bpm) }
    }

    fun nudge(delta: Int) = setBpm(_state.value.config.bpm + delta)

    fun setMeter(meter: Meter) = update { it.copy(meter = meter, accents = meter.defaultAccents()) }
    fun setSubdivision(sub: Int) = update { it.copy(subdivision = sub.coerceIn(1, 4)) }
    fun cycleAccent(beat: Int) = update { cfg ->
        cfg.copy(accents = cfg.accents.mapIndexed { i, a -> if (i == beat) a.next() else a })
    }

    private fun update(f: (MetronomeConfig) -> MetronomeConfig) {
        val cfg = f(_state.value.config)
        _state.value = _state.value.copy(config = cfg)
        if (_state.value.playing) engine.update(cfg)
    }

    /** Tick en cours d'écoute et progression (0..1) dans ce tick, pour l'animation. */
    fun currentTick(): Pair<Tick, Float>? {
        val s = engine.schedule ?: return null
        val frame = engine.playbackFrame()
        val n = s.tickAt(frame)
        if (n < 0) return null
        val a = s.tickFrame(n); val b = s.tickFrame(n + 1)
        return s.tick(n) to ((frame - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }

    /** Vibration sur le temps fort, synchronisée sur la position de lecture réelle. */
    private fun startWatcher() {
        watcher?.cancel()
        watcher = c.scope.launch {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            var last = -1L
            while (isActive) {
                val vibrate = c.settings.current().vibrateDownbeat
                val t = currentTick()?.first
                if (t != null && t.index != last) {
                    last = t.index
                    if (vibrate && t.beat == 0 && t.sub == 0 && t.accent != Accent.MUTE) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    }
                }
                delay(4)
            }
        }
    }
}
