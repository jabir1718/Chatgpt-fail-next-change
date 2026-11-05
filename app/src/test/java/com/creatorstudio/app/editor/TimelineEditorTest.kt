package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditorTest {
    private data class Fixture(val project: EditorProject, val clip: TimelineClip, val asset: MediaAsset)

    private fun fixture(): Fixture {
        val asset = MediaAsset(
            uri = "content://media/audio/1",
            displayName = "demo.mp3",
            kind = MediaKind.AUDIO,
            durationMs = 10_000L
        )
        val clip = TimelineClip(
            mediaId = asset.id,
            kind = MediaKind.AUDIO,
            timelineStartMs = 0L,
            durationMs = 10_000L
        )
        val track = TimelineTrack(name = "Audio", kind = TrackKind.AUDIO, clips = listOf(clip))
        return Fixture(EditorProject(assets = listOf(asset), tracks = listOf(track)), clip, asset)
    }

    @Test
    fun splitPreservesSourceRangesAndCreatesTwoTimelineClips() {
        val (project, clip) = fixture()
        val result = TimelineEditor.split(project, clip.id, 4_000L)
        val clips = result.tracks.single().clips.sortedBy { it.timelineStartMs }

        assertEquals(2, clips.size)
        assertEquals(4_000L, clips[0].durationMs)
        assertEquals(0L, clips[0].sourceStartMs)
        assertEquals(4_000L, clips[0].sourceEndMs)
        assertEquals(4_000L, clips[1].timelineStartMs)
        assertEquals(6_000L, clips[1].durationMs)
        assertEquals(4_000L, clips[1].sourceStartMs)
        assertEquals(10_000L, clips[1].sourceEndMs)
    }

    @Test
    fun trimStartAdvancesSourceInPointAndShortensClip() {
        val (project, clip) = fixture()
        val result = TimelineEditor.trimStart(project, clip.id, 1_500L)
        val trimmed = result.tracks.single().clips.single()

        assertEquals(1_500L, trimmed.timelineStartMs)
        assertEquals(1_500L, trimmed.sourceStartMs)
        assertEquals(8_500L, trimmed.durationMs)
        assertEquals(10_000L, trimmed.sourceEndMs)
    }

    @Test
    fun trimEndShortensSourceOutPoint() {
        val (project, clip) = fixture()
        val result = TimelineEditor.trimEnd(project, clip.id, 7_000L)
        val trimmed = result.tracks.single().clips.single()

        assertEquals(7_000L, trimmed.durationMs)
        assertEquals(7_000L, trimmed.sourceEndMs)
    }

    @Test
    fun moveClipChangesTimelinePositionWithoutChangingSourceRange() {
        val (project, clip) = fixture()
        val moved = TimelineEditor.moveClip(project, clip.id, 3_000L).tracks.single().clips.single()

        assertEquals(3_000L, moved.timelineStartMs)
        assertEquals(0L, moved.sourceStartMs)
        assertEquals(10_000L, moved.sourceEndMs)
    }

    @Test
    fun deleteClipRemovesOnlyTheTimelineClip() {
        val (project, clip, asset) = fixture()
        val result = TimelineEditor.deleteClip(project, clip.id)

        assertTrue(result.tracks.single().clips.isEmpty())
        assertEquals(asset.id, result.assets.single().id)
    }

    @Test
    fun duplicateProjectRemapsAssetTrackAndClipIds() {
        val (baseProject, clip, asset) = fixture()
        val project = baseProject.copy(
            visualizer = VisualizerSettings(backgroundAssetId = asset.id, foregroundAssetId = asset.id)
        )
        val copy = project.duplicateProject()
        val copiedClip = copy.tracks.single().clips.single()

        assertNotEquals(project.id, copy.id)
        assertNotEquals(asset.id, copy.assets.single().id)
        assertNotEquals(project.tracks.single().id, copy.tracks.single().id)
        assertNotEquals(clip.id, copiedClip.id)
        assertEquals(copy.assets.single().id, copiedClip.mediaId)
        assertEquals(copy.assets.single().id, copy.visualizer.backgroundAssetId)
        assertEquals(copy.assets.single().id, copy.visualizer.foregroundAssetId)
    }

    @Test
    fun clipAudioSettingsCanBePersistedIndependently() {
        val (project, clip) = fixture()
        val result = project.updateClipAudio(clip.id) {
            it.copy(volume = 0.65f, muted = true, pitchSemitones = 3f, playbackRate = 1.25f)
        }
        val audio = result.tracks.single().clips.single().audio

        assertEquals(0.65f, audio.volume, 0.001f)
        assertTrue(audio.muted)
        assertEquals(3f, audio.pitchSemitones, 0.001f)
        assertEquals(1.25f, audio.playbackRate, 0.001f)
    }
}
