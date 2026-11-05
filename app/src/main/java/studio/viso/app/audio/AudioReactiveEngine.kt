package com.creatorstudio.app.audio

import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reads the output FFT and waveform from an Android audio session.
 * This is a live-preview analyser; offline export analysis is a separate pipeline.
 */
class AudioReactiveEngine(
    private val onSpectrum: (List<Float>) -> Unit,
    private val onWaveform: (List<Float>) -> Unit,
    private val onBeat: () -> Unit,
    private val onBpm: (Int?) -> Unit,
    private val onError: (String) -> Unit
) {
    private var visualizer: Visualizer? = null
    private val previousBands = FloatArray(32)
    private val fluxHistory = ArrayDeque<Float>()
    private val beatIntervals = ArrayDeque<Long>()
    private var lastBeatAtMs = 0L

    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        stop()
        if (audioSessionId <= 0) {
            onError("Audio session is not ready. Start playback and try again.")
            return false
        }
        var candidate: Visualizer? = null
        try {
            val effect = Visualizer(audioSessionId)
            candidate = effect
            val range = Visualizer.getCaptureSizeRange()
            if (effect.setCaptureSize(range[1]) != Visualizer.SUCCESS) {
                throw IllegalStateException("Audio analyser capture size is unavailable.")
            }
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null) return
                        val samples = waveform.map { ((it.toInt() and 0xFF) - 128) / 128f }
                        onWaveform(samples)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        val bands = toBands(fft, 32)
                        onSpectrum(bands)
                        detectBeat(bands)
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                true,
                true
            )
            if (effect.setEnabled(true) != Visualizer.SUCCESS) {
                throw IllegalStateException("Android audio analyser could not be enabled.")
            }
            visualizer = effect
            return true
        } catch (error: Exception) {
            runCatching { candidate?.release() }
            onError(error.message ?: "Live audio analysis could not start.")
            return false
        }
    }

    @Synchronized
    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        onSpectrum(List(32) { 0f })
        onWaveform(emptyList())
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
