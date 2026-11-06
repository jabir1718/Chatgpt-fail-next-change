package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineWhisperTranscriberTest {
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
}
