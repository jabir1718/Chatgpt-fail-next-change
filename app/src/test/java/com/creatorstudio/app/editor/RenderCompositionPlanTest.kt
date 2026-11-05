package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderCompositionPlanTest {
    private fun project(): EditorProject {
        val assets = listOf(
            MediaAsset(id = "video", uri = "file:///video.mp4", displayName = "video", kind = MediaKind.VIDEO, durationMs = 3000L),
            MediaAsset(id = "audio", uri = "file:///audio.mp3", displayName = "audio", kind = MediaKind.AUDIO, durationMs = 3000L),
            MediaAsset(id = "background", uri = "file:///bg.png", displayName = "background", kind = MediaKind.IMAGE),
            MediaAsset(id = "foreground", uri = "file:///fg.png", displayName = "foreground", kind = MediaKind.IMAGE),
            MediaAsset(id = "overlay", uri = "file:///overlay.png", displayName = "overlay", kind = MediaKind.IMAGE)
        )
        val videoClip = TimelineClip(
            id = "video-clip", mediaId = "video", kind = MediaKind.VIDEO,
            timelineStartMs = 0L, durationMs = 2000L, sourceEndMs = 2000L,
            audio = ClipAudioSettings(playbackRate = 2f),
            captions = listOf(CaptionSegment(id = "line", text = "Hello", startMs = 400L, endMs = 700L))
        )
        val audioClip = TimelineClip(
            id = "audio-clip", mediaId = "audio", kind = MediaKind.AUDIO,
            timelineStartMs = 0L, durationMs = 2000L, sourceEndMs = 2000L
        )
        val imageClip = TimelineClip(
            id = "image-clip", mediaId = "overlay", kind = MediaKind.IMAGE,
            timelineStartMs = 500L, durationMs = 1000L, sourceEndMs = 1000L
        )
        return EditorProject(
            width = 1080, height = 1920, frameRate = 30,
            assets = assets,
            tracks = listOf(
                TimelineTrack(id = "video-track", name = "Video", kind = TrackKind.VIDEO, clips = listOf(videoClip)),
                TimelineTrack(id = "audio-track", name = "Audio", kind = TrackKind.AUDIO, clips = listOf(audioClip)),
                TimelineTrack(id = "overlay-track", name = "Overlay", kind = TrackKind.OVERLAY, clips = listOf(imageClip))
            ),
            visualizer = VisualizerSettings(backgroundAssetId = "background", foregroundAssetId = "foreground", foregroundScale = 1.25f)
        )
    }

    @Test
    fun frameCountRoundsUpToCoverFinalPartialFrame() {
        assertEquals(60L, RenderCompositionPlan.frameCount(project()))
    }

    @Test
    fun frameSnapshotIncludesOrderedLayersAndOverlappingAudio() {
        val frame = RenderCompositionPlan.frameAt(project(), 15L)
        assertEquals(500L, frame.timestampMs)
        assertEquals(1080, frame.width)
        assertEquals(1920, frame.height)
        assertEquals(
            listOf(RenderLayerRole.VISUALIZER_BACKGROUND, RenderLayerRole.VIDEO, RenderLayerRole.IMAGE, RenderLayerRole.VISUALIZER_FOREGROUND),
            frame.layers.map { it.role }
        )
        assertEquals(2, frame.audio.size)
        assertEquals(1000L, frame.layers.first { it.role == RenderLayerRole.VIDEO }.sourcePositionMs)
        assertEquals(1.25f, frame.layers.last().transform.scaleX)
    }

    @Test
    fun captionsAreSelectedAtFrameTimeUsingClipLocalTimestamps() {
        val frame = RenderCompositionPlan.frameAt(project(), 15L)
        val video = frame.layers.first { it.role == RenderLayerRole.VIDEO }
        assertEquals(listOf("Hello"), video.activeCaptions.map { it.text })
    }

    @Test
    fun rejectsFrameOutsideProject() {
        try {
            RenderCompositionPlan.frameAt(project(), 60L)
            throw AssertionError("Expected out-of-range frame to fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("outside"))
        }
    }

    @Test
    fun overlappingVideoClipsKeepTrackOrderAndIndependentSourcePositions() {
        val lower = MediaAsset(id = "lower-video", uri = "file:///lower.mp4", displayName = "Lower", kind = MediaKind.VIDEO)
        val upper = MediaAsset(id = "upper-video", uri = "file:///upper.mp4", displayName = "Upper", kind = MediaKind.VIDEO)
        val lowerClip = TimelineClip(
            id = "lower-clip", mediaId = lower.id, kind = MediaKind.VIDEO,
            timelineStartMs = 0L, durationMs = 2_000L, sourceStartMs = 100L, sourceEndMs = 2_100L,
            audio = ClipAudioSettings(playbackRate = 2f),
            transform = Transform(scaleX = 0.8f, scaleY = 0.8f, opacity = 0.9f)
        )
        val upperClip = TimelineClip(
            id = "upper-clip", mediaId = upper.id, kind = MediaKind.VIDEO,
            timelineStartMs = 500L, durationMs = 1_500L, sourceStartMs = 300L, sourceEndMs = 1_800L,
            audio = ClipAudioSettings(playbackRate = 0.5f),
            transform = Transform(x = 24f, rotationDegrees = 12f)
        )
        val project = EditorProject(
            frameRate = 20,
            assets = listOf(lower, upper),
            tracks = listOf(
                TimelineTrack(id = "lower-track", name = "Lower", kind = TrackKind.VIDEO, clips = listOf(lowerClip)),
                TimelineTrack(id = "upper-track", name = "Upper", kind = TrackKind.VIDEO, clips = listOf(upperClip))
            )
        )

        val frame = RenderCompositionPlan.frameAt(project, 15L)
        val videos = frame.layers.filter { it.role == RenderLayerRole.VIDEO }

        assertEquals(listOf("lower-clip", "upper-clip"), videos.map { it.clipId })
        assertEquals(listOf(0, 1), videos.map { it.zOrder })
        assertEquals(listOf(1_600L, 425L), videos.map { it.sourcePositionMs })
        assertEquals(0.8f, videos.first().transform.scaleX)
        assertEquals(24f, videos.last().transform.x)
        assertEquals(12f, videos.last().transform.rotationDegrees)
    }

    @Test
    fun overlappingVideoClipsAreExcludedAtTheirExclusiveTimelineEnd() {
        val video = MediaAsset(id = "video", uri = "file:///video.mp4", displayName = "Video", kind = MediaKind.VIDEO)
        val clip = TimelineClip(
            id = "video-clip", mediaId = video.id, kind = MediaKind.VIDEO,
            timelineStartMs = 0L, durationMs = 1_000L
        )
        val project = EditorProject(
            frameRate = 20,
            assets = listOf(video),
            tracks = listOf(TimelineTrack(name = "Video", kind = TrackKind.VIDEO, clips = listOf(clip)))
        )

        val frameAtEnd = RenderCompositionPlan.frameAt(project, 20L)

        assertTrue(frameAtEnd.layers.none { it.role == RenderLayerRole.VIDEO })
    }

    @Test
    fun frameCountIsZeroForEmptyProject() {
        assertFalse(RenderCompositionPlan.frameCount(EditorProject()) > 0L)
    }
}
