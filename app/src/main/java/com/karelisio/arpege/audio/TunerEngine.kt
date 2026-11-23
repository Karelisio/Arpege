package com.karelisio.arpege.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import com.karelisio.arpege.theory.dsp.PitchEstimate
import com.karelisio.arpege.theory.dsp.TimedPitch
import com.karelisio.arpege.theory.dsp.YinPitchDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.concurrent.thread

/** Capture micro (UNPROCESSED si disponible, sinon MIC) + détection YIN en continu. */
class TunerEngine(private val context: Context) {
    val sampleRate = 44100
    private val window = 4096
    private val hop = 1024

    private fun audioSource(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
    }

    /** Flux d'estimations (null = silence / pas de hauteur nette). Nécessite RECORD_AUDIO. */
    fun pitches(): Flow<PitchEstimate?> = timedPitches().map { it.estimate }

    /**
     * Comme [pitches], mais chaque estimation est horodatée sur l'horloge de capture
     * (CLOCK_MONOTONIC, comparable à System.nanoTime et aux instants de SoundPlayer) :
     * position en échantillons + AudioTimestamp de l'AudioRecord quand il est disponible.
     */
    @SuppressLint("MissingPermission")
    fun timedPitches(): Flow<TimedPitch> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord(
            audioSource(), sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuf, window * 4 * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("Micro indisponible"))
            return@callbackFlow
        }
        val detector = YinPitchDetector(sampleRate, window)
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val worker = thread(name = "tuner", priority = Thread.MAX_PRIORITY) {
            val ring = FloatArray(window)
            val chunk = FloatArray(hop)
            val ts = AudioTimestamp()
            val nanosPerFrame = 1_000_000_000.0 / sampleRate
            var frames = 0L // échantillons lus depuis le début de la capture
            var anchorNanos = Long.MIN_VALUE // instant de l'échantillon 0
            var hops = 0
            record.startRecording()
            while (running.get()) {
                var read = 0
                while (read < hop && running.get()) {
                    val n = record.read(chunk, read, hop - read, AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    read += n
                }
                if (read < hop) continue
                frames += hop
                // Recalage périodique sur l'horloge du flux (dérive, latence d'entrée).
                if (hops++ % 128 == 0) {
                    anchorNanos = if (record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        ts.nanoTime - (ts.framePosition * nanosPerFrame).toLong()
                    } else if (anchorNanos == Long.MIN_VALUE) {
                        System.nanoTime() - (frames * nanosPerFrame).toLong()
                    } else anchorNanos
                }
                System.arraycopy(ring, hop, ring, 0, window - hop)
                System.arraycopy(chunk, 0, ring, window - hop, hop)
                // Horodatage du centre de la fenêtre analysée.
                val center = anchorNanos + ((frames - window / 2) * nanosPerFrame).toLong()
                trySend(TimedPitch(center, detector.detect(ring, silenceRms = 0.004)))
            }
            runCatching { record.stop() }
            record.release()
        }
        awaitClose {
            running.set(false)
            worker.join(500)
        }
    }.flowOn(Dispatchers.Default)
}
