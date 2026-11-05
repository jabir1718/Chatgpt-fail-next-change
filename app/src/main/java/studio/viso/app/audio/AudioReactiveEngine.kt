package com.creatorstudio.app.audio

import android.media.audiofx.Visualizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reads the output FFT and waveform from an Android audio session.
 * This is a live-preview analyser; offline export analysis is a separate pipeline.
 */
class AudioReactiveEngine(
    private val onSpectrum: (List<Float>) -> Unit,
    private val onWaveform: (List<Float>) -> Unit,
    private val onError: (String) -> Unit
) {
    private var visualizer: Visualizer? = null

    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        stop()
        if (audioSessionId <= 0) {
            onError("Audio session is not ready. Start playback and try again.")
            return false
        }
        try {
            val effect = Visualizer(audioSessionId)
            val range = Visualizer.getCaptureSizeRange()
            effect.captureSize = range[1]
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
                        onSpectrum(toBands(fft, 32))
                    }
                },
                (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1),
                true,
                true
            )
            effect.enabled = true
            visualizer = effect
            return true
        } catch (error: Exception) {
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
