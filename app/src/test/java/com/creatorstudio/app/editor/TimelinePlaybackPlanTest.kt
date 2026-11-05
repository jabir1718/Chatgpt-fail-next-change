package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePlaybackPlanTest {
    @Test
    fun planIncludesEveryOverlappingClipAcrossTracks() {
        val audio = MediaAsset(uri = "content://audio/1", displayName = "music.mp3", kind = MediaKind.AUDIO)
        val video = MediaAsset(uri = "content://video/1", displayName = "scene.mp4", kind = MediaKind.VIDEO)
        val audioClip = TimelineClip(mediaId = audio.id, kind = MediaKind.AUDIO, timelineStartMs = 1_000L, durationMs = 8_000L)
        val videoClip = TimelineClip(mediaId = video.id, kind = MediaKind.VIDEO, timelineStartMs = 3_000L, durationMs = 5_000L)
        val audioTrack = TimelineTrack(name = "Music", kind = TrackKind.AUDIO, clips = listOf(audioClip))
        val videoTrack = TimelineTrack(name = "Video", kind = TrackKind.VIDEO, clips = listOf(videoClip))
        val project = EditorProject(assets = listOf(audio, video), tracks = listOf(videoTrack, audioTrack))

        val plan = TimelinePlaybackPlan.at(project, 4_500L)

        assertEquals(2, plan.activeClips.size)
        assertEquals(2, plan.audioClips.size) // Music plus embedded video audio.
        assertEquals(1, plan.videoClips.size)
        assertEquals(3_500L, plan.audioClips.first { it.clip.id == audioClip.id }.sourcePositionMs)
        assertEquals(1_500L, plan.audioClips.first { it.clip.id == videoClip.id }.sourcePositionMs)
        assertEquals(1_500L, plan.videoClips.single().sourcePositionMs)
    }

    @Test
    fun planExcludesClipsBeforeStartAndAtExclusiveEnd() {
        val asset = MediaAsset(uri = "content://audio/2", displayName = "voice.mp3", kind = MediaKind.AUDIO)
        val clip = TimelineClip(mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 2_000L, durationMs = 4_000L)
        val project = EditorProject(
            assets = listOf(asset),
            tracks = listOf(TimelineTrack(name = "Voice", kind = TrackKind.AUDIO, clips = listOf(clip)))
        )

        assertTrue(TimelinePlaybackPlan.at(project, 1_999L).activeClips.isEmpty())
        assertTrue(TimelinePlaybackPlan.at(project, 6_000L).activeClips.isEmpty())
        assertEquals(1, TimelinePlaybackPlan.at(project, 5_999L).activeClips.size)
    }

    @Test
    fun mutedTrackAndMutedClipAreExcludedFromAudioMix() {
        val asset = MediaAsset(uri = "content://audio/3", displayName = "fx.mp3", kind = MediaKind.AUDIO)
        val audible = TimelineClip(mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 0L, durationMs = 5_000L)
        val clipMuted = audible.copy(id = "clip-muted", audio = audible.audio.copy(muted = true))
        val trackMuted = TimelineTrack(name = "Muted", kind = TrackKind.AUDIO, muted = true, clips = listOf(clipMuted))
        val trackLive = TimelineTrack(name = "Live", kind = TrackKind.AUDIO, clips = listOf(audible))
        val project = EditorProject(assets = listOf(asset), tracks = listOf(trackMuted, trackLive))

        val plan = TimelinePlaybackPlan.at(project, 2_000L)

        assertEquals(2, plan.activeClips.size)
        assertEquals(1, plan.audioClips.size)
        assertEquals(audible.id, plan.audioClips.single().clip.id)
        assertFalse(plan.activeClips.first { it.clip.id == clipMuted.id }.audible)
    }

    @Test
    fun trackGainScalesClipVolumeAndMuteAndSoloRulesAreApplied() {
        val asset = MediaAsset(uri = "content://audio/gain", displayName = "music.mp3", kind = MediaKind.AUDIO)
        val clipA = TimelineClip(mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 0L, durationMs = 5_000L,
            audio = ClipAudioSettings(volume = 0.8f))
        val clipB = clipA.copy(id = "clip-b")
        val trackA = TimelineTrack(id = "a", name = "Music", kind = TrackKind.AUDIO, gain = 0.5f, clips = listOf(clipA))
        val trackB = TimelineTrack(id = "b", name = "Voice", kind = TrackKind.AUDIO, clips = listOf(clipB))
        val project = EditorProject(assets = listOf(asset), tracks = listOf(trackA, trackB))

        val mixed = TimelinePlaybackPlan.at(project, 1_000L)
        assertEquals(0.4f, mixed.audioClips.first { it.trackId == "a" }.effectiveVolume, 0.0001f)
        assertEquals(0.8f, mixed.audioClips.first { it.trackId == "b" }.effectiveVolume, 0.0001f)

        val soloProject = project.copy(tracks = listOf(trackA.copy(solo = true), trackB))
        val soloPlan = TimelinePlaybackPlan.at(soloProject, 1_000L)
        assertEquals(1, soloPlan.audioClips.size)
        assertEquals("a", soloPlan.audioClips.single().trackId)

        val mutedSolo = soloProject.copy(tracks = listOf(trackA.copy(muted = true, solo = true), trackB))
        assertTrue(TimelinePlaybackPlan.at(mutedSolo, 1_000L).audioClips.isEmpty())
    }

    @Test
    fun trackGainAndSoloEditsReturnUpdatedTrackWithoutChangingClipSettings() {
        val asset = MediaAsset(uri = "content://audio/edit", displayName = "edit.mp3", kind = MediaKind.AUDIO)
        val clip = TimelineClip(mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 0L, durationMs = 2_000L)
        val track = TimelineTrack(id = "mix", name = "Mix", kind = TrackKind.AUDIO, clips = listOf(clip))
        val project = EditorProject(assets = listOf(asset), tracks = listOf(track))

        val gained = TimelineEditor.setTrackGain(project, "mix", 0.35f)
        val soloed = TimelineEditor.setTrackSolo(gained, "mix", true)

        assertEquals(0.35f, soloed.tracks.single().gain, 0.0001f)
        assertTrue(soloed.tracks.single().solo)
        assertEquals(1f, soloed.tracks.single().clips.single().audio.volume)
    }

    @Test
    fun imageAndTextClipsNeverEnterAudioMix() {
        val image = MediaAsset(uri = "content://image/1", displayName = "cover.png", kind = MediaKind.IMAGE)
        val text = MediaAsset(uri = "content://text/1", displayName = "caption", kind = MediaKind.TEXT)
        val imageClip = TimelineClip(mediaId = image.id, kind = MediaKind.IMAGE, timelineStartMs = 0L, durationMs = 5_000L)
        val textClip = TimelineClip(mediaId = text.id, kind = MediaKind.TEXT, timelineStartMs = 0L, durationMs = 5_000L)
        val project = EditorProject(
            assets = listOf(image, text),
            tracks = listOf(
                TimelineTrack(name = "Overlay", kind = TrackKind.OVERLAY, clips = listOf(imageClip)),
                TimelineTrack(name = "Captions", kind = TrackKind.CAPTION, clips = listOf(textClip))
            )
        )

        val plan = TimelinePlaybackPlan.at(project, 1_000L)

        assertEquals(2, plan.activeClips.size)
        assertTrue(plan.audioClips.isEmpty())
        assertTrue(plan.videoClips.isEmpty())
    }

    @Test
    fun fadeInAndFadeOutScaleEffectiveClipVolume() {
        val asset = MediaAsset(uri = "content://audio/fade", displayName = "fade.mp3", kind = MediaKind.AUDIO)
        val clip = TimelineClip(
            mediaId = asset.id,
            kind = MediaKind.AUDIO,
            timelineStartMs = 1_000L,
            durationMs = 4_000L,
            audio = ClipAudioSettings(volume = 0.8f, fadeInMs = 1_000L, fadeOutMs = 1_000L)
        )
        val project = EditorProject(
            assets = listOf(asset),
            tracks = listOf(TimelineTrack(name = "Music", kind = TrackKind.AUDIO, clips = listOf(clip)))
        )

        val start = TimelinePlaybackPlan.at(project, 1_000L).audioClips.single()
        val fadeInHalf = TimelinePlaybackPlan.at(project, 1_500L).audioClips.single()
        val middle = TimelinePlaybackPlan.at(project, 2_500L).audioClips.single()
        val fadeOutHalf = TimelinePlaybackPlan.at(project, 4_500L).audioClips.single()

        assertEquals(0f, start.effectiveVolume, 0.0001f)
        assertEquals(0.4f, fadeInHalf.effectiveVolume, 0.0001f)
        assertEquals(0.8f, middle.effectiveVolume, 0.0001f)
        assertEquals(0.4f, fadeOutHalf.effectiveVolume, 0.0001f)
    } 
    @Test
    fun sourcePositionRespectsClipPlaybackRate() {
        val asset = MediaAsset(uri = "content://audio/speed", displayName = "speed.mp3", kind = MediaKind.AUDIO)
        val doubleSpeed = TimelineClip(
            mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 1_000L, durationMs = 4_000L,
            audio = ClipAudioSettings(playbackRate = 2f)
        )
        val halfSpeed = TimelineClip(
            mediaId = asset.id, kind = MediaKind.AUDIO, timelineStartMs = 6_000L, durationMs = 4_000L,
            audio = ClipAudioSettings(playbackRate = 0.5f)
        )
        val project = EditorProject(
            assets = listOf(asset),
            tracks = listOf(TimelineTrack(name = "Speed", kind = TrackKind.AUDIO, clips = listOf(doubleSpeed, halfSpeed)))
        )

        val fast = TimelinePlaybackPlan.at(project, 2_500L).audioClips.single()
        val slow = TimelinePlaybackPlan.at(project, 7_000L).audioClips.single()

        assertEquals(3_000L, fast.sourcePositionMs)
        assertEquals(500L, slow.sourcePositionMs)
    }

}
