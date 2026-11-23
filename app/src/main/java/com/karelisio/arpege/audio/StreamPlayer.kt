package com.karelisio.arpege.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.concurrent.thread

/**
 * Lecture en flux d'une source rendue bloc par bloc (boucle d'accompagnement infinie, morceau
 * long) : AudioTrack MODE_STREAM, aucun timer. La source est appelée avec des positions contiguës.
 * [startNanos] (System.nanoTime du 1er échantillon entendu) est réestimé en continu via
 * AudioTimestamp : position entendue = (maintenant − startNanos) × sampleRate.
 */
class StreamPlayer(context: Context) {
    val sampleRate: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    /** Remplit `out` à partir de l'échantillon `startFrame` ; renvoie le nombre d'échantillons valides (< out.size = fin). */
    fun interface Source {
        fun read(out: FloatArray, startFrame: Long): Int
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var generation = 0
    private var worker: Thread? = null

    /** Instant (System.nanoTime) où le premier échantillon est sorti, ou -1 si inconnu. */
    @Volatile var startNanos: Long = -1L
        private set

    val isPlaying: Boolean get() = running

    private fun buildTrack(lowLatency: Boolean): AudioTrack? {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuf <= 0) return null
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
                .setBufferSizeInBytes(maxOf(minBuf * 2, sampleRate / 10 * 4))
                .apply { if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
                .build()
        }.getOrNull() ?: return null
        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return null }
        return t
    }

    /**
     * Démarre la lecture de [source]. [onStart] (thread principal) reçoit l'instant du premier
     * échantillon entendu ; [onEnd] est appelé en fin de source ; [onError] si l'audio est indisponible.
     */
    fun start(source: Source, onStart: (Long) -> Unit = {}, onEnd: () -> Unit = {}, onError: () -> Unit = {}) {
        stop()
        val t = buildTrack(lowLatency = true) ?: buildTrack(lowLatency = false)
        if (t == null) { main.post(onError); return }
        val gen = ++generation
        track = t
        startNanos = -1L
        running = true
        worker = thread(name = "stream-player", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val chunk = FloatArray(1024)
            val ts = AudioTimestamp()
            var written = 0L
            var ended = false
            var failed = false
            try {
                t.play()
                val t0 = System.nanoTime()
                while (running && generation == gen) {
                    val n = source.read(chunk, written).coerceIn(0, chunk.size)
                    if (n > 0) {
                        val w = t.write(chunk, 0, n, AudioTrack.WRITE_BLOCKING)
                        if (w <= 0) break
                        written += w
                    }
                    // Horodatage matériel : corrige aussi les éventuels sous-dépassements.
                    if (t.getTimestamp(ts) && ts.framePosition > 0) {
                        val first = startNanos < 0
                        startNanos = ts.nanoTime - ts.framePosition * 1_000_000_000L / sampleRate
                        if (first) { val s = startNanos; main.post { if (generation == gen) onStart(s) } }
                    } else if (startNanos < 0 && System.nanoTime() - t0 > 250_000_000L) {
                        startNanos = t0
                        main.post { if (generation == gen) onStart(t0) }
                    }
                    if (n < chunk.size) { ended = true; break }
                }
                if (ended) {
                    // Laisser sortir la fin du flux avant de libérer la piste.
                    val limit = System.nanoTime() + 3_000_000_000L
                    while (running && generation == gen && t.playbackHeadPosition < written && System.nanoTime() < limit) Thread.sleep(15)
                }
            } catch (_: Exception) {
                failed = true
            } finally {
                runCatching { t.pause(); t.flush(); t.stop() }
                t.release()
                if (track === t) track = null
                if (generation == gen) {
                    running = false
                    when {
                        failed -> main.post(onError)
                        ended -> main.post(onEnd)
                    }
                }
            }
        }
    }

    /** Arrête la lecture (débloque l'écriture en cours) ; sans effet si rien ne joue. */
    fun stop() {
        generation++
        running = false
        track?.let { runCatching { it.pause(); it.flush() } }
        worker?.join(300)
        worker = null
        startNanos = -1L
    }
}
