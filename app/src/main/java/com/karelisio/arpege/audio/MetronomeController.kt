package com.karelisio.arpege.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.karelisio.arpege.data.PracticeActivity
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Accent
import com.karelisio.arpege.theory.dsp.Meter
import com.karelisio.arpege.theory.dsp.MetronomeConfig
import com.karelisio.arpege.theory.dsp.MetronomeFavorite
import com.karelisio.arpege.theory.dsp.SpeedTrainer
import com.karelisio.arpege.theory.dsp.Tick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MetronomeState(
    val config: MetronomeConfig = MetronomeConfig(),
    val playing: Boolean = false,
    val trainerOn: Boolean = false,
    val trainer: SpeedTrainer = SpeedTrainer(),
    /** Tempo de départ de l'accélération en cours (restauré à l'arrêt), null sinon. */
    val trainerStartBpm: Int? = null,
    val timerOn: Boolean = false,
    val timerMinutes: Int = 20,
    /** Fin du minuteur de séance (ms depuis l'epoch) pendant la lecture, null sinon. */
    val timerEndsAt: Long? = null,
)

/** Tick en cours d'écoute : son échantillon de départ et la progression (0..1) dans ce tick. */
data class TickPosition(val tick: Tick, val startFrame: Long, val progress: Float)

/** Point d'entrée unique du métronome (écran, notification, tuile, deep link). */
class MetronomeController(private val context: Context) {
    private val c get() = context.container
    val sampleRate: Int = runCatching {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    }.getOrNull()?.takeIf { it in 8000..192000 } ?: 48000
    val engine = MetronomeEngine(sampleRate)
    private val _state = MutableStateFlow(MetronomeState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()
    private val loaded = CompletableDeferred<Unit>()
    private var watcher: Job? = null
    private var persistJob: Job? = null
    private var startedAt = 0L
    @Volatile private var timerEnd: Long? = null // SystemClock.elapsedRealtime()
    @Volatile private var vibrateDownbeat = false

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    val favorites: Flow<List<MetronomeFavorite>> =
        c.settings.settings.map { it.metronomeFavorites }.distinctUntilChanged()

    init {
        engine.onTrainerTempo = { bpm -> _state.update { it.copy(config = it.config.copy(bpm = bpm)) } }
        engine.onFailure = { c.scope.launch(Dispatchers.Main) { stop() } }
        c.scope.launch {
            runCatching { c.settings.current() }.getOrNull()?.let { s ->
                _state.update {
                    it.copy(
                        config = it.config.copy(bpm = s.metronomeBpm.coerceIn(20, 300)),
                        trainerOn = s.trainerOn, trainer = s.trainer,
                        timerOn = s.timerOn, timerMinutes = s.timerMinutes,
                    )
                }
            }
            loaded.complete(Unit)
            c.settings.settings.collect { vibrateDownbeat = it.vibrateDownbeat }
        }
    }

    /** Attend le chargement des réglages enregistrés (tempo, accélération, minuteur). */
    suspend fun ready() = loaded.await()

    fun toggle() { if (_state.value.playing) stop() else start() }

    /**
     * Lance la lecture ; renvoie false si l'audio est indisponible. [requireService] : échoue aussi
     * si le service de premier plan est refusé (lancement depuis la tuile, appli en arrière-plan).
     */
    @Synchronized
    fun start(requireService: Boolean = false): Boolean {
        val st = _state.value
        if (st.playing) return true
        if (!engine.start(st.config, st.trainer.takeIf { st.trainerOn })) return false
        val serviceOk = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        }.onFailure { Log.w(TAG, "Service de premier plan refusé", it) }.isSuccess
        if (!serviceOk && requireService) { engine.stop(); return false }
        startedAt = System.currentTimeMillis()
        timerEnd = if (st.timerOn) SystemClock.elapsedRealtime() + st.timerMinutes * 60_000L else null
        _state.update {
            it.copy(
                playing = true,
                trainerStartBpm = if (it.trainerOn) it.config.bpm else null,
                timerEndsAt = timerEnd?.let(::wallClock),
            )
        }
        startWatcher()
        return true
    }

    /** Arrête la lecture ; après une accélération progressive, on revient au tempo de départ. */
    @Synchronized
    fun stop() {
        if (!_state.value.playing) return
        engine.stop()
        watcher?.cancel()
        watcher = null
        timerEnd = null
        _state.update { s ->
            s.copy(
                playing = false, timerEndsAt = null, trainerStartBpm = null,
                config = s.trainerStartBpm?.let { s.config.copy(bpm = it) } ?: s.config,
            )
        }
        runCatching { c.practice.record(PracticeActivity.METRONOME, startedAt, System.currentTimeMillis() - startedAt) }
    }

    /** Arrêt complet (bouton × de la notification). */
    fun shutdown() {
        stop()
        runCatching { context.stopService(Intent(context, MetronomeService::class.java)) }
    }

    fun setBpm(bpm: Int) {
        val v = bpm.coerceIn(20, 300)
        if (v == _state.value.config.bpm) return
        update(tempoChanged = true) { it.copy(bpm = v) }
        // Un réglage manuel pendant l'accélération la relance depuis ce tempo.
        _state.update { if (it.playing && it.trainerOn) it.copy(trainerStartBpm = v) else it }
        persistBpm()
    }

    fun nudge(delta: Int) = setBpm(_state.value.config.bpm + delta)

    fun setMeter(meter: Meter) = update { it.copy(meter = meter, accents = meter.defaultAccents()) }
    fun setSubdivision(sub: Int) = update { it.copy(subdivision = sub.coerceIn(1, 4)) }
    fun cycleAccent(beat: Int) = update { cfg ->
        cfg.copy(accents = cfg.accents.mapIndexed { i, a -> if (i == beat) a.next() else a })
    }

