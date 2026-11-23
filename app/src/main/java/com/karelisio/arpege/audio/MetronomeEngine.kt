package com.karelisio.arpege.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.karelisio.arpege.theory.dsp.MetronomeConfig
import com.karelisio.arpege.theory.dsp.MetronomeRenderer
import com.karelisio.arpege.theory.dsp.MetronomeSchedule
import com.karelisio.arpege.theory.dsp.SpeedTrainer
import com.karelisio.arpege.theory.dsp.SpeedTrainerRun
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Flux AudioTrack basse latence alimenté par [MetronomeRenderer] : chaque clic est placé
 * à l'échantillon près (aucun timer). La position de lecture sert à synchroniser l'affichage.
 *
 * Le planning n'est modifié que par le thread audio (réglages en attente appliqués avant chaque
 * tampon) : aucun verrou, et l'accélération progressive tombe exactement sur le début de mesure.
 * Rien ici ne doit pouvoir planter l'appli : toute erreur audio arrête simplement le flux.
 */
class MetronomeEngine(val sampleRate: Int) {
    private class PendingConfig(val config: MetronomeConfig, val tempoChanged: Boolean)
    private class PendingTrainer(val trainer: SpeedTrainer?)

    /** Une lecture : son propre AudioTrack, son thread et ses réglages en attente. */
    private inner class Session(val track: AudioTrack, initial: MetronomeSchedule, trainer: SpeedTrainer?) {
        @Volatile var active = true
        @Volatile var schedule: MetronomeSchedule = initial
        @Volatile var framesWritten = 0L
        val pendingConfig = AtomicReference<PendingConfig?>(null)
        val pendingTrainer = AtomicReference<PendingTrainer?>(null)
        var trainerRun: SpeedTrainerRun? = trainer?.let { SpeedTrainerRun(it, initial.config.bpm) }
        var worker: Thread? = null
    }

    private val renderer = MetronomeRenderer(sampleRate)
    @Volatile private var session: Session? = null
    private val ts = AudioTimestamp()

    /** Appelé depuis le thread audio quand l'accélération progressive change le tempo. */
    @Volatile var onTrainerTempo: ((Int) -> Unit)? = null

    /** Appelé depuis le thread audio si le flux s'interrompt de lui-même (erreur de sortie). */
    @Volatile var onFailure: (() -> Unit)? = null

    val isRunning: Boolean get() = session?.active == true

    /** Planning en cours (null à l'arrêt). */
    val schedule: MetronomeSchedule? get() = session?.takeIf { it.active }?.schedule

    /** Démarre le flux ; renvoie false si la sortie audio est indisponible. */
    fun start(config: MetronomeConfig, trainer: SpeedTrainer?): Boolean {
        session?.takeIf { it.active }?.let { update(config, tempoChanged = true); setTrainer(trainer); return true }
        val t = createTrack(lowLatency = true) ?: createTrack(lowLatency = false) ?: return false
        // Premier clic légèrement différé pour laisser le flux démarrer.
        val s = Session(t, MetronomeSchedule(sampleRate, config, originFrame = sampleRate / 20L), trainer)
        if (runCatching { t.play() }.isFailure) { release(t); return false }
        val w = runCatching { thread(name = "metronome", priority = Thread.MAX_PRIORITY) { loop(s) } }.getOrNull()
        if (w == null) { release(t); return false }
        s.worker = w
        session = s
        return true
    }

    /**
     * Change les réglages en cours de lecture sans à-coup : le nouveau tempo démarre sur le
     * prochain temps, la position dans la mesure est conservée (sauf changement de mesure).
     * [tempoChanged] = changement manuel du tempo (relance l'accélération depuis ce tempo) ;
     * sinon le tempo courant du flux est conservé.
     */
    fun update(config: MetronomeConfig, tempoChanged: Boolean) {
        val s = session?.takeIf { it.active } ?: return
        s.pendingConfig.getAndUpdate { prev -> PendingConfig(config, tempoChanged || prev?.tempoChanged == true) }
    }

    /** Active (ou coupe, si null) l'accélération progressive à partir du tempo courant. */
    fun setTrainer(trainer: SpeedTrainer?) {
        session?.takeIf { it.active }?.pendingTrainer?.set(PendingTrainer(trainer))
    }

    fun stop() {
        val s = session ?: return
        session = null
        s.active = false
        // Débloque une écriture bloquante avant de joindre le thread.
        runCatching { s.track.pause() }
        runCatching { s.track.flush() }
        s.worker?.let { runCatching { it.join(500) } }
        runCatching { s.track.stop() }
        release(s.track)
    }

    /** Échantillon actuellement entendu (estimé via AudioTimestamp), ‑1 à l'arrêt. */
    fun playbackFrame(): Long {
        val s = session?.takeIf { it.active } ?: return -1
        return runCatching {
            synchronized(ts) {
                if (s.track.getTimestamp(ts)) {
                    ts.framePosition + (System.nanoTime() - ts.nanoTime) * sampleRate / 1_000_000_000L
                } else s.track.playbackHeadPosition.toLong()
            }
        }.getOrDefault(-1L)
    }

    private fun loop(s: Session) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
        val chunk = FloatArray(CHUNK)
        var failed = false
        try {
            while (s.active) {
                val from = s.framesWritten
                s.schedule = prepare(s, from, from + CHUNK)
                renderer.render(chunk, from, s.schedule)
                val n = runCatching { s.track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING) }.getOrDefault(-1)
                if (n <= 0) { failed = s.active; break }
                s.framesWritten = from + n
            }
        } catch (e: Exception) {
            Log.w(TAG, "Flux du métronome interrompu", e)
            failed = s.active
        }
        if (failed) runCatching { onFailure?.invoke() }
    }

    /** Applique les réglages en attente et l'accélération pour le tampon [from, until). */
    private fun prepare(s: Session, from: Long, until: Long): MetronomeSchedule {
        var sched = s.schedule
        s.pendingConfig.getAndSet(null)?.let { p ->
            val cfg = if (p.tempoChanged) p.config else p.config.copy(bpm = sched.config.bpm)
            sched = sched.retimed(cfg, from - 1)
            s.trainerRun = s.trainerRun?.let { run ->
                if (p.tempoChanged) SpeedTrainerRun(run.trainer, cfg.bpm) else run.also { it.resync() }
            }
        }
        s.pendingTrainer.getAndSet(null)?.let { p ->
            s.trainerRun = p.trainer?.let { SpeedTrainerRun(it, sched.config.bpm) }
        }
        s.trainerRun?.let { run ->
            val before = sched.config.bpm
            sched = run.advance(sched, from, until)
            if (sched.config.bpm != before) {
                val bpm = sched.config.bpm
                runCatching { onTrainerTempo?.invoke(bpm) }
            }
        }
        // Les plannings révolus depuis plus d'une seconde ne servent plus (ni au rendu ni à l'affichage).
        return if (sched.previous != null) sched.pruned(from - sampleRate) else sched
    }

    private fun createTrack(lowLatency: Boolean): AudioTrack? {
        val t = runCatching {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val b = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (minBuf > 0) b.setBufferSizeInBytes(minBuf * 2)
            if (lowLatency) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            b.build()
        }.onFailure { Log.w(TAG, "AudioTrack indisponible (basse latence : $lowLatency)", it) }.getOrNull() ?: return null
        if (t.state != AudioTrack.STATE_INITIALIZED) { release(t); return null }
        return t
    }

    private fun release(t: AudioTrack) { runCatching { t.release() } }

    private companion object {
        const val TAG = "MetronomeEngine"
        const val CHUNK = 256
    }
}
