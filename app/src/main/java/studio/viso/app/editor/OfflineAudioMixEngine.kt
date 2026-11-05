package com.creatorstudio.app.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decoded, normalized floating-point PCM supplied by the media decoder.
 * Samples are interleaved and must be in [-1, 1]. This buffer abstraction keeps
 * decoding separate from deterministic timeline mixing.
 */
data class PcmAudioBuffer(
    val sampleRate: Int,
    val channelCount: Int,
    val samples: FloatArray
) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channelCount in 1..2) { "Only mono and stereo PCM are supported" }
        require(samples.size % channelCount == 0) { "PCM sample count must align to complete frames" }
    }

    val frameCount: Int get() = samples.size / channelCount
    val durationMs: Long get() = frameCount.toLong() * 1000L / sampleRate
}

/** Result of one contiguous export-audio block. */
data class MixedPcmBlock(
    val startFrame: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val samples: FloatArray
)

/**
 * Offline timeline PCM mixer.
 *
 * The caller supplies decoded PCM buffers keyed by imported asset ID. Rendering is
 * block-based so a later MediaCodec decoder can feed bounded blocks without changing
 * the scheduling, interpolation, fades, gain, EQ, compressor or limiter rules.
 *
 * Create one engine per export and call renderBlock with monotonically increasing
 * startFrame values to preserve filter/compressor state between blocks.
 */
