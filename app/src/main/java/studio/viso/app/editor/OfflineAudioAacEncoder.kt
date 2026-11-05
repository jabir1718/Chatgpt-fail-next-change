package com.creatorstudio.app.editor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

data class AacAudioExportResult(
    val file: File,
    val durationMs: Long,
    val byteCount: Long
)

/** Encodes block-based offline mix PCM into an AAC-LC M4A container. */
class OfflineAudioAacEncoder {
    fun encode(
        mixer: OfflineAudioMixEngine,
        totalFrames: Long,
        outputFile: File,
        bitRate: Int = 192_000,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): AacAudioExportResult {
        require(totalFrames > 0L) { "There is no timeline audio to export" }
        require(bitRate in 64_000..320_000) { "AAC bit rate must be between 64 and 320 kbps" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) {
            throw IllegalStateException("Could not replace the previous audio export")
        }

        var encoder: MediaCodec? = null
        var encoderStarted = false
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, mixer.outputSampleRate, mixer.outputChannels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderStarted = true
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerTrack = -1
            var inputEnded = false
            var outputEnded = false
            var nextFrame = 0L
            var idlePolls = 0
            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L

            while (!outputEnded) {
                if (shouldCancel()) throw CancellationException("AAC audio export cancelled")
                var madeProgress = false

                if (!inputEnded) {
                    val inputIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val input = encoder.getInputBuffer(inputIndex) ?: error("AAC encoder input buffer was unavailable")
                        input.clear()
                        input.order(ByteOrder.LITTLE_ENDIAN)
                        val remainingFrames = totalFrames - nextFrame
                        if (remainingFrames <= 0L) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, framePtsUs(totalFrames, mixer.outputSampleRate), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val frameCapacity = input.remaining() / (2 * mixer.outputChannels)
                            require(frameCapacity > 0) { "AAC encoder input buffer is too small" }
                            val frames = minOf(1_024L, remainingFrames, frameCapacity.toLong()).toInt()
                            val block = mixer.renderBlock(nextFrame, frames)
                            block.samples.forEach { input.putShort(floatToPcm16(it)) }
                            encoder.queueInputBuffer(inputIndex, 0, input.position(), framePtsUs(nextFrame, mixer.outputSampleRate), 0)
                            nextFrame += frames
                            onProgress((nextFrame.toDouble() / totalFrames * 0.95).toFloat().coerceIn(0f, 0.95f))
                        }
                        madeProgress = true
                    }
                }

                when (val outputIndex = encoder.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "AAC encoder changed output format more than once" }
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        madeProgress = true
                    }
                    else -> if (outputIndex >= 0) {
                        val encoded = encoder.getOutputBuffer(outputIndex)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted && muxerTrack >= 0) { "AAC muxer was not initialized" }
                            val buffer = encoded ?: error("AAC encoder output buffer was unavailable")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(muxerTrack, buffer, info)
                        }
                        val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) outputEnded = true
                        madeProgress = true
                    }
                }

                idlePolls = if (madeProgress) 0 else idlePolls + 1
                if (idlePolls > 1_200) throw IllegalStateException("AAC encoder timed out")
            }

            check(muxerStarted) { "AAC encoder produced no output track" }
            muxer.stop()
            muxerStarted = false
            require(outputFile.isFile && outputFile.length() > 0L) { "AAC export file is empty" }
            onProgress(1f)
            success = true
            return AacAudioExportResult(outputFile, totalFrames * 1_000L / mixer.outputSampleRate, outputFile.length())
        } finally {
            if (encoderStarted) runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!success) outputFile.delete()
        }
    }

    private fun framePtsUs(frame: Long, sampleRate: Int): Long = frame * 1_000_000L / sampleRate
}

internal fun floatToPcm16(sample: Float): Short {
    val clipped = sample.coerceIn(-1f, 1f)
    return (if (clipped < 0f) clipped * 32_768f else clipped * 32_767f).toInt().toShort()
}
