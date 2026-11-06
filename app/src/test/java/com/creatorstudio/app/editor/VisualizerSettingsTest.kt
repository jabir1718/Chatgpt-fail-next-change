package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerSettingsTest {
    @Test
    fun acceptsAllImplementedVisualizerModes() {
        listOf("Spectrum", "Radial", "Waveform", "Rings", "Particles", "3D Orb").forEach { mode ->
            assertEquals(mode, VisualizerSettings(mode = mode).mode)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnimplementedVisualizerMode() {
        VisualizerSettings(mode = "unsupported")
    }
    @Test
    fun foregroundBeatMultiplierMatchesLiveAndOfflinePulseEnvelope() {
        assertEquals(1f, foregroundBeatMultiplier(1.25f, 0f), 0.0001f)
        assertEquals(1.125f, foregroundBeatMultiplier(1.25f, 0.5f), 0.0001f)
        assertEquals(1.25f, foregroundBeatMultiplier(1.25f, 1f), 0.0001f)
    }

    @Test
    fun foregroundBeatMultiplierClampsPulseRange() {
        assertEquals(1f, foregroundBeatMultiplier(1.2f, -1f), 0.0001f)
        assertEquals(1.2f, foregroundBeatMultiplier(1.2f, 2f), 0.0001f)
    }

}
