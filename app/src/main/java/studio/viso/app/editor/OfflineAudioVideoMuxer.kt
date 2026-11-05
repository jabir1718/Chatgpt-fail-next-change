package com.creatorstudio.app.editor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

data class MuxedAudioVideoResult(val file: File, val byteCount: Long)

/** Combines already encoded H.264 video-only MP4 and AAC audio-only M4A tracks. */
class OfflineAudioVideoMuxer {
    fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): MuxedAudioVideoResult {
        require(videoFile.isFile && videoFile.length() > 0L) { "Video source file is missing or empty" }
        require(audioFile.isFile && audioFile.length() > 0L) { "Audio source file is missing or empty" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) throw IllegalStateException("Could not replace output MP4")

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            require(videoTrack >= 0) { "Encoded video file has no video track" }
            require(audioTrack >= 0) { "Encoded audio file has no AAC track" }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val outputAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()
            muxerStarted = true

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            copyTrack(videoExtractor, videoTrack, muxer, outputVideoTrack, shouldCancel) { progress ->
                onProgress(progress * 0.5f)
            }
            if (shouldCancel()) throw CancellationException("Audio/video mux cancelled")
            copyTrack(audioExtractor, audioTrack, muxer, outputAudioTrack, shouldCancel) { progress ->
                onProgress(0.5f + progress * 0.5f)
            }

            muxer.stop()
            muxerStarted = false
            require(outputFile.isFile && outputFile.length() > 0L) { "Muxed MP4 is empty" }
            onProgress(1f)
            success = true
            return MuxedAudioVideoResult(outputFile, outputFile.length())
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (!success) outputFile.delete()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return index
        }
        return -1
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        sourceTrack: Int,
        muxer: MediaMuxer,
        outputTrack: Int,
        shouldCancel: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        val format = extractor.getTrackFormat(sourceTrack)
        val capacity = (if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else 0).coerceAtLeast(4 * 1024 * 1024)
        val buffer = ByteBuffer.allocate(capacity)
        val info = MediaCodec.BufferInfo()
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        var copied = 0L
        while (true) {
            if (shouldCancel()) throw CancellationException("Audio/video mux cancelled")
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val pts = extractor.sampleTime
            info.set(0, size, pts, extractor.sampleFlags)
            muxer.writeSampleData(outputTrack, buffer, info)
            copied++
            if (durationUs > 0L && pts >= 0L) onProgress((pts.toDouble() / durationUs).toFloat().coerceIn(0f, 1f))
            extractor.advance()
        }
        if (copied == 0L) throw IllegalStateException("Encoded media track contains no samples")
        onProgress(1f)
    }
}
