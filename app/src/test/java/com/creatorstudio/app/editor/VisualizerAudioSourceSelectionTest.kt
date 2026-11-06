package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerAudioSourceSelectionTest {
    @Test
    fun includesAudioAndVideoClipAssetsOnce() {
        val project = EditorProject(
            assets = listOf(
                MediaAsset(id = "song", uri = "file:///song.mp3", displayName = "Song", kind = MediaKind.AUDIO),
                MediaAsset(id = "movie", uri = "file:///movie.mp4", displayName = "Movie", kind = MediaKind.VIDEO),
                MediaAsset(id = "still", uri = "file:///still.png", displayName = "Still", kind = MediaKind.IMAGE)
            ),
            tracks = listOf(
                TimelineTrack(
                    name = "Audio",
                    kind = TrackKind.AUDIO,
                    clips = listOf(
                        TimelineClip(mediaId = "song", kind = MediaKind.AUDIO, timelineStartMs = 0, durationMs = 1000),
                        TimelineClip(mediaId = "song", kind = MediaKind.AUDIO, timelineStartMs = 1000, durationMs = 1000)
                    )
                ),
                TimelineTrack(
                    name = "Video",
                    kind = TrackKind.VIDEO,
                    clips = listOf(TimelineClip(mediaId = "movie", kind = MediaKind.VIDEO, timelineStartMs = 0, durationMs = 2000))
                ),
                TimelineTrack(
                    name = "Overlay",
                    kind = TrackKind.OVERLAY,
                    clips = listOf(TimelineClip(mediaId = "still", kind = MediaKind.IMAGE, timelineStartMs = 0, durationMs = 2000))
                )
            )
        )

        assertEquals(listOf("song", "movie"), visualizerAudioSourceAssetIds(project))
    }

    @Test
    fun ignoresVideoClipsOnNonVideoTracks() {
        val project = EditorProject(
            assets = listOf(MediaAsset(id = "movie", uri = "file:///movie.mp4", displayName = "Movie", kind = MediaKind.VIDEO)),
            tracks = listOf(
                TimelineTrack(
                    name = "Overlay",
                    kind = TrackKind.OVERLAY,
                    clips = listOf(TimelineClip(mediaId = "movie", kind = MediaKind.VIDEO, timelineStartMs = 0, durationMs = 1000))
                )
            )
        )

        assertEquals(emptyList<String>(), visualizerAudioSourceAssetIds(project))
    }
}
