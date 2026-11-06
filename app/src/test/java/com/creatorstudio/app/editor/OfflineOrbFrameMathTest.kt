package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineOrbFrameMathTest {
    private fun frame(timestampMs: Long = 0L, pulse: Float = 0f, bands: List<Float> = List(32) { 0.5f }) =
        OfflineVisualizerFrameData(
            frameIndex = 0L,
            timestampMs = timestampMs,
            rms = 0.2f,
            spectrumBands = bands,
            waveform = emptyList(),
            beatDetected = pulse > 0f,
            beatPulse = pulse
        )

    @Test
    fun beatPulseExpandsOrbRadiusWithinExpectedBounds() {
        val calm = OfflineOrbFrameMath.geometry(frame(), 1080, 1920)
        val beat = OfflineOrbFrameMath.geometry(frame(pulse = 1f), 1080, 1920)
        assertTrue(beat.radiusPx > calm.radiusPx)
        assertEquals(1080f * 0.19f * 1.13f, beat.radiusPx, 0.01f)
    }

    @Test
    fun frameTimestampProducesDeterministicOrbRotation() {
        val first = OfflineOrbFrameMath.geometry(frame(timestampMs = 3_000L), 1080, 1920)
        val repeated = OfflineOrbFrameMath.geometry(frame(timestampMs = 3_000L), 1080, 1920)
        val later = OfflineOrbFrameMath.geometry(frame(timestampMs = 6_000L), 1080, 1920)
        assertEquals(first.rotationRadians, repeated.rotationRadians, 0f)
        assertTrue(later.rotationRadians > first.rotationRadians)
        assertEquals(0.66f, first.rotationRadians, 0.0001f)
        assertEquals(1.32f, later.rotationRadians, 0.0001f)
    }

    @Test
    fun angularBandLookupMatchesShaderWrapAndClampsInvalidSamples() {
        val samples = List(32) { it / 31f }
        val source = frame(bands = samples)
        assertEquals(samples[16], OfflineOrbFrameMath.bandAtAngle(source, 0f), 0.0001f)
        assertEquals(samples[0], OfflineOrbFrameMath.bandAtAngle(source, -Math.PI.toFloat()), 0.0001f)
        assertEquals(samples[31], OfflineOrbFrameMath.bandAtAngle(source, Math.PI.toFloat() - 0.0001f), 0.0001f)
        assertEquals(samples[0], OfflineOrbFrameMath.bandAtAngle(source, Math.PI.toFloat()), 0.0001f)
        assertEquals(samples[16], OfflineOrbFrameMath.bandAtAngle(source, 8f * Math.PI.toFloat()), 0.0001f)
        assertEquals(0f, OfflineOrbFrameMath.bandAtAngle(frame(bands = listOf(Float.NaN)), 0f), 0f)
    }

    @Test
    fun invalidBandValuesAreSafelyClamped() {
        val result = OfflineOrbFrameMath.geometry(
            frame(bands = listOf(Float.NaN, -1f, 2f)), 720, 1280
        )
        assertEquals(1f / 3f, result.averageBandEnergy, 0.0001f)
    }
}
