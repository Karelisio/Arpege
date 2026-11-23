package com.karelisio.arpege.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.dsp.NoteEvent
import com.karelisio.arpege.theory.dsp.Synth
import com.karelisio.arpege.theory.dsp.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lecture de sons synthétisés localement (cordes, piano, sinus). Une seule voix à la fois.
 *
 * Flux AudioTrack (MODE_STREAM) écrit par blocs : compatible avec tous les appareils. On tente
 * d'abord le mode basse latence, puis un AudioTrack standard si celui-ci est refusé (certains
 * téléphones, dont des Honor, refusent les pistes « fast » statiques).
 */
class SoundPlayer(context: Context) {
    val sampleRate: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var track: AudioTrack? = null
    private var job: Job? = null

    /** Faux si aucune piste audio n'a pu être créée lors de la dernière tentative. */
    @Volatile var available: Boolean = true
        private set

    /** Joue un tampon ; [onStart] est appelé au démarrage effectif (synchro des animations). */
    fun play(buffer: FloatArray, onStart: () -> Unit = {}) = playTimed(buffer, { onStart() })

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
                .setBufferSizeInBytes(maxOf(minBuf * 2, 16 * 1024))
                .apply { if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
                .build()
        }.getOrNull() ?: return null
        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return null }
        return t
    }

    /**
     * Comme [play], mais [onStart] reçoit l'instant (System.nanoTime / CLOCK_MONOTONIC) où le
     * premier échantillon sort réellement du haut-parleur, estimé via AudioTimestamp.
     * [onError] est appelé (thread principal) si la sortie audio est indisponible.
     */
    fun playTimed(buffer: FloatArray, onStart: (Long) -> Unit, onError: () -> Unit = {}) {
        stop()
        if (buffer.isEmpty()) return
        job = scope.launch {
            val t = buildTrack(lowLatency = true) ?: buildTrack(lowLatency = false)
            if (t == null) {
                available = false
                withContext(Dispatchers.Main) { onError() }
                return@launch
            }
            available = true
            track = t
            try {
                t.play()
                val ts = AudioTimestamp()
                val t0 = System.nanoTime()
                var startNanos = -1L
                var offset = 0
                val chunk = 1024
                while (isActive && offset < buffer.size) {
                    val n = minOf(chunk, buffer.size - offset)
                    val w = t.write(buffer, offset, n, AudioTrack.WRITE_BLOCKING)
                    if (w <= 0) break
                    offset += w
                    if (startNanos < 0) {
                        if (t.getTimestamp(ts) && ts.framePosition > 0) {
                            startNanos = ts.nanoTime - ts.framePosition * 1_000_000_000L / sampleRate
                            withContext(Dispatchers.Main) { onStart(startNanos) }
                        } else if (System.nanoTime() - t0 > 250_000_000L) {
                            // Pas d'horodatage matériel : estimation à partir de l'instant de lecture.
                            startNanos = t0
                            withContext(Dispatchers.Main) { onStart(startNanos) }
                        }
                    }
                }
                if (startNanos < 0 && isActive) withContext(Dispatchers.Main) { onStart(t0) }
                // Laisser la fin du tampon sortir avant de libérer la piste.
                val end = System.nanoTime() + 3_000_000_000L
                while (isActive && t.playbackHeadPosition < offset && System.nanoTime() < end) delay(20)
            } catch (_: Exception) {
                withContext(NonCancellable + Dispatchers.Main) { onError() }
            } finally {
                if (track === t) track = null
                runCatching { t.pause(); t.flush(); t.stop() }
                t.release()
            }
        }
    }

    /** Arrête la lecture ; la piste est libérée par la coroutine de lecture elle-même. */
    fun stop() {
        job?.cancel()
        // Débloque un write() en cours pour que la coroutine se termine aussitôt.
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    /** Joue des hauteurs plaquées ([strumMs] petit) ou arpégées ([strumMs] grand). */
    fun playPitches(
        pitches: List<Pitch>,
        voice: Voice,
        a4: Double,
        strumMs: Int = 25,
        noteSec: Double = 2.2,
        onStart: () -> Unit = {},
    ) {
        scope.launch {
            val events = pitches.mapIndexed { i, p -> NoteEvent(i * strumMs / 1000.0, p.frequency(a4), noteSec, 0.75) }
            play(Synth.sequence(events, voice, sampleRate), onStart)
        }
    }

    /** Gamme ou mélodie : une note toutes les [stepMs] ms. */
    fun playMelody(pitches: List<Pitch>, voice: Voice, a4: Double, stepMs: Int = 320, onStart: () -> Unit = {}) {
        scope.launch {
            val step = stepMs / 1000.0
            val events = pitches.mapIndexed { i, p -> NoteEvent(i * step, p.frequency(a4), step * 1.6, 0.7) }
            play(Synth.sequence(events, voice, sampleRate), onStart)
        }
    }

    /** Événements arbitraires (entraîneurs). */
    fun playEvents(events: List<NoteEvent>, voice: Voice, onStart: (Long) -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch { playTimed(Synth.sequence(events, voice, sampleRate), onStart, onError) }
    }

    /** Tampon déjà rendu, avec horodatage précis du début. */
    fun playBuffer(buffer: FloatArray, onStart: (Long) -> Unit = {}, onError: () -> Unit = {}) = playTimed(buffer, onStart, onError)

    fun playFrequency(freq: Double, seconds: Double = 2.5, voice: Voice = Voice.SINE) {
        scope.launch { play(Synth.render(voice, freq, sampleRate, seconds, 0.8)) }
    }
}
