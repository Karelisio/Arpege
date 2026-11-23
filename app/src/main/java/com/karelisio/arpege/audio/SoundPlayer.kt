package com.karelisio.arpege.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.karelisio.arpege.theory.Pitch
import com.karelisio.arpege.theory.dsp.NoteEvent
import com.karelisio.arpege.theory.dsp.Synth
import com.karelisio.arpege.theory.dsp.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lecture de sons synthétisés localement (cordes, piano, sinus). Une seule voix à la fois. */
class SoundPlayer(context: Context) {
    val sampleRate: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var track: AudioTrack? = null
    private var job: Job? = null

    /** Joue un tampon ; [onStart] est appelé au démarrage effectif (synchro des animations). */
    fun play(buffer: FloatArray, onStart: () -> Unit = {}) = playTimed(buffer) { onStart() }

    /**
     * Comme [play], mais [onStart] reçoit l'instant (System.nanoTime / CLOCK_MONOTONIC) où le
     * premier échantillon sort réellement du haut-parleur, estimé via AudioTimestamp.
     */
    fun playTimed(buffer: FloatArray, onStart: (Long) -> Unit) {
        stop()
        if (buffer.isEmpty()) return
        job = scope.launch {
            val t = runCatching { AudioTrack.Builder()
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
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buffer.size * 4)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build() }.getOrNull() ?: return@launch
            // Sortie audio indisponible (ressources, focus…) : on abandonne sans planter.
            if (t.state != AudioTrack.STATE_INITIALIZED || t.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) {
                t.release()
                return@launch
            }
            track = t
            if (runCatching { t.play() }.isFailure) { t.release(); track = null; return@launch }
            val ts = android.media.AudioTimestamp()
            var startNanos = System.nanoTime()
            for (i in 0 until 50) {
                if (t.getTimestamp(ts) && ts.framePosition > 0) {
                    startNanos = ts.nanoTime - ts.framePosition * 1_000_000_000L / sampleRate
                    break
                }
                delay(5)
            }
            withContext(Dispatchers.Main) { onStart(startNanos) }
            delay(buffer.size * 1000L / sampleRate + 100)
            if (track === t) track = null
            runCatching { t.stop() }
            t.release()
        }
    }

    fun stop() {
        job?.cancel()
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
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
    fun playEvents(events: List<NoteEvent>, voice: Voice, onStart: (Long) -> Unit = {}) {
        scope.launch { playTimed(Synth.sequence(events, voice, sampleRate), onStart) }
    }

    /** Tampon déjà rendu, avec horodatage précis du début. */
    fun playBuffer(buffer: FloatArray, onStart: (Long) -> Unit = {}) = playTimed(buffer, onStart)

    fun playFrequency(freq: Double, seconds: Double = 2.5, voice: Voice = Voice.SINE) {
        scope.launch { play(Synth.render(voice, freq, sampleRate, seconds, 0.8)) }
    }
}