    // --- Favoris -------------------------------------------------------------------------------

    fun applyFavorite(f: MetronomeFavorite) {
        val tempo = f.bpm != _state.value.config.bpm
        update(tempoChanged = tempo) { cfg ->
            val sameMeter = cfg.meter.beats == f.meter.beats && cfg.meter.unit == f.meter.unit
            MetronomeConfig(
                bpm = f.bpm, meter = if (sameMeter) cfg.meter else f.meter, subdivision = f.subdivision,
                accents = if (sameMeter) cfg.accents else f.meter.defaultAccents(),
            )
        }
        if (tempo) {
            _state.update { if (it.playing && it.trainerOn) it.copy(trainerStartBpm = f.bpm) else it }
            persistBpm()
        }
    }

    /** Enregistre le réglage courant (un favori du même nom est remplacé). */
    fun saveFavorite(name: String) {
        val fav = MetronomeFavorite.of(name, _state.value.config)
        c.scope.launch {
            c.settings.updateMetronomeFavorites { list ->
                val i = list.indexOfFirst { it.name.equals(fav.name, ignoreCase = true) }
                if (i >= 0) list.toMutableList().also { it[i] = fav } else list + fav
            }
        }
    }

    fun deleteFavorite(f: MetronomeFavorite) {
        c.scope.launch { c.settings.updateMetronomeFavorites { list -> list - f } }
    }

    fun restoreFavorite(f: MetronomeFavorite, index: Int) {
        c.scope.launch {
            c.settings.updateMetronomeFavorites { list ->
                if (f in list) list else list.toMutableList().also { it.add(index.coerceIn(0, it.size), f) }
            }
        }
    }

    // --- Accélération progressive --------------------------------------------------------------

    fun setTrainerOn(on: Boolean) {
        val st = _state.updateAndGet { it.copy(trainerOn = on, trainerStartBpm = if (on && it.playing) it.config.bpm else null) }
        if (st.playing) engine.setTrainer(st.trainer.takeIf { on })
        persistTrainer(st)
    }

    fun setTrainer(t: SpeedTrainer) {
        val st = _state.updateAndGet { it.copy(trainer = t) }
        if (st.playing && st.trainerOn) engine.setTrainer(t)
        persistTrainer(st)
    }

    private fun persistTrainer(st: MetronomeState) {
        c.scope.launch { c.settings.setTrainer(st.trainerOn, st.trainer) }
    }

    // --- Minuteur de séance --------------------------------------------------------------------

    fun setTimerOn(on: Boolean) = setTimer(on, _state.value.timerMinutes)

    fun setTimerMinutes(minutes: Int) = setTimer(_state.value.timerOn, minutes.coerceIn(5, 60))

    @Synchronized
    private fun setTimer(on: Boolean, minutes: Int) {
        val st = _state.value
        if (st.timerOn == on && st.timerMinutes == minutes) return
        // En lecture, (ré)armer le minuteur repart de la durée choisie.
        timerEnd = if (on && st.playing) SystemClock.elapsedRealtime() + minutes * 60_000L else null
        _state.update { it.copy(timerOn = on, timerMinutes = minutes, timerEndsAt = timerEnd?.let(::wallClock)) }
        c.scope.launch { c.settings.setTimer(on, minutes) }
    }

    private fun finishTimer() {
        if (!_state.value.playing) return
        stop()
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220, 120, 480), -1))
    }

    // --- Interne -------------------------------------------------------------------------------

    private fun update(tempoChanged: Boolean = false, f: (MetronomeConfig) -> MetronomeConfig) {
        val st = _state.updateAndGet { it.copy(config = f(it.config)) }
        if (st.playing) engine.update(st.config, tempoChanged)
    }

    /** Enregistre le tempo une fois la roue relâchée (pas une écriture par cran). */
    private fun persistBpm() {
        persistJob?.cancel()
        persistJob = c.scope.launch {
            delay(400)
            c.settings.setMetronomeBpm(_state.value.trainerStartBpm ?: _state.value.config.bpm)
        }
    }

    private fun wallClock(elapsed: Long) = System.currentTimeMillis() + (elapsed - SystemClock.elapsedRealtime())

    private fun vibrate(effect: VibrationEffect) {
        runCatching { vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect) }
    }

    /** Tick en cours d'écoute et progression dans ce tick, pour l'animation. */
    fun currentTick(): TickPosition? {
        val root = engine.schedule ?: return null
        val frame = engine.playbackFrame()
        if (frame < 0) return null
        val s = root.at(frame)
        val n = s.tickAt(frame)
        if (n < 0) return null
        val a = s.tickFrame(n)
        val b = s.tickFrame(n + 1)
        return TickPosition(s.tick(n), a, ((frame - a).toFloat() / (b - a)).coerceIn(0f, 1f))
    }

    /** Vibration sur le temps fort (position de lecture réelle) et fin du minuteur de séance. */
    private fun startWatcher() {
        watcher?.cancel()
        watcher = c.scope.launch {
            var last = Long.MIN_VALUE
            while (isActive) {
                val end = timerEnd
                if (end != null && SystemClock.elapsedRealtime() >= end) {
                    withContext(Dispatchers.Main) { finishTimer() }
                    break
                }
                val t = currentTick()
                if (t != null && t.startFrame != last) {
                    last = t.startFrame
                    val k = t.tick
                    if (vibrateDownbeat && k.beat == 0 && k.sub == 0 && k.accent != Accent.MUTE) {
                        vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    }
                }
                delay(4)
            }
        }
    }

    private companion object { const val TAG = "Metronome" }
}
