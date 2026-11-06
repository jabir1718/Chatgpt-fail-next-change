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
}
