package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAudioMixEngineTest {
    private fun project(
        clip: TimelineClip,
        track: TimelineTrack = TimelineTrack(id = "audio-track", name = "Audio", kind = TrackKind.AUDIO, clips = listOf(clip)),
        settings: AudioProcessingSettings = AudioProcessingSettings()
    ) = EditorProject(
        assets = listOf(MediaAsset(id = "song", uri = "memory://song", displayName = "Song", kind = MediaKind.AUDIO, durationMs = 2_000L)),
        tracks = listOf(track),
        audioProcessing = settings
    )

    private fun clip(
        startMs: Long = 0L,
        durationMs: Long = 1_000L,
        volume: Float = 1f,
        muted: Boolean = false,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        playbackRate: Float = 1f
    ) = TimelineClip(
        id = "clip", mediaId = "song", kind = MediaKind.AUDIO,
        timelineStartMs = startMs, durationMs = durationMs,
        sourceStartMs = 0L, sourceEndMs = durationMs,
        audio = ClipAudioSettings(volume = volume, muted = muted, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs, playbackRate = playbackRate)
    )

    private fun source(samples: FloatArray, sampleRate: Int = 8_000) =
        PcmAudioBuffer(sampleRate, 1, samples)

    @Test
    fun mixesMonoPcmIntoStereoAtTimelinePosition() {
        val p = project(clip(startMs = 10L))
        val engine = OfflineAudioMixEngine(p, mapOf("song" to source(FloatArray(800) { 0.5f })), 8_000, 2)
        val block = engine.renderBlock(0L, 160)
        assertEquals(0f, block.samples[0], 0.0001f)
        assertEquals(0.5f, block.samples[160], 0.0001f)
        assertEquals(0.5f, block.samples[161], 0.0001f)
    }

    @Test
    fun appliesClipGainAndFadeIn() {
        val p = project(clip(volume = 0.5f, fadeInMs = 100L))
        val engine = OfflineAudioMixEngine(p, mapOf("song" to source(FloatArray(800) { 0.8f })), 8_000, 1)
        val block = engine.renderBlock(0L, 800)
        assertEquals(0f, block.samples[0], 0.0001f)
        assertEquals(0.1f, block.samples[200], 0.002f)
        assertEquals(0.4f, block.samples[799], 0.002f)
    }

    @Test
    fun respectsClipMuteTrackMuteAndSolo() {
        val pcm = source(FloatArray(800) { 0.4f })
        val mutedProject = project(clip(muted = true))
        assertEquals(0f, OfflineAudioMixEngine(mutedProject, mapOf("song" to pcm), 8_000, 1).renderBlock(0, 8).samples[2], 0.0001f)

        val soloTrack = TimelineTrack(id = "other", name = "Other", kind = TrackKind.AUDIO, solo = true)
        val excluded = project(clip(), track = TimelineTrack(id = "audio-track", name = "Audio", kind = TrackKind.AUDIO, clips = listOf(clip())))
            .copy(tracks = listOf(TimelineTrack(id = "audio-track", name = "Audio", kind = TrackKind.AUDIO, clips = listOf(clip())), soloTrack))
        assertEquals(0f, OfflineAudioMixEngine(excluded, mapOf("song" to pcm), 8_000, 1).renderBlock(0, 8).samples[2], 0.0001f)
    }

    @Test
    fun playbackRateAdvancesSourceAndFadeOutReachesSilence() {
        val p = project(clip(durationMs = 1_000L, fadeOutMs = 200L, playbackRate = 1f))
        val ramp = FloatArray(16_000) { it / 16_000f }
        val block = OfflineAudioMixEngine(p, mapOf("song" to source(ramp)), 8_000, 1).renderBlock(0L, 8_000)
        assertTrue(block.samples[4_000] > block.samples[2_000])
        assertEquals(0f, block.samples[7_999], 0.0001f)
    }


    @Test
    fun playbackRateAdvancesSourceFasterThanTimeline() {
        val p = project(clip(durationMs = 1_000L, playbackRate = 2f))
        val ramp = FloatArray(16_000) { it / 16_000f }
        val block = OfflineAudioMixEngine(p, mapOf("song" to source(ramp)), 8_000, 1).renderBlock(0L, 4_000)
        assertTrue(block.samples[2_000] > block.samples[1_000])
    }

    @Test
    fun sumsOverlappingClipsAndLimitsOutputPeak() {
        val first = clip(volume = 1f)
        val second = clip().copy(id = "clip-2", timelineStartMs = 0L)
        val p = project(first).copy(tracks = listOf(
            TimelineTrack(id = "a", name = "A", kind = TrackKind.AUDIO, clips = listOf(first)),
            TimelineTrack(id = "b", name = "B", kind = TrackKind.AUDIO, clips = listOf(second))
        ))
        val block = OfflineAudioMixEngine(p, mapOf("song" to source(FloatArray(800) { 0.8f })), 8_000, 1).renderBlock(0L, 800)
        assertEquals(0.98f, block.samples[100], 0.0001f)
    }

    @Test
    fun keepsFilterStateAcrossSequentialBlocks() {
        val settings = AudioProcessingSettings(eqGainsDb = listOf(3f, 0f, 0f, 0f, 0f))
        val p = project(clip(), settings = settings)
        val engine = OfflineAudioMixEngine(p, mapOf("song" to source(FloatArray(800) { 0.1f })), 8_000, 1)
        val first = engine.renderBlock(0L, 400)
        val second = engine.renderBlock(400L, 400)
        assertTrue(first.samples.last() > 0f)
        assertTrue(second.samples.first() > 0f)
    }
}
