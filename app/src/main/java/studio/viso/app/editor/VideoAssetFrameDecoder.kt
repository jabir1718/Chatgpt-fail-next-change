package com.creatorstudio.app.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.Closeable
import java.io.IOException

/**
 * Decodes source-video frames on demand for the offline compositor.
 * Retrievers are reused per imported asset; decoded frame bitmaps are caller-owned.
 * Only one frame per active clip is held by the export loop at a time.
 */
class VideoAssetFrameDecoder(
    private val context: Context
) : Closeable {
    private val retrievers = linkedMapOf<String, MediaMetadataRetriever>()

    @Synchronized
    fun decode(
        asset: MediaAsset,
        sourcePositionMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        require(asset.kind == MediaKind.VIDEO) { "Video frame decoder requires a video asset" }
        require(sourcePositionMs >= 0L) { "Video source position cannot be negative" }
        require(targetWidth > 0 && targetHeight > 0)
        val retriever = retrievers.getOrPut(asset.id) {
            MediaMetadataRetriever().apply {
                try {
                    setDataSource(context, Uri.parse(asset.uri))
                } catch (error: Exception) {
                    release()
                    throw IOException("Could not open video asset: ${asset.displayName}", error)
                }
            }
        }
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val orientedWidth = if (rotation == 90 || rotation == 270) height ?: targetWidth else width ?: targetWidth
        val orientedHeight = if (rotation == 90 || rotation == 270) width ?: targetHeight else height ?: targetHeight
        val scaledSize = scaledVideoFrameSize(orientedWidth, orientedHeight, targetWidth, targetHeight)
        val timeUs = videoSourceTimeUs(sourcePositionMs)

        val decoded = if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    scaledSize.first,
                    scaledSize.second
                )
            }.getOrNull()
        } else null
        val frame = decoded ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: throw IOException("Could not decode video frame at ${sourcePositionMs}ms from ${asset.displayName}")

        if (frame.width <= targetWidth && frame.height <= targetHeight) return frame
        val finalSize = scaledVideoFrameSize(frame.width, frame.height, targetWidth, targetHeight)
        val scaled = Bitmap.createScaledBitmap(frame, finalSize.first, finalSize.second, true)
        if (scaled !== frame) frame.recycle()
        return scaled
    }

    @Synchronized
    override fun close() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
    }
}

internal fun videoSourceTimeUs(sourcePositionMs: Long): Long {
    require(sourcePositionMs >= 0L)
    return Math.multiplyExact(sourcePositionMs, 1_000L)
}

internal fun scaledVideoFrameSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Pair<Int, Int> {
    require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0)
    val scale = minOf(1.0, targetWidth.toDouble() / sourceWidth, targetHeight.toDouble() / sourceHeight)
    return maxOf(1, (sourceWidth * scale).toInt()) to maxOf(1, (sourceHeight * scale).toInt())
}
