package com.creatorstudio.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformAccumulatorTest {
    @Test fun reducesPcmToPeakBuckets() {
        val values = WaveformAccumulator.fromMonoSamples(floatArrayOf(0.1f, -0.8f, 0.2f, 0.3f, -0.5f, 0.1f, 0.4f, 0.2f), 2)
        assertEquals(1f, values[0], 0.0001f)
        assertEquals(0.625f, values[1], 0.0001f)
    }

    @Test fun silenceAndEmptyInputAreSafe() {
        assertTrue(WaveformAccumulator.fromMonoSamples(floatArrayOf(), 16).all { it == 0f })
        assertTrue(WaveformAccumulator.fromMonoSamples(FloatArray(32), 8).all { it == 0f })
    }

    @Test fun supportsChunkedSamplesAndClampsPeaks() {
        val reducer = WaveformAccumulator(4L, 4)
        reducer.addMonoSamples(floatArrayOf(-2f, 0.25f, 0.5f, 2f), 1, 2)
        reducer.addMonoSamples(floatArrayOf(0.75f, 0f))
        val result = reducer.finish(48_000, 1)
        assertEquals(1f / 3f, result.samples[0], 0.0001f)
        assertEquals(2f / 3f, result.samples[1], 0.0001f)
        assertEquals(1f, result.samples[2], 0.0001f)
    }
}
