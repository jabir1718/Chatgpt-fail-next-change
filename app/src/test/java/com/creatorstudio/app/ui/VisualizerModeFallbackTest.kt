package com.creatorstudio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerModeFallbackTest {
    @Test
    fun keepsRequestedModeWhenOpenGles2IsAvailable() {
        assertEquals("3D Orb", resolveVisualizerMode("3D Orb", supportsGles2 = true))
        assertEquals("Radial", resolveVisualizerMode("Radial", supportsGles2 = false))
    }

    @Test
    fun fallsBackToSpectrumWhenOpenGles2IsUnavailable() {
        assertEquals("Spectrum", resolveVisualizerMode("3D Orb", supportsGles2 = false))
    }
}
