package studio.viso.app.audio

import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reads FFT and waveform snapshots from Android audio sessions.
 *
 * Timeline mode combines per-player session snapshots; this is not a capture of
 * the final post-mixer output. Offline export analysis is a separate pipeline.
 */
class AudioReactiveEngine(
    private val onSpectrum: (List<Float>) -> Unit,
    private val onWaveform: (List<Float>) -> Unit,
    private val onBeat: () -> Unit,
    private val onBpm: (Int?) -> Unit,
    private val onError: (String) -> Unit
) {
    private val visualizers = linkedMapOf<Int, Visualizer>()
    private val sessionBands = linkedMapOf<Int, List<Float>>()
    private val sessionWaveforms = linkedMapOf<Int, List<Float>>()
    private val previousBands = FloatArray(32)
    private val fluxHistory = ArrayDeque<Float>()
    private val beatIntervals = ArrayDeque<Long>()
    private var lastBeatAtMs = 0L

    @Synchronized
    fun start(audioSessionId: Int): Boolean = start(setOf(audioSessionId))

    /** Starts a fresh analysis window for all currently active timeline sessions. */
    @Synchronized
    fun start(audioSessionIds: Set<Int>): Boolean {
        stop()
        val ids = audioSessionIds.filter { it > 0 }.toSet()
        if (ids.isEmpty()) {
            onError("No active timeline audio sessions are available. Start timeline playback first.")
            return false
        }
        return try {
            ids.forEach(::attachSession)
            true
        } catch (error: Exception) {
            stop()
            onError(error.message ?: "Live audio analysis could not start.")
            false
        }
    }

    /**
     * Incrementally attaches and detaches session effects. Existing sessions
     * stay alive when a clip enters or leaves the timeline, preserving beat/BPM
     * history and avoiding needless capture restarts.
     */
    @Synchronized
    fun updateSessions(audioSessionIds: Set<Int>) {
        val next = audioSessionIds.filter { it > 0 }.toSet()
        val delta = audioSessionDelta(visualizers.keys, next)

        delta.removed.forEach(::detachSession)
        try {
            delta.added.forEach(::attachSession)
        } catch (error: Exception) {
            onError(error.message ?: "A new timeline audio session could not be analysed.")
        }

        if (visualizers.isEmpty()) {
            onSpectrum(List(32) { 0f })
            onWaveform(emptyList())
            resetBeatHistory()
        } else {
            emitCombinedSnapshots()
        }
    }

    @Synchronized
    fun stop() {
        visualizers.keys.toList().forEach(::detachSession)
        visualizers.clear()
        sessionBands.clear()
        sessionWaveforms.clear()
        onSpectrum(List(32) { 0f })
        onWaveform(emptyList())
        resetBeatHistory()
    }

    private fun attachSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId in visualizers) return
        lateinit var effect: Visualizer
        effect = Visualizer(sessionId)
        try {
            val range = Visualizer.getCaptureSizeRange()
            check(effect.setCaptureSize(range[1]) == Visualizer.SUCCESS) {
                "Audio analyser capture size is unavailable."
            }
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null) return
                        synchronized(this@AudioReactiveEngine) {
                            if (visualizers[sessionId] !== effect) return
                            sessionWaveforms[sessionId] =
                                waveform.map { ((it.toInt() and 0xFF) - 128) / 128f }
                            emitCombinedWaveform()
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        synchronized(this@AudioReactiveEngine) {
                            if (visualizers[sessionId] !== effect) return
                            sessionBands[sessionId] = toBands(fft, 32)
                            val combined = combineBands(sessionBands.values.toList())
                            onSpectrum(combined)
                            detectBeat(combined)
                        }
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                true,
                true
            )
            // Register before enabling so the first callback is accepted.
            visualizers[sessionId] = effect
            check(effect.setEnabled(true) == Visualizer.SUCCESS) {
                "Android audio analyser could not be enabled for a timeline source."
            }
        } catch (error: Exception) {
            visualizers.remove(sessionId)
            sessionBands.remove(sessionId)
            sessionWaveforms.remove(sessionId)
            runCatching { effect.enabled = false; effect.release() }
            throw error
        }
    }

    private fun detachSession(sessionId: Int) {
        visualizers.remove(sessionId)?.let { effect ->
            runCatching { effect.enabled = false; effect.release() }
        }
        sessionBands.remove(sessionId)
        sessionWaveforms.remove(sessionId)
    }

    private fun emitCombinedSnapshots() {
        onSpectrum(combineBands(sessionBands.values.toList()))
        emitCombinedWaveform()
    }

    private fun emitCombinedWaveform() {
        val snapshots = sessionWaveforms.values
        if (snapshots.isEmpty()) {
            onWaveform(emptyList())
            return
        }
        val size = snapshots.minOf { it.size }
        onWaveform(List(size) { index ->
            snapshots.sumOf { it[index].toDouble() }.toFloat() / snapshots.size
        })
    }

    private fun combineBands(snapshots: List<List<Float>>): List<Float> {
        if (snapshots.isEmpty()) return List(32) { 0f }
        return List(32) { index ->
            snapshots.mapNotNull { it.getOrNull(index) }.average().toFloat().coerceIn(0f, 1f)
        }
    }

    private fun resetBeatHistory() {
        previousBands.fill(0f)
        fluxHistory.clear()
        beatIntervals.clear()
        lastBeatAtMs = 0L
        onBpm(null)
    }

    private fun detectBeat(bands: List<Float>) {
        var flux = 0f
        bands.forEachIndexed { index, value ->
            flux += (value - previousBands[index]).coerceAtLeast(0f)
            previousBands[index] = value
        }
        fluxHistory.addLast(flux)
        if (fluxHistory.size > 40) fluxHistory.removeFirst()
        val average = if (fluxHistory.isEmpty()) 0f else fluxHistory.average().toFloat()
        val now = SystemClock.elapsedRealtime()
        if (flux > maxOf(average * 1.45f, 0.055f) && now - lastBeatAtMs > 280L) {
            if (lastBeatAtMs > 0L) {
                val interval = now - lastBeatAtMs
                if (interval in 300L..1_500L) {
                    beatIntervals.addLast(interval)
                    if (beatIntervals.size > 6) beatIntervals.removeFirst()
                    val bpm = (60_000.0 / beatIntervals.average()).toInt()
                    onBpm(bpm.takeIf { it in 40..200 })
                }
            }
            lastBeatAtMs = now
            onBeat()
        }
    }

    private fun toBands(fft: ByteArray, count: Int): List<Float> {
        val totalBins = fft.size / 2
        val usableBins = (totalBins - 1).coerceAtLeast(1)
        return List(count) { band ->
            val start = 1 + (usableBins * band / count)
            val end = (1 + (usableBins * (band + 1) / count)).coerceAtLeast(start + 1)
            var sum = 0.0
            var samples = 0
            for (bin in start until end.coerceAtMost(totalBins)) {
                val real = fft[bin * 2].toInt()
                val imaginary = fft[bin * 2 + 1].toInt()
                sum += real * real + imaginary * imaginary
                samples++
            }
            val magnitude = if (samples == 0) 0.0 else sqrt(sum / samples)
            (ln(1.0 + magnitude) / ln(129.0)).toFloat().coerceIn(0f, 1f)
        }
    }
}

/** Pure set-diff helper keeps session reconciliation deterministic and testable. */
internal data class AudioSessionDelta(
    val removed: Set<Int>,
    val added: Set<Int>
)

internal fun audioSessionDelta(current: Set<Int>, desired: Set<Int>): AudioSessionDelta =
    AudioSessionDelta(
        removed = current - desired,
        added = desired - current
    )
