package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class OfflineVisualizerAnalyzerTest {
    private fun project(durationMs: Long = 1_000L, frameRate: Int = 30): EditorProject {
        val asset = MediaAsset(id = "audio", uri = "memory://audio", displayName = "Tone", kind = MediaKind.AUDIO, durationMs = durationMs)
        val clip = TimelineClip(
            id = "audio-clip", mediaId = asset.id, kind = MediaKind.AUDIO,
            timelineStartMs = 0L, durationMs = durationMs, sourceStartMs = 0L, sourceEndMs = durationMs
        )
        return EditorProject(assets = listOf(asset), frameRate = frameRate, tracks = listOf(
            TimelineTrack(id = "audio-track", name = "Audio", kind = TrackKind.AUDIO, clips = listOf(clip))
        ))
    }

    @Test
    fun emitsOneAudioReactiveSnapshotPerVideoFrame() {
        val project = project()
        val samples = FloatArray(48_000) { index -> (0.5 * sin(2.0 * PI * 440.0 * index / 48_000.0)).toFloat() }
        val result = OfflineVisualizerAnalyzer.analyze(project, mapOf("audio" to PcmAudioBuffer(48_000, 1, samples)))
        assertEquals(30, result.frames.size)
        assertTrue(result.frames.any { it.rms > 0.1f })
        assertTrue(result.frames.any { it.spectrumBands.any { band -> band > 0f } })
        assertEquals(64, result.frames.first().waveform.size)
    }

    @Test
    fun silenceProducesZeroEnergyAndNoBeat() {
        val project = project(durationMs = 500L)
        val result = OfflineVisualizerAnalyzer.analyze(project, mapOf("audio" to PcmAudioBuffer(48_000, 1, FloatArray(24_000))))
        assertEquals(15, result.frames.size)
        assertTrue(result.frames.all { it.rms == 0f && !it.beatDetected })
    }

    @Test
    fun emptyProjectProducesNoAnalysisFrames() {
        val result = OfflineVisualizerAnalyzer.analyze(EditorProject(), emptyMap())
        assertTrue(result.frames.isEmpty())
        assertEquals(null, result.detectedBpm)
    }
}
