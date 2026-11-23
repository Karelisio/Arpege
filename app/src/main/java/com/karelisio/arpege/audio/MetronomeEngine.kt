package com.karelisio.arpege.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import com.karelisio.arpege.theory.dsp.MetronomeConfig
import com.karelisio.arpege.theory.dsp.MetronomeRenderer
import com.karelisio.arpege.theory.dsp.MetronomeSchedule
import kotlin.concurrent.thread

/**
 * Flux AudioTrack basse latence alimenté par [MetronomeRenderer] : chaque clic est placé
 * à l'échantillon près (aucun timer). La position de lecture sert à synchroniser l'affichage.
 */
class MetronomeEngine(val sampleRate: Int) {
    private val renderer = MetronomeRenderer(sampleRate)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var framesWritten = 0L
    @Volatile var schedule: MetronomeSchedule? = null
        private set
    private var worker: Thread? = null
    private val ts = AudioTimestamp()

    val isRunning: Boolean get() = running

    /** Démarre le flux ; renvoie false si la sortie audio est indisponible. */
    fun start(config: MetronomeConfig): Boolean {
        if (running) { update(config); return true }
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
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
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minBuf * 2)
            .build()
        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return false }
        framesWritten = 0
        // Premier clic légèrement différé pour laisser le flux démarrer.
        val s = MetronomeSchedule(sampleRate, config, originFrame = sampleRate / 20L)
        schedule = s
        renderer.schedule = s
        track = t
        running = true
        if (runCatching { t.play() }.isFailure) { running = false; t.release(); track = null; schedule = null; return false }
        worker = thread(name = "metronome", priority = Thread.MAX_PRIORITY) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunk = FloatArray(256)
            while (running) {
                renderer.render(chunk, framesWritten)
                val n = t.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                if (n <= 0) break
                framesWritten += n
            }
        }
        return true
    }

    /**
     * Change les réglages en cours de lecture sans à-coup : le nouveau tempo démarre sur le
     * prochain temps, la position dans la mesure est conservée (sauf changement de mesure).
     */
    fun update(config: MetronomeConfig) {
        val old = schedule
        if (!running || old == null) return
        val s = old.retimed(config, framesWritten)
        schedule = s
        renderer.schedule = s
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
        track?.let { runCatching { it.pause(); it.flush(); it.stop() }; it.release() }
        track = null
        schedule = null
    }

    /** Échantillon actuellement entendu (estimé via AudioTimestamp). */
    fun playbackFrame(): Long {
        val t = track ?: return -1
        return if (t.getTimestamp(ts)) {
            ts.framePosition + (System.nanoTime() - ts.nanoTime) * sampleRate / 1_000_000_000L
        } else t.playbackHeadPosition.toLong()
    }
}
