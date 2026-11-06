package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptionStyleTest {
    @Test
    fun legacyCaptionDefaultsRemainReadable() {
        val caption = CaptionSegment(text = "Hello", startMs = 0L, endMs = 1_000L)
        assertEquals(-1, caption.textColorArgb)
        assertEquals(0xB3000000.toInt(), caption.backgroundColorArgb)
        assertEquals(16f, caption.fontSizeSp)
        assertEquals(CaptionFontFamily.SANS, caption.fontFamily)
        assertEquals(true, caption.isBold)
        assertEquals(false, caption.isItalic)
        assertEquals(CaptionAlignment.CENTER, caption.alignment)
        assertEquals(84f, caption.verticalPositionPercent)
        assertEquals(0xFF000000.toInt(), caption.outlineColorArgb)
        assertEquals(0f, caption.outlineWidthSp)
        assertEquals(0.65f, caption.shadowStrength)
    }

    @Test
    fun styleCanBeChangedWithoutLosingCaptionTimingOrKaraokeWords() {
        val word = CaptionWordTiming("Hello", 100L, 400L)
        val caption = CaptionSegment(text = "Hello world", startMs = 0L, endMs = 1_000L, wordTimings = listOf(word))
        val styled = caption.copy(textColorArgb = 0xFFFFD54F.toInt(), backgroundColorArgb = 0, fontSizeSp = 24f, fontFamily = CaptionFontFamily.SERIF, isBold = false, isItalic = true)
        assertEquals(caption.startMs, styled.startMs)
        assertEquals(caption.endMs, styled.endMs)
        assertEquals(caption.wordTimings, styled.wordTimings)
        assertEquals(0xFFFFD54F.toInt(), styled.textColorArgb)
        assertEquals(0, styled.backgroundColorArgb)
        assertEquals(24f, styled.fontSizeSp)
        assertEquals(CaptionFontFamily.SERIF, styled.fontFamily)
        assertEquals(false, styled.isBold)
        assertEquals(true, styled.isItalic)
        assertEquals(CaptionAlignment.CENTER, styled.alignment)
    }

    @Test
    fun alignmentCanBeChangedIndependentlyOfCaptionContent() {
        val caption = CaptionSegment(text = "Align me", startMs = 0L, endMs = 1_000L)
        val aligned = caption.copy(alignment = CaptionAlignment.RIGHT)
        assertEquals(CaptionAlignment.RIGHT, aligned.alignment)
        assertEquals(caption.text, aligned.text)
        assertEquals(caption.startMs, aligned.startMs)
        assertEquals(caption.endMs, aligned.endMs)
    }

    @Test
    fun outlineAndShadowCanBeAdjustedWithoutChangingCaptionTiming() {
        val caption = CaptionSegment(text = "Readable", startMs = 100L, endMs = 900L)
        val styled = caption.copy(outlineColorArgb = 0xFFFF3EA5.toInt(), outlineWidthSp = 3.5f, shadowStrength = 0.2f)
        assertEquals(caption.startMs, styled.startMs)
        assertEquals(caption.endMs, styled.endMs)
        assertEquals(0xFFFF3EA5.toInt(), styled.outlineColorArgb)
        assertEquals(3.5f, styled.outlineWidthSp)
        assertEquals(0.2f, styled.shadowStrength)
    }

    @Test
    fun rejectsInvalidOutlineAndShadowValues() {
        assertThrows(IllegalArgumentException::class.java) { CaptionSegment(text = "Bad", startMs = 0L, endMs = 100L, outlineWidthSp = 7f) }
        assertThrows(IllegalArgumentException::class.java) { CaptionSegment(text = "Bad", startMs = 0L, endMs = 100L, shadowStrength = Float.NaN) }
    }

    @Test
    fun rejectsCaptionFontSizesOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptionSegment(text = "Too large", startMs = 0L, endMs = 1_000L, fontSizeSp = 60f)
        }
    }
}
