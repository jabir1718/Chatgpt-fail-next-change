package com.creatorstudio.app.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.Closeable
import java.io.IOException

/**
 * Decodes imported still-image assets with bounded resolution and an explicit pixel budget.
 * PNG alpha is preserved by ARGB_8888. Cached bitmaps remain valid until close().
 */
class ImageAssetBitmapLoader(
    private val context: Context,
    private val maxCachedPixels: Long = 32_000_000L
) : Closeable {
    private val cache = linkedMapOf<String, Bitmap>()
    private var cachedPixels = 0L

    @Synchronized
    fun load(asset: MediaAsset, targetWidth: Int, targetHeight: Int): Bitmap {
        require(targetWidth > 0 && targetHeight > 0)
        cache[asset.id]?.let { return it }
        val uri = Uri.parse(asset.uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Could not open image asset: ${asset.displayName}")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image asset is invalid: ${asset.displayName}" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inDither = false
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Could not decode image asset: ${asset.displayName}")
        val pixels = bitmap.width.toLong() * bitmap.height
        if (cachedPixels + pixels > maxCachedPixels) {
            bitmap.recycle()
            throw IOException("Image layer cache budget exceeded; reduce image resolution or number of layers")
        }
        cache[asset.id] = bitmap
        cachedPixels += pixels
        return bitmap
    }

    @Synchronized
    override fun close() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        cachedPixels = 0L
    }
}

internal fun calculateImageSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0)
    var sample = 1
    while (sourceWidth / sample > targetWidth * 2 || sourceHeight / sample > targetHeight * 2) {
        sample *= 2
    }
    return sample
}