class OfflineAudioMixEngine(
    private val project: EditorProject,
    private val sources: Map<String, PcmAudioBuffer>,
    private val outputSampleRate: Int = 48_000,
    private val outputChannels: Int = 2
) {
    private val soloTrackIds = project.tracks
        .filter { it.solo && (it.kind == TrackKind.AUDIO || it.kind == TrackKind.VIDEO) }
        .mapTo(hashSetOf()) { it.id }
    private val hasSolo = soloTrackIds.isNotEmpty()
    private val eq = Array(outputChannels) { channel ->
        Array(5) { band ->
            val gain = (project.audioProcessing.eqGainsDb[band] + if (band == 0) project.audioProcessing.bassBoostDb else 0f)
                .coerceIn(-12f, 12f)
            PeakingEqState(outputSampleRate, BAND_FREQUENCIES[band], gain)
        }
    }
    private val compressorEnvelope = FloatArray(outputChannels)

    init {
        require(outputSampleRate >= 8_000) { "Output sample rate is too low" }
        require(outputChannels in 1..2) { "Only mono and stereo output are supported" }
    }

    fun renderBlock(startFrame: Long, frameCount: Int): MixedPcmBlock {
        require(startFrame >= 0L) { "Start frame cannot be negative" }
        require(frameCount >= 0) { "Frame count cannot be negative" }
        val output = FloatArray(frameCount * outputChannels)
        if (frameCount == 0) return MixedPcmBlock(startFrame, outputSampleRate, outputChannels, output)

        val audioTracks = project.tracks.filter { it.kind == TrackKind.AUDIO || it.kind == TrackKind.VIDEO }
        for (track in audioTracks) {
            val isSoloAllowed = !hasSolo || track.id in soloTrackIds
            if (track.muted || !isSoloAllowed || track.gain <= 0f) continue
            for (clip in track.clips) {
                if (clip.kind != MediaKind.AUDIO && clip.kind != MediaKind.VIDEO) continue
                if (clip.audio.muted || clip.audio.volume <= 0f) continue
                val pcm = sources[clip.mediaId] ?: continue
                val clipStartFrame = millisecondsToFrames(clip.timelineStartMs)
                val clipEndFrame = millisecondsToFrames(clip.timelineEndMs)
                val blockEnd = startFrame + frameCount
                val from = maxOf(startFrame, clipStartFrame)
                val until = minOf(blockEnd, clipEndFrame)
                if (from >= until) continue

                for (timelineFrame in from until until) {
                    val localFrame = timelineFrame - clipStartFrame
                    val elapsedMs = localFrame.toDouble() * 1000.0 / outputSampleRate
                    val sourceMs = clip.sourceStartMs + elapsedMs * clip.audio.playbackRate
                    if (sourceMs >= clip.sourceEndMs) continue
                    val sourceFrame = sourceMs * pcm.sampleRate / 1000.0
                    val fadeIn = if (clip.audio.fadeInMs > 0L) {
                        (elapsedMs / clip.audio.fadeInMs).coerceIn(0.0, 1.0)
                    } else 1.0
                    val remainingMs = clip.durationMs - elapsedMs
                    val fadeOut = if (clip.audio.fadeOutMs > 0L) {
                        (remainingMs / clip.audio.fadeOutMs).coerceIn(0.0, 1.0)
                    } else 1.0
                    val gain = clip.audio.volume * track.gain * fadeIn * fadeOut
                    val outBase = ((timelineFrame - startFrame).toInt()) * outputChannels
                    for (channel in 0 until outputChannels) {
                        output[outBase + channel] += sampleAt(pcm, sourceFrame, channel) * gain.toFloat()
                    }
                }
            }
        }

        processMaster(output)
        return MixedPcmBlock(startFrame, outputSampleRate, outputChannels, output)
    }

    private fun millisecondsToFrames(milliseconds: Long): Long =
        (milliseconds.toDouble() * outputSampleRate / 1000.0).toLong()

    private fun sampleAt(buffer: PcmAudioBuffer, position: Double, outputChannel: Int): Float {
        if (position < 0.0 || position >= buffer.frameCount) return 0f
        val first = position.toInt()
        val second = minOf(first + 1, buffer.frameCount - 1)
        val fraction = (position - first).toFloat()
        fun channelSample(frame: Int): Float {
            val base = frame * buffer.channelCount
            return if (buffer.channelCount == 1) {
                buffer.samples[base]
            } else if (outputChannels == 1) {
                (buffer.samples[base] + buffer.samples[base + 1]) * 0.5f
            } else {
                buffer.samples[base + outputChannel.coerceAtMost(1)]
            }
        }
        val a = channelSample(first)
        val b = channelSample(second)
        return a + (b - a) * fraction
    }

    private fun processMaster(samples: FloatArray) {
        val settings = project.audioProcessing
        val amount = settings.compressorAmount
        val ratio = 1.0 + amount * 7.0
        val thresholdDb = -36.0 * amount
        val threshold = 10.0.pow(thresholdDb / 20.0)
        val attack = expCoefficient(10.0, outputSampleRate)
        val release = expCoefficient(120.0, outputSampleRate)

        var index = 0
        while (index < samples.size) {
            for (channel in 0 until outputChannels) {
                var value = samples[index + channel].toDouble()
                for (band in 0 until 5) value = eq[channel][band].process(value)
                if (amount > 0f) {
                    val level = abs(value)
                    val coefficient = if (level > compressorEnvelope[channel]) attack else release
                    compressorEnvelope[channel] = (coefficient * compressorEnvelope[channel] + (1.0 - coefficient) * level).toFloat()
                    val envelope = compressorEnvelope[channel].toDouble().coerceAtLeast(1e-9)
                    if (envelope > threshold) {
                        val levelDb = 20.0 * kotlin.math.log10(envelope)
                        val reducedDb = thresholdDb + (levelDb - thresholdDb) / ratio
                        value *= 10.0.pow((reducedDb - levelDb) / 20.0)
                    }
                }
                samples[index + channel] = value.coerceIn(-0.98, 0.98).toFloat()
            }
            index += outputChannels
        }
    }

    private class PeakingEqState(sampleRate: Int, frequency: Int, gainDb: Float) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            if (gainDb == 0f || frequency >= sampleRate / 2) {
                b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            } else {
                val safeFrequency = frequency.coerceAtMost(sampleRate / 2 - 1)
                val omega = 2.0 * PI * safeFrequency / sampleRate
                val alpha = sin(omega) / 2.0
                val amplitude = 10.0.pow(gainDb / 40.0)
                val cosine = cos(omega)
                val a0 = 1.0 + alpha / amplitude
                b0 = (1.0 + alpha * amplitude) / a0
                b1 = (-2.0 * cosine) / a0
                b2 = (1.0 - alpha * amplitude) / a0
                a1 = (-2.0 * cosine) / a0
                a2 = (1.0 - alpha / amplitude) / a0
            }
        }

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = input; y2 = y1; y1 = output
            return output
        }
    }

    private fun expCoefficient(milliseconds: Double, sampleRate: Int): Double =
        kotlin.math.exp(-1.0 / (milliseconds * sampleRate / 1000.0))

    companion object {
        private val BAND_FREQUENCIES = intArrayOf(80, 250, 1_000, 4_000, 12_000)
    }
}
