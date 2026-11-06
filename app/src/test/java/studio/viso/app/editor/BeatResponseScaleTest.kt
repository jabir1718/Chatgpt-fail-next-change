package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatResponseScaleTest {
    @Test
    fun disabledLayerNeverScales() {
        assertEquals(1f, BeatResponseSettings(enabled = false, scaleAmount = 0.5f).scaleForPulse(1f), 0.0001f)
    }

    @Test
    fun enabledLayerUsesPulseScaleAndSensitivity() {
        val settings = BeatResponseSettings(enabled = true, scaleAmount = 0.2f, sensitivity = 1.5f)
        assertEquals(1f, settings.scaleForPulse(0f), 0.0001f)
        assertEquals(1.3f, settings.scaleForPulse(1f), 0.0001f)
    }

    @Test
    fun pulseInputAndMaximumScaleAreBounded() {
        val settings = BeatResponseSettings(enabled = true, scaleAmount = 0.5f, sensitivity = 2f)
        assertEquals(1f, settings.scaleForPulse(-1f), 0.0001f)
        assertEquals(2f, settings.scaleForPulse(2f), 0.0001f)
    }
}
