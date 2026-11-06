package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAudioStorageEstimatorTest {
    @Test
    fun estimatesDiskPcmForEachUniqueReferencedAssetAndEncodedOutput() {
        val audio = MediaAsset(id = "a", uri = "memory://a", displayName = "A", kind = MediaKind.AUDIO, durationMs = 2_000L)
        val video = MediaAsset(id = "v", uri = "memory://v", displayName = "V", kind = MediaKind.VIDEO, durationMs = 3_000L)
        val videoClip = TimelineClip(
            mediaId = "v", kind = MediaKind.VIDEO, timelineStartMs = 0L, durationMs = 3_000L,
            sourceStartMs = 0L, sourceEndMs = 3_000L
        )
        val project = EditorProject(
            assets = listOf(audio, video),
            tracks = listOf(TimelineTrack(id = "video-track", name = "Video", kind = TrackKind.VIDEO, clips = listOf(videoClip)))
        )
        val estimate = OfflineAudioStorageEstimator.estimate(
            project, listOf("a", "v", "a"), sampleRate = 8_000, channels = 1, bitRate = 64_000, safetyReserveBytes = 100L
        )
        assertEquals(160_000L, estimate.decodedPcmBytes)
        assertEquals(24_000L, estimate.encodedAudioBytes)
        assertEquals(100L, estimate.safetyReserveBytes)
        assertEquals(184_100L, estimate.requiredFreeBytes)
    }

    @Test
    fun rejectsMissingAssetReferences() {
        val project = EditorProject()
        val error = runCatching { OfflineAudioStorageEstimator.estimate(project, listOf("missing")) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
