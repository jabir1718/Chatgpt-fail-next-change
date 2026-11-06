package com.creatorstudio.app.audio

data class WaveformData(val samples: List<Float>, val sampleRate: Int, val durationMs: Long) {
    init {
        require(samples.isNotEmpty() && samples.all { it.isFinite() && it in 0f..1f })
        require(sampleRate > 0 && durationMs > 0L)
    }
}

class WaveformAccumulator(private val expectedFrames: Long, bucketCount: Int) {
    private val peaks = FloatArray(bucketCount)
    private var framesSeen = 0L
    init { require(expectedFrames > 0L && bucketCount > 0) }

    fun addMonoSamples(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size)
        for (i in offset until offset + length) {
            val bucket = ((framesSeen * peaks.size) / expectedFrames).coerceIn(0L, (peaks.size - 1).toLong()).toInt()
            peaks[bucket] = maxOf(peaks[bucket], kotlin.math.abs(samples[i]).coerceIn(0f, 1f))
            framesSeen++
        }
    }

    fun finish(sampleRate: Int, durationMs: Long): WaveformData {
        val max = peaks.maxOrNull() ?: 0f
        val values = if (max <= 0.00001f) peaks.map { 0f } else peaks.map { (it / max).coerceIn(0f, 1f) }
        return WaveformData(values, sampleRate, durationMs.coerceAtLeast(1L))
    }

    companion object {
        fun fromMonoSamples(samples: FloatArray, bucketCount: Int): List<Float> {
            val count = bucketCount.coerceAtLeast(1)
            if (samples.isEmpty()) return List(count) { 0f }
            val reducer = WaveformAccumulator(samples.size.toLong(), count)
            reducer.addMonoSamples(samples)
            return reducer.finish(1, 1L).samples
        }
    }
}
