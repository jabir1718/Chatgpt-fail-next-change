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
        assertEquals(CaptionAlignment.CENTER, caption.alignment)
    }

    @Test
    fun styleCanBeChangedWithoutLosingCaptionTimingOrKaraokeWords() {
        val word = CaptionWordTiming("Hello", 100L, 400L)
        val caption = CaptionSegment(text = "Hello world", startMs = 0L, endMs = 1_000L, wordTimings = listOf(word))
        val styled = caption.copy(textColorArgb = 0xFFFFD54F.toInt(), backgroundColorArgb = 0, fontSizeSp = 24f)
        assertEquals(caption.startMs, styled.startMs)
        assertEquals(caption.endMs, styled.endMs)
        assertEquals(caption.wordTimings, styled.wordTimings)
        assertEquals(0xFFFFD54F.toInt(), styled.textColorArgb)
        assertEquals(0, styled.backgroundColorArgb)
        assertEquals(24f, styled.fontSizeSp)
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
    fun rejectsCaptionFontSizesOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptionSegment(text = "Too large", startMs = 0L, endMs = 1_000L, fontSizeSp = 60f)
        }
    }
}
