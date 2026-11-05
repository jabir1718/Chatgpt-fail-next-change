package com.creatorstudio.app.editor

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Random-access normalized PCM source. Implementations may keep samples in memory
 * or page them from disk; the timeline mixer uses the same sampling contract.
 */
interface PcmAudioSource : Closeable {
    val sampleRate: Int
    val channelCount: Int
    val frameCount: Long

    fun sampleAt(framePosition: Double, outputChannel: Int, outputChannelCount: Int): Float

    override fun close() = Unit
}

/** Interleaved mono/stereo PCM held in memory. */
data class PcmAudioBuffer(
    override val sampleRate: Int,
    override val channelCount: Int,
    val samples: FloatArray
) : PcmAudioSource {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channelCount in 1..2) { "Only mono and stereo PCM are supported" }
        require(samples.size % channelCount == 0) { "PCM sample count must align to complete frames" }
    }

    override val frameCount: Long get() = (samples.size / channelCount).toLong()
    val durationMs: Long get() = frameCount * 1000L / sampleRate

    override fun sampleAt(framePosition: Double, outputChannel: Int, outputChannelCount: Int): Float {
        require(outputChannelCount in 1..2)
        if (framePosition < 0.0 || framePosition >= frameCount) return 0f
        val first = framePosition.toInt()
        val second = min(first + 1, (frameCount - 1L).toInt())
        val fraction = (framePosition - first).toFloat()
        fun channelSample(frame: Int): Float {
            val base = frame * channelCount
            return when {
                channelCount == 1 -> samples[base]
                outputChannelCount == 1 -> (samples[base] + samples[base + 1]) * 0.5f
                else -> samples[base + outputChannel.coerceIn(0, 1)]
            }
        }
        val a = channelSample(first)
        return a + (channelSample(second) - a) * fraction
    }
}

/**
 * Disk-backed little-endian Float32 PCM source. A small sliding cache avoids
 * loading long recordings into RAM while still making sequential timeline reads cheap.
 */
class PcmFileAudioSource(
    private val file: File,
    override val sampleRate: Int,
    override val channelCount: Int,
    private val cacheFrames: Int = 4_096
) : PcmAudioSource {
    private val randomAccess = RandomAccessFile(file, "r")
    private val cache = FloatArray(cacheFrames * channelCount)
    private var cacheStart = -1L
    private var cachedFrames = 0
    private val totalFrames: Long

    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channelCount in 1..2) { "Only mono and stereo PCM are supported" }
        require(cacheFrames > 0) { "Cache size must be positive" }
        val bytesPerFrame = channelCount.toLong() * 4L
        require(randomAccess.length() % bytesPerFrame == 0L) { "PCM file ends with an incomplete frame" }
        totalFrames = randomAccess.length() / bytesPerFrame
    }

    override val frameCount: Long get() = totalFrames
    val durationMs: Long get() = frameCount * 1000L / sampleRate

    @Synchronized
    override fun sampleAt(framePosition: Double, outputChannel: Int, outputChannelCount: Int): Float {
        check(outputChannelCount in 1..2)
        if (framePosition < 0.0 || framePosition >= frameCount || frameCount == 0L) return 0f
        val first = framePosition.toLong()
        val second = min(first + 1L, frameCount - 1L)
        val fraction = (framePosition - first).toFloat()
        val a = channelSample(first, outputChannel, outputChannelCount)
        val b = channelSample(second, outputChannel, outputChannelCount)
        return a + (b - a) * fraction
    }

    private fun channelSample(frame: Long, outputChannel: Int, outputChannelCount: Int): Float {
        ensureCached(frame)
        val base = ((frame - cacheStart).toInt()) * channelCount
        return when {
            channelCount == 1 -> cache[base]
            outputChannelCount == 1 -> (cache[base] + cache[base + 1]) * 0.5f
            else -> cache[base + outputChannel.coerceIn(0, 1)]
        }
    }

    private fun ensureCached(frame: Long) {
        if (frame >= cacheStart && frame < cacheStart + cachedFrames) return
        cacheStart = (frame / cacheFrames) * cacheFrames.toLong()
        cachedFrames = min(cacheFrames.toLong(), frameCount - cacheStart).toInt()
        val byteCount = cachedFrames * channelCount * 4
        val bytes = ByteArray(byteCount)
        randomAccess.seek(cacheStart * channelCount * 4L)
        randomAccess.readFully(bytes)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(cache, 0, cachedFrames * channelCount)
    }

    @Synchronized
    override fun close() {
        randomAccess.close()
    }
}
