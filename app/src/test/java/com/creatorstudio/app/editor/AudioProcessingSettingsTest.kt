package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioProcessingSettingsTest {
    @Test
    fun defaultsAreNeutralAndFiveBand() {
        val settings = AudioProcessingSettings()
        assertEquals(List(5) { 0f }, settings.eqGainsDb)
        assertEquals(0f, settings.bassBoostDb)
        assertEquals(0f, settings.compressorAmount)
    }

    @Test
    fun acceptsConfiguredProcessingValues() {
        val settings = AudioProcessingSettings(listOf(-12f, -6f, 0f, 6f, 12f), 8f, 0.75f)
        assertEquals(8f, settings.bassBoostDb)
        assertEquals(0.75f, settings.compressorAmount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncorrectEqBandCount() { AudioProcessingSettings(eqGainsDb = listOf(0f, 0f)) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeEqGain() { AudioProcessingSettings(eqGainsDb = listOf(13f, 0f, 0f, 0f, 0f)) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeCompressorAmount() { AudioProcessingSettings(compressorAmount = 1.1f) }
}
