package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerVideoFrameCountTest {
    @Test
    fun compositionFrameCountMatchesVisualizerAnalysisFrameCount() {
        val project = EditorProject(width = 1080, height = 1920, frameRate = 30).copy(
            tracks = listOf(TimelineTrack(
                id = "image", name = "Image", kind = TrackKind.OVERLAY,
                clips = listOf(TimelineClip(mediaId = "art", kind = MediaKind.IMAGE, timelineStartMs = 0L, durationMs = 1_250L, sourceEndMs = 1_250L))
            )),
            assets = listOf(MediaAsset(id = "art", uri = "memory://art", displayName = "Art", kind = MediaKind.IMAGE))
        )
        assertEquals(38L, RenderCompositionPlan.frameCount(project))
    }
}
