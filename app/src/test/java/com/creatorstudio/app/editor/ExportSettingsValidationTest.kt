package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsValidationTest {
    @Test
    fun defaultExportSettingsArePortrait30FpsAndEightMbps() {
        val project = EditorProject()

        assertEquals(1080, project.width)
        assertEquals(1920, project.height)
        assertEquals(30, project.frameRate)
        assertEquals(8_000_000, project.videoBitRate)
    }

    @Test
    fun supportedFrameRatesAreAccepted() {
        listOf(24, 30, 60).forEach { fps ->
            assertEquals(fps, EditorProject(frameRate = fps).frameRate)
        }
    }

    @Test
    fun configuredVideoBitratesAreAccepted() {
        listOf(4_000_000, 8_000_000, 12_000_000, 20_000_000).forEach { rate ->
            assertEquals(rate, EditorProject(videoBitRate = rate).videoBitRate)
        }
    }

    @Test
    fun frameRateOutsideEncoderRangeIsRejected() {
        assertTrue(runCatching { EditorProject(frameRate = 0) }.isFailure)
        assertTrue(runCatching { EditorProject(frameRate = 61) }.isFailure)
    }

    @Test
    fun videoBitrateOutsideSafeRangeIsRejected() {
        assertTrue(runCatching { EditorProject(videoBitRate = 249_999) }.isFailure)
        assertTrue(runCatching { EditorProject(videoBitRate = 80_000_001) }.isFailure)
    }

    @Test
    fun exportSettingsSurviveProjectCopies() {
        val project = EditorProject(
            width = 1080,
            height = 1350,
            frameRate = 60,
            videoBitRate = 20_000_000
        )
        val copied = project.copy(name = "Preset test")

        assertEquals(1080, copied.width)
        assertEquals(1350, copied.height)
        assertEquals(60, copied.frameRate)
        assertEquals(20_000_000, copied.videoBitRate)
    }
}
