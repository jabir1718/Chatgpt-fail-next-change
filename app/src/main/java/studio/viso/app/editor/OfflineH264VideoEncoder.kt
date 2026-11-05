package com.creatorstudio.app.editor

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

data class H264VideoExportResult(val file: File, val frameCount: Long, val byteCount: Long)

/**
 * Encodes rendered ARGB bitmaps into an H.264 MP4 video-only file.
 * The caller supplies one frame at a time, keeping memory bounded.
 */
class OfflineH264VideoEncoder {
    fun encode(
        width: Int,
        height: Int,
        frameRate: Int,
        totalFrames: Long,
        outputFile: File,
        frameProvider: (Long) -> Bitmap,
        bitRate: Int = 8_000_000,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): H264VideoExportResult {
        require(width >= 64 && height >= 64 && width % 2 == 0 && height % 2 == 0) {
            "H.264 dimensions must be even and at least 64 pixels"
        }
        require(frameRate in 1..60) { "Frame rate must be between 1 and 60 FPS" }
        require(totalFrames > 0L) { "There are no video frames to encode" }
        require(bitRate in 250_000..80_000_000) { "Video bit rate is outside the supported range" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) {
            throw IllegalStateException("Could not replace the previous video export")
        }

        var codec: MediaCodec? = null
        var codecStarted = false
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        try {
            val codecInfo = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } &&
                    runCatching {
                        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats
                            .contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    }.getOrDefault(false)
            } ?: throw UnsupportedOperationException("This device has no H.264 encoder with flexible YUV input")

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 3 / 2)
            }
            codec = MediaCodec.createByCodecName(codecInfo.name)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerTrack = -1
            var nextFrame = 0L
            var inputEnded = false
            var outputEnded = false
            var idlePolls = 0
            val info = MediaCodec.BufferInfo()
            val pixelBuffer = IntArray(width * height)
            val timeoutUs = 10_000L

            while (!outputEnded) {
                if (shouldCancel()) throw CancellationException("Video export cancelled")
                var madeProgress = false

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        if (nextFrame < totalFrames) {
                            val bitmap = frameProvider(nextFrame)
                            require(bitmap.width == width && bitmap.height == height) {
                                "Rendered frame dimensions do not match the export canvas"
                            }
                            bitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
                            val image = codec.getInputImage(inputIndex)
                                ?: throw UnsupportedOperationException("H.264 encoder did not expose a flexible YUV input image")
                            try {
                                writeYuv420Image(image, pixelBuffer, width, height)
                            } finally {
                                image.close()
                            }
                            codec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, framePtsUs(nextFrame, frameRate), 0)
                            nextFrame++
                            onProgress((nextFrame.toDouble() / totalFrames * 0.95).toFloat().coerceIn(0f, 0.95f))
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, framePtsUs(totalFrames, frameRate),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        }
                        madeProgress = true
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "H.264 encoder changed output format more than once" }
                        muxerTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        madeProgress = true
                    }
                    else -> if (outputIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outputIndex)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted && muxerTrack >= 0) { "Video muxer was not initialized" }
                            val buffer = encoded ?: error("H.264 output buffer was unavailable")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(muxerTrack, buffer, info)
                        }
                        val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) outputEnded = true
                        madeProgress = true
                    }
                }

                idlePolls = if (madeProgress) 0 else idlePolls + 1
                if (idlePolls > 1_200) throw IllegalStateException("H.264 encoder timed out")
            }

            check(muxerStarted) { "H.264 encoder produced no video track" }
            muxer.stop()
            muxerStarted = false
            require(outputFile.isFile && outputFile.length() > 0L) { "H.264 output file is empty" }
            onProgress(1f)
            success = true
            return H264VideoExportResult(outputFile, totalFrames, outputFile.length())
        } finally {
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!success) outputFile.delete()
        }
    }

    private fun framePtsUs(frame: Long, frameRate: Int): Long = frame * 1_000_000L / frameRate

    private fun writeYuv420Image(image: Image, pixels: IntArray, width: Int, height: Int) {
        val planes = image.planes
        require(planes.size == 3) { "Flexible YUV input must expose Y, U and V planes" }
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        for (row in 0 until height) {
            for (column in 0 until width) {
                val yuv = rgbToYuv(pixels[row * width + column])
                putPlaneByte(yBuffer, row * yPlane.rowStride + column * yPlane.pixelStride, yuv.y)
            }
        }

        for (row in 0 until height step 2) {
            for (column in 0 until width step 2) {
                var uSum = 0
                var vSum = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val yuv = rgbToYuv(pixels[(row + dy) * width + column + dx])
                    uSum += yuv.u
                    vSum += yuv.v
                }
                val chromaRow = row / 2
                val chromaColumn = column / 2
                putPlaneByte(uBuffer, chromaRow * uPlane.rowStride + chromaColumn * uPlane.pixelStride, uSum / 4)
                putPlaneByte(vBuffer, chromaRow * vPlane.rowStride + chromaColumn * vPlane.pixelStride, vSum / 4)
            }
        }
    }

    private fun putPlaneByte(buffer: ByteBuffer, index: Int, value: Int) {
        require(index in 0 until buffer.limit()) { "YUV plane layout exceeded the input buffer" }
        buffer.put(index, value.coerceIn(0, 255).toByte())
    }
}

internal data class YuvPixel(val y: Int, val u: Int, val v: Int)

internal fun rgbToYuv(argb: Int): YuvPixel {
    val red = (argb shr 16) and 0xff
    val green = (argb shr 8) and 0xff
    val blue = argb and 0xff
    val y = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
    val u = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
    val v = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128
    return YuvPixel(y.coerceIn(0, 255), u.coerceIn(0, 255), v.coerceIn(0, 255))
}
