package com.creatorstudio.app.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class OfflineVisualizerFrameData(
    val frameIndex: Long,
    val timestampMs: Long,
    val rms: Float,
    val spectrumBands: List<Float>,
    val waveform: List<Float>,
    val beatDetected: Boolean,
    val beatPulse: Float
)

data class OfflineVisualizerAnalysis(
    val frames: List<OfflineVisualizerFrameData>,
    val detectedBpm: Int?
)

/**
 * Offline audio-reactive analysis using the exact project timeline mix.
 * Frames are generated in timeline order, so the mixer EQ/compressor state is continuous.
 */
object OfflineVisualizerAnalyzer {
    fun analyze(
        project: EditorProject,
        sources: Map<String, PcmAudioSource>,
        fftSize: Int = 1_024
    ): OfflineVisualizerAnalysis {
        require(fftSize >= 64 && fftSize.countOneBits() == 1) { "FFT size must be a power of two >= 64" }
        val totalFrames = RenderCompositionPlan.frameCount(project)
        if (totalFrames == 0L) return OfflineVisualizerAnalysis(emptyList(), null)

        val sampleRate = 48_000
        val totalAudioFrames = ceilFrames(project.durationMs, sampleRate)
        val mixer = OfflineAudioMixEngine(project, sources, sampleRate, 2)
        val frames = ArrayList<OfflineVisualizerFrameData>(totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val beatTimes = mutableListOf<Long>()
        val energyHistory = ArrayDeque<Float>()
        var pulse = 0f
        var lastBeatMs = Long.MIN_VALUE / 2

        for (frameIndex in 0 until totalFrames) {
            val startFrame = frameIndex * sampleRate / project.frameRate
            val nextStart = (frameIndex + 1L) * sampleRate / project.frameRate
            val hopFrames = (nextStart - startFrame).coerceAtLeast(1L)
            val availableFrames = (totalAudioFrames - startFrame).coerceAtLeast(0L)
            val blockFrames = minOf(hopFrames, availableFrames).toInt()
            val block = if (blockFrames > 0) mixer.renderBlock(startFrame, blockFrames)
                else MixedPcmBlock(startFrame, sampleRate, 2, FloatArray(0))
            val mono = FloatArray(blockFrames) { index ->
                (block.samples[index * 2] + block.samples[index * 2 + 1]) * 0.5f
            }
            val rms = if (mono.isEmpty()) 0f else sqrt(mono.sumOf { it.toDouble() * it } / mono.size).toFloat()
            val activeFftSize = minOf(fftSize, highestPowerOfTwoAtMost(maxOf(blockFrames, 64)))
            val spectrum = spectrum(mono, activeFftSize)
            val waveform = waveform(mono, 64)

            val timestampMs = frameIndex * 1_000L / project.frameRate
            val baseline = if (energyHistory.isEmpty()) 0f else energyHistory.average().toFloat()
            val beat = rms > 0.025f && baseline > 0.001f &&
                rms > baseline * 1.55f && timestampMs - lastBeatMs >= 220L
            if (beat) {
                beatTimes += timestampMs
                lastBeatMs = timestampMs
                pulse = 1f
            } else {
                pulse = (pulse * 0.76f).coerceAtLeast(0f)
            }
            energyHistory.addLast(rms)
            while (energyHistory.size > 24) energyHistory.removeFirst()

            frames += OfflineVisualizerFrameData(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                rms = rms,
                spectrumBands = spectrum,
                waveform = waveform,
                beatDetected = beat,
                beatPulse = pulse
            )
        }

        val bpm = if (beatTimes.size >= 2) {
            val intervals = beatTimes.zipWithNext { a, b -> b - a }.filter { it in 250L..1_500L }
            intervals.takeIf { it.isNotEmpty() }?.average()?.let { (60_000.0 / it).toInt().coerceIn(40, 240) }
        } else null
        return OfflineVisualizerAnalysis(frames, bpm)
    }

    private fun spectrum(samples: FloatArray, fftSize: Int): List<Float> {
        if (samples.isEmpty()) return List(32) { 0f }
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        for (i in 0 until minOf(samples.size, fftSize)) {
            val window = if (fftSize <= 1) 1.0 else 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))
            real[i] = samples[i] * window
        }
        fft(real, imaginary)
        val maxBin = fftSize / 2
        return List(32) { band ->
            val start = max(1, floor((maxBin.toDouble()).pow(band / 32.0)).toInt())
            val end = max(start + 1, floor(maxBin.toDouble().pow((band + 1) / 32.0)).toInt()).coerceAtMost(maxBin)
            var sum = 0.0
            var count = 0
            for (bin in start until end) {
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * 2.0 / fftSize
                sum += magnitude * magnitude
                count++
            }
            if (count == 0) 0f else (sqrt(sum / count) * 5.0).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            val half = length / 2
            var start = 0
            while (start < n) {
                var wr = 1.0
                var wi = 0.0
                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half
                    val oddReal = real[odd] * wr - imaginary[odd] * wi
                    val oddImaginary = real[odd] * wi + imaginary[odd] * wr
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextWr = wr * stepReal - wi * stepImaginary
                    wi = wr * stepImaginary + wi * stepReal
                    wr = nextWr
                }
                start += length
            }
            length = length shl 1
        }
    }

    private fun waveform(samples: FloatArray, count: Int): List<Float> {
        if (samples.isEmpty()) return List(count) { 0f }
        return List(count) { index ->
            val start = index * samples.size / count
            val end = max(start + 1, (index + 1) * samples.size / count).coerceAtMost(samples.size)
            var peak = 0f
            for (i in start until end) peak = max(peak, kotlin.math.abs(samples[i]))
            peak.coerceIn(0f, 1f)
        }
    }

    private fun highestPowerOfTwoAtMost(value: Int): Int {
        var result = 1
        while (result <= value / 2) result = result shl 1
        return result
    }

    private fun ceilFrames(durationMs: Long, sampleRate: Int): Long =
        (durationMs / 1_000L) * sampleRate +
            ((durationMs % 1_000L) * sampleRate + 999L) / 1_000L
}
