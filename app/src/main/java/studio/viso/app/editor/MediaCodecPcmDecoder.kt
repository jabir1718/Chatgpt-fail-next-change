package com.creatorstudio.app.editor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

data class PcmDecodeResult(
    val source: PcmFileAudioSource,
    val durationMs: Long,
    val sourceDurationMs: Long
)

/**
 * Decodes an imported audio URI to a disk-backed Float32 PCM file using MediaExtractor
 * and MediaCodec. The resulting source pages small blocks from disk for offline mixing.
 * This avoids retaining entire songs in the app heap.
 */
class MediaCodecPcmDecoder {
    fun decodeToFile(
        context: Context,
        uri: Uri,
        outputFile: File,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): PcmDecodeResult {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) {
            throw IllegalStateException("Could not replace previous PCM decode file")
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var codecStarted = false
        var output: BufferedOutputStream? = null
        var success = false
        var decodedRate = 0
        var decodedChannels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var sourceDurationMs = 0L
        var totalFrames = 0L
        val floatChunk = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)

        fun flushFloatChunk() {
            val stream = output ?: error("Decoder output format was not initialized")
            if (floatChunk.position() > 0) {
                stream.write(floatChunk.array(), 0, floatChunk.position())
                floatChunk.clear()
            }
        }

        fun writeFloat(value: Float) {
            if (floatChunk.remaining() < 4) flushFloatChunk()
            floatChunk.putFloat(value.coerceIn(-1f, 1f))
        }

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    audioFormat = candidate
                    break
                }
            }
            require(audioTrack >= 0 && audioFormat != null) { "No decodable audio track was found" }
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            sourceDurationMs = audioFormat.optLong(MediaFormat.KEY_DURATION, 0L) / 1_000L
            extractor.selectTrack(audioTrack)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()
            codecStarted = true

            var inputEnded = false
            var outputEnded = false
            var idlePolls = 0
            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L

            while (!outputEnded) {
                if (shouldCancel()) throw CancellationException("Audio decode cancelled")
                var madeProgress = false

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: error("Decoder input buffer was unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
                            if (sourceDurationMs > 0L) {
                                onProgress((presentationTimeUs / (sourceDurationMs * 1_000.0)).toFloat().coerceIn(0f, 0.9f))
                            }
                            extractor.advance()
                        }
                        madeProgress = true
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        decodedRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        decodedChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        require(decodedRate > 0) { "Decoder returned an invalid sample rate" }
                        require(decodedChannels in 1..2) { "Offline mixer currently supports mono or stereo source audio" }
                        require(
                            pcmEncoding == AudioFormat.ENCODING_PCM_16BIT ||
                                pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                        ) { "Decoder returned an unsupported PCM sample format: $pcmEncoding" }
                        output = BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024)
                        madeProgress = true
                    }
                    else -> if (outputIndex >= 0) {
                        val decoded = codec.getOutputBuffer(outputIndex)
                        if (info.size > 0) {
                            if (output == null) {
                                val format = codec.outputFormat
                                decodedRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                decodedChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                } else AudioFormat.ENCODING_PCM_16BIT
                                require(decodedRate > 0 && decodedChannels in 1..2)
                                require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT || pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT)
                                output = BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024)
                            }
                            if (decoded != null) {
                                val data = decoded.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
                                    position(info.offset)
                                    limit(info.offset + info.size)
                                }.slice().order(ByteOrder.LITTLE_ENDIAN)
                                when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_16BIT -> {
                                        val usableBytes = data.remaining() - data.remaining() % (2 * decodedChannels)
                                        data.limit(data.position() + usableBytes)
                                        while (data.remaining() >= 2) {
                                            writeFloat(data.short / 32768f)
                                            if (floatChunk.position() == 0) totalFrames += 0L
                                        }
                                    }
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        val usableBytes = data.remaining() - data.remaining() % (4 * decodedChannels)
                                        data.limit(data.position() + usableBytes)
                                        while (data.remaining() >= 4) writeFloat(data.float)
                                    }
                                }
                                totalFrames = outputFile.length() / (decodedChannels * 4L)
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                        codec.releaseOutputBuffer(outputIndex, false)
                        madeProgress = true
                    }
                }

                idlePolls = if (madeProgress) 0 else idlePolls + 1
                if (idlePolls > 1_200) throw IllegalStateException("Audio decoder timed out")
            }

            require(decodedRate > 0 && decodedChannels in 1..2) { "Decoder produced no PCM format" }
            flushFloatChunk()
            output?.flush()
            output?.close()
            output = null
            val source = PcmFileAudioSource(outputFile, decodedRate, decodedChannels)
            success = true
            onProgress(1f)
            return PcmDecodeResult(source, source.durationMs, sourceDurationMs)
        } finally {
            runCatching { output?.close() }
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            if (!success) outputFile.delete()
        }
    }
}

private fun MediaFormat.optLong(key: String, fallback: Long): Long =
    if (containsKey(key)) getLong(key) else fallback
