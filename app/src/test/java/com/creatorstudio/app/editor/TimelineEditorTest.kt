package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
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
    fun addTrackPreservesExistingTracksAndCreatesEmptyTypedTrack() {
        val (project) = fixture()
        val result = TimelineEditor.addTrack(project, "Overlay 2", TrackKind.OVERLAY)

        assertEquals(2, result.tracks.size)
        assertEquals("Audio", result.tracks[0].name)
        assertEquals(TrackKind.AUDIO, result.tracks[0].kind)
        assertEquals(1, result.tracks[0].clips.size)
        assertEquals("Overlay 2", result.tracks[1].name)
        assertEquals(TrackKind.OVERLAY, result.tracks[1].kind)
        assertTrue(result.tracks[1].clips.isEmpty())
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
    fun snapAlignsClipStartToNearbyClipStart() {
        val (project, clip) = fixture()
        val neighbor = clip.copy(id = "neighbor", timelineStartMs = 12_000L)
        val expanded = project.copy(tracks = project.tracks.map { it.copy(clips = listOf(clip, neighbor)) })
        assertEquals(12_000L, TimelineEditor.snapClipStart(expanded, clip.id, 11_850L, 0L))
    }

    @Test
    fun snapAlignsClipEndToPlayheadAndRespectsThreshold() {
        val (project, clip) = fixture()
        assertEquals(5_000L, TimelineEditor.snapClipStart(project, clip.id, 4_850L, 15_000L))
        assertEquals(4_850L, TimelineEditor.snapClipStart(project, clip.id, 4_850L, 15_000L, thresholdMs = 100L))
    }

    @Test
    fun snapIgnoresTheDraggedClipAndNeverReturnsNegativeStart() {
        val (project, clip) = fixture()
        assertEquals(0L, TimelineEditor.snapClipStart(project, clip.id, 100L, 0L))
    }

    @Test
    fun moveClipToCompatibleTrackPreservesClipIdentityAndSourceRange() {
        val (project, clip) = fixture()
        val destination = TimelineTrack(name = "Music 2", kind = TrackKind.AUDIO)
        val withDestination = project.copy(tracks = project.tracks + destination)

        val moved = TimelineEditor.moveClipToTrack(
            withDestination,
            clip.id,
            destination.id,
            newStartMs = 2_500L
        )

        assertTrue(moved.tracks.first { it.id == project.tracks.single().id }.clips.isEmpty())
        val relocated = moved.tracks.first { it.id == destination.id }.clips.single()
        assertEquals(clip.id, relocated.id)
        assertEquals(2_500L, relocated.timelineStartMs)
        assertEquals(clip.sourceStartMs, relocated.sourceStartMs)
        assertEquals(clip.sourceEndMs, relocated.sourceEndMs)
    }

    @Test
    fun movingClipToWrongTrackTypeIsRejected() {
        val (project, clip) = fixture()
        val videoTrack = TimelineTrack(name = "Video 2", kind = TrackKind.VIDEO)
        val withDestination = project.copy(tracks = project.tracks + videoTrack)

        val error = runCatching {
            TimelineEditor.moveClipToTrack(withDestination, clip.id, videoTrack.id)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun movingClipFromLockedTrackIsRejected() {
        val (project, clip) = fixture()
        val lockedProject = project.copy(
            tracks = project.tracks.map { it.copy(locked = true) } +
                TimelineTrack(name = "Audio 2", kind = TrackKind.AUDIO)
        )
        val destination = lockedProject.tracks.last()

        val error = runCatching {
            TimelineEditor.moveClipToTrack(lockedProject, clip.id, destination.id)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
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
    fun movingClipOnLockedTrackIsRejected() {
        val (project, clip) = fixture()
        val locked = project.copy(tracks = project.tracks.map { it.copy(locked = true) })
        val error = runCatching { TimelineEditor.moveClip(locked, clip.id, 2_000L) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun movingClipBeforeTimelineZeroIsRejected() {
        val (project, clip) = fixture()
        val error = runCatching { TimelineEditor.moveClip(project, clip.id, -1L) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun duplicateClipCreatesNewIdentityImmediatelyAfterOriginal() {
        val (project, clip) = fixture()
        val result = TimelineEditor.duplicateClip(project, clip.id)
        val clips = result.tracks.single().clips.sortedBy { it.timelineStartMs }
        assertEquals(2, clips.size)
        assertEquals(clip.id, clips[0].id)
        assertEquals(10_000L, clips[1].timelineStartMs)
        assertEquals(clip.sourceStartMs, clips[1].sourceStartMs)
        assertEquals(clip.sourceEndMs, clips[1].sourceEndMs)
        assertTrue(clip.id != clips[1].id)
    }

    @Test
    fun duplicateClipOnLockedTrackIsRejected() {
        val (project, clip) = fixture()
        val locked = project.copy(tracks = project.tracks.map { it.copy(locked = true) })
        val error = runCatching { TimelineEditor.duplicateClip(locked, clip.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun deleteClipRemovesOnlyTheTimelineClip() {
        val (project, clip, asset) = fixture()
        val result = TimelineEditor.deleteClip(project, clip.id)

        assertTrue(result.tracks.single().clips.isEmpty())
        assertEquals(asset.id, result.assets.single().id)
    }

    @Test
    fun rippleDeleteClosesGapOnSameTrackAndPreservesOtherTracks() {
        val (project, first) = fixture()
        val second = first.copy(id = "later", timelineStartMs = 12_000L)
        val otherTrack = TimelineTrack(name = "Video", kind = TrackKind.VIDEO)
        val expanded = project.copy(tracks = listOf(project.tracks.single().copy(clips = listOf(first, second)), otherTrack))

        val result = TimelineEditor.rippleDeleteClip(expanded, first.id)
        val remaining = result.tracks.first().clips.single()

        assertEquals(2_000L, remaining.timelineStartMs)
        assertEquals(second.id, remaining.id)
        assertTrue(result.tracks.last().clips.isEmpty())
    }

    @Test
    fun rippleDeleteIsRejectedOnLockedTrack() {
        val (project, clip) = fixture()
        val locked = project.copy(tracks = project.tracks.map { it.copy(locked = true) })
        val error = runCatching { TimelineEditor.rippleDeleteClip(locked, clip.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun trackMuteAndLockCanBeChangedIndependently() {
        val (project) = fixture()
        val trackId = project.tracks.single().id
        val muted = TimelineEditor.setTrackMuted(project, trackId, true)
        val locked = TimelineEditor.setTrackLocked(muted, trackId, true)

        assertTrue(locked.tracks.single().muted)
        assertTrue(locked.tracks.single().locked)
        assertEquals(1, locked.tracks.single().clips.size)
    }


    @Test
    fun clipCanvasContentModeCanBeChangedWithoutChangingTimelineOrAudioSettings() {
        val (base, original) = fixture()
        val video = MediaAsset(
            id = "video", uri = "file:///video.mp4", displayName = "video.mp4",
            kind = MediaKind.VIDEO, durationMs = 10_000L
        )
        val videoClip = TimelineClip(
            id = "video-clip", mediaId = video.id, kind = MediaKind.VIDEO,
            timelineStartMs = 2_000L, durationMs = 5_000L,
            audio = ClipAudioSettings(volume = 0.4f, playbackRate = 1.5f)
        )
        val project = base.copy(
            assets = base.assets + video,
            tracks = base.tracks + TimelineTrack(name = "Video", kind = TrackKind.VIDEO, clips = listOf(videoClip))
        )

        val changed = TimelineEditor.setClipContentMode(project, videoClip.id, CanvasContentMode.CROP)
        val updated = changed.tracks.last().clips.single()

        assertEquals(CanvasContentMode.CROP, updated.contentMode)
        assertEquals(videoClip.timelineStartMs, updated.timelineStartMs)
        assertEquals(videoClip.durationMs, updated.durationMs)
        assertEquals(videoClip.audio, updated.audio)
        assertEquals(original.id, changed.tracks.first().clips.single().id)
        assertEquals(CanvasContentMode.FIT, changed.tracks.first().clips.single().contentMode)
    }

    @Test
    fun clipBeatResponseCanBeChangedIndependentlyAndPreservesOtherClipSettings() {
        val (base, original) = fixture()
        val updated = TimelineEditor.setClipBeatResponse(base, original.id, BeatResponseSettings(true, 0.3f, 1.7f))
        val changed = updated.tracks.first().clips.single()
        assertEquals(BeatResponseSettings(true, 0.3f, 1.7f), changed.beatResponse)
        assertEquals(original.timelineStartMs, changed.timelineStartMs)
        assertEquals(original.contentMode, changed.contentMode)
        assertEquals(original.audio, changed.audio)
    }

    @Test
    fun duplicateProjectRemapsAssetTrackAndClipIds() {
        val (baseProject, clip, _) = fixture()
        val image = MediaAsset(
            uri = "content://media/image/2",
            displayName = "cover.png",
            kind = MediaKind.IMAGE,
            durationMs = 5_000L
        )
        val project = baseProject.copy(
            assets = baseProject.assets + image,
            visualizer = VisualizerSettings(backgroundAssetId = image.id, foregroundAssetId = image.id)
        )
        val copy = project.duplicateProject()
        val copiedClip = copy.tracks.single().clips.single()
        val copiedImage = copy.assets.first { it.kind == MediaKind.IMAGE }
        val copiedAudio = copy.assets.first { it.kind == MediaKind.AUDIO }

        assertTrue(project.id != copy.id)
        assertTrue(image.id != copiedImage.id)
        assertTrue(project.tracks.single().id != copy.tracks.single().id)
        assertTrue(clip.id != copiedClip.id)
        assertEquals(copiedAudio.id, copiedClip.mediaId)
        assertEquals(copiedImage.id, copy.visualizer.backgroundAssetId)
        assertEquals(copiedImage.id, copy.visualizer.foregroundAssetId)
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
    @Test
    fun setClipCaptionsSortsByStartAndPreservesCaptionIds() {
        val asset=MediaAsset(uri="content://media/video/1",displayName="clip.mp4",kind=MediaKind.VIDEO)
        val clip=TimelineClip(mediaId=asset.id,kind=MediaKind.VIDEO,timelineStartMs=0L,durationMs=10_000L)
        val project=EditorProject(assets=listOf(asset),tracks=listOf(TimelineTrack(name="Video",kind=TrackKind.VIDEO,clips=listOf(clip))))
        val later=CaptionSegment(id="later",text="Second",startMs=2_000L,endMs=3_000L)
        val earlier=CaptionSegment(id="earlier",text="First",startMs=0L,endMs=1_000L)
        val result=TimelineEditor.setClipCaptions(project,clip.id,listOf(later,earlier))
        val captions=result.tracks.single().clips.single().captions
        assertEquals(listOf("earlier","later"),captions.map { it.id })
        assertEquals("Second",captions.last().text)
    }

    @Test
    fun setClipCaptionsRejectsTimingOutsideClip() {
        val asset=MediaAsset(uri="content://media/video/2",displayName="clip.mp4",kind=MediaKind.VIDEO)
        val clip=TimelineClip(mediaId=asset.id,kind=MediaKind.VIDEO,timelineStartMs=0L,durationMs=1_000L)
        val project=EditorProject(assets=listOf(asset),tracks=listOf(TimelineTrack(name="Video",kind=TrackKind.VIDEO,clips=listOf(clip))))
        val invalid=CaptionSegment(text="Too long",startMs=500L,endMs=1_500L)
        val error=runCatching { TimelineEditor.setClipCaptions(project,clip.id,listOf(invalid)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun setClipCaptionsRejectsLockedTrack() {
        val asset=MediaAsset(uri="content://media/video/3",displayName="clip.mp4",kind=MediaKind.VIDEO)
        val clip=TimelineClip(mediaId=asset.id,kind=MediaKind.VIDEO,timelineStartMs=0L,durationMs=1_000L)
        val project=EditorProject(assets=listOf(asset),tracks=listOf(TimelineTrack(name="Video",kind=TrackKind.VIDEO,locked=true,clips=listOf(clip))))
        val error=runCatching { TimelineEditor.setClipCaptions(project,clip.id,listOf(CaptionSegment(text="Locked",startMs=0L,endMs=500L))) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

}
