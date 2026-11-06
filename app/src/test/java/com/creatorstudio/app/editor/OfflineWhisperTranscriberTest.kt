package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineWhisperTranscriberTest {
    @Test
    fun romanUrduTransliterationPreservesEnglishAndPunctuation() {
        assertEquals("salaam! Pakistan", transliterateUrduToRoman("سلام! پاکستان"))
        assertEquals("Hello, friend!", transliterateUrduToRoman("Hello, friend!"))
        assertEquals("aap kaise hain?", transliterateUrduToRoman("آپ کیسے ہیں?"))
    }

    @Test
    fun alignsSourceClipOffsetAndPreservesLanguage() {
        val chunks = listOf(
            OfflineTranscriptionChunk("Hello there", 1_000L, 4_000L, "en")
        )
        val captions = alignTranscriptionChunks(
            chunks = chunks,
            sourceTimelineStartMs = 5_000L,
            targetTimelineStartMs = 2_000L,
            targetDurationMs = 8_000L
        )
        assertEquals(1, captions.size)
        assertEquals(4_000L, captions.single().startMs)
        assertEquals(7_000L, captions.single().endMs)
        assertEquals("en", captions.single().languageTag)
    }

    @Test
    fun clipsTranscriptionToTargetBoundsAndAccountsForPlaybackRate() {
        val chunks = listOf(
            OfflineTranscriptionChunk("Before and during", 0L, 6_000L, "ur"),
            OfflineTranscriptionChunk("After", 8_000L, 10_000L, "ur")
        )
        val captions = alignTranscriptionChunks(
            chunks = chunks,
            sourceTimelineStartMs = 1_000L,
            targetTimelineStartMs = 3_000L,
            targetDurationMs = 2_000L,
            sourcePlaybackRate = 2f
        )
        assertEquals(1, captions.size)
        assertEquals(0L, captions.single().startMs)
        assertEquals(1_000L, captions.single().endMs)
        assertEquals("ur", captions.single().languageTag)
    }

    @Test
    fun dropsChunksOutsideTargetAndRejectsInvalidRate() {
        val captions = alignTranscriptionChunks(
            chunks = listOf(OfflineTranscriptionChunk("Outside", 5_000L, 6_000L, null)),
            sourceTimelineStartMs = 0L,
            targetTimelineStartMs = 0L,
            targetDurationMs = 2_000L
        )
        assertTrue(captions.isEmpty())
        try {
            alignTranscriptionChunks(emptyList(), 0L, 0L, 2_000L, 0f)
            throw AssertionError("Expected invalid playback rate to fail")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
    @Test
    fun tokenTimestampsCreateShortPunctuatedCaptionSegments() {
        val chunks = buildTokenTimedCaptionChunks(
            tokens = listOf("▁Hello", "▁there.", "▁How", "▁are", "▁you?"),
            timestampsSeconds = listOf(0f, 0.4f, 2f, 2.2f, 2.5f),
            fallbackText = "Hello there. How are you?",
            chunkStartMs = 1_000L,
            chunkEndMs = 5_000L,
            languageTag = "en"
        )
        assertEquals(2, chunks.size)
        assertEquals("Hello there.", chunks[0].text)
        assertEquals(1_000L, chunks[0].startMs)
        assertEquals(3_000L, chunks[1].startMs)
        assertEquals("How are you?", chunks[1].text)
        assertEquals("en", chunks[1].languageTag)
    }

    @Test
    fun malformedTokenTimingFallsBackToWindowCaption() {
        val chunks = buildTokenTimedCaptionChunks(
            tokens = listOf("▁not", "▁timed"),
            timestampsSeconds = listOf(0f),
            fallbackText = "Fallback sentence",
            chunkStartMs = 2_000L,
            chunkEndMs = 7_000L,
            languageTag = "ur"
        )
        assertEquals(1, chunks.size)
        assertEquals("Fallback sentence", chunks.single().text)
        assertEquals(2_000L, chunks.single().startMs)
        assertEquals(7_000L, chunks.single().endMs)
    }

    @Test
    fun tokenCaptionGroupsRespectCharacterLimitAndClampToWindow() {
        val chunks = buildTokenTimedCaptionChunks(
            tokens = listOf("▁Averylongtoken", "▁secondlongtoken", "▁thirdlongtoken"),
            timestampsSeconds = listOf(0f, 1f, 20f),
            fallbackText = "Fallback",
            chunkStartMs = 500L,
            chunkEndMs = 3_000L,
            languageTag = null,
            maxCaptionChars = 12
        )
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.startMs >= 500L && it.endMs <= 3_000L && it.endMs > it.startMs })
    }

}
