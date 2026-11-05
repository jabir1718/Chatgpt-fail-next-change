package com.creatorstudio.app.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws one deterministic offline visualizer composition frame.
 * Video frames are keyed by clip ID; still-image bitmaps are keyed by asset ID.
 * The caller owns and must release the returned Bitmap.
 */
class OfflineVisualizerFrameRenderer {
    fun render(
        project: EditorProject,
        framePlan: RenderFramePlan,
        decodedFrames: Map<String, Bitmap>
    ): Bitmap {
        require(framePlan.width > 0 && framePlan.height > 0)
        val output = Bitmap.createBitmap(framePlan.width, framePlan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        project.visualizer.backgroundAssetId?.let { assetId ->
            decodedFrames[assetId]?.let { drawFullCanvas(canvas, it, framePlan.width, framePlan.height, 255) }
        }

        framePlan.visualizerFrame?.let { drawVisualizer(canvas, it, project.visualizer.mode, framePlan.width, framePlan.height) }

        framePlan.layers.forEach { layer ->
            when (layer.role) {
                RenderLayerRole.VISUALIZER_BACKGROUND -> Unit
                RenderLayerRole.VISUALIZER_FOREGROUND -> {
                    decodedFrames[layer.assetId]?.let { bitmap ->
                        val beatScale = 1f + (framePlan.visualizerFrame?.beatPulse ?: 0f) * 0.16f
                        drawLayerBitmap(
                            canvas, bitmap, layer.transform, framePlan.width, framePlan.height,
                            (project.visualizer.foregroundScale * beatScale).coerceIn(0.5f, 2.5f)
                        )
                    }
                }
                RenderLayerRole.IMAGE -> {
                    decodedFrames[layer.assetId]?.let { bitmap ->
                        drawLayerBitmap(canvas, bitmap, layer.transform, framePlan.width, framePlan.height, 1f)
                    }
                    drawCaptions(canvas, layer.activeCaptions.map { it.text }, framePlan.width, framePlan.height)
                }
                RenderLayerRole.VIDEO -> {
                    decodedFrames[layer.clipId ?: layer.assetId]?.let { bitmap ->
                        drawLayerBitmap(canvas, bitmap, layer.transform, framePlan.width, framePlan.height, 1f)
                    }
                    drawCaptions(canvas, layer.activeCaptions.map { it.text }, framePlan.width, framePlan.height)
                }
                RenderLayerRole.TEXT -> {
                    drawCaptions(canvas, layer.activeCaptions.map { it.text }, framePlan.width, framePlan.height)
                }
            }
        }
        return output
    }

    private fun drawFullCanvas(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) * 0.5f
        val top = (height - drawHeight) * 0.5f
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        canvas.restore()
    }

    private fun drawLayerBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        transform: Transform,
        width: Int,
        height: Int,
        extraScale: Float,
        contentMode: CanvasContentMode = CanvasContentMode.FIT
    ) {
        val save = canvas.save()
        canvas.translate(width * 0.5f + transform.x, height * 0.5f + transform.y)
        canvas.rotate(transform.rotationDegrees)
        canvas.scale(transform.scaleX * extraScale, transform.scaleY * extraScale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (transform.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        val scaleX = width.toFloat() / bitmap.width
        val scaleY = height.toFloat() / bitmap.height
        val fitScale = when (contentMode) {
            CanvasContentMode.FIT -> min(scaleX, scaleY)
            CanvasContentMode.CROP -> maxOf(scaleX, scaleY)
            CanvasContentMode.FILL -> 1f
        }
        val drawWidth = if (contentMode == CanvasContentMode.FILL) width.toFloat() else bitmap.width * fitScale
        val drawHeight = if (contentMode == CanvasContentMode.FILL) height.toFloat() else bitmap.height * fitScale
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(-drawWidth * 0.5f, -drawHeight * 0.5f, drawWidth * 0.5f, drawHeight * 0.5f),
            paint
        )
        canvas.restoreToCount(save)
    }

    private fun drawVisualizer(canvas: Canvas, frame: OfflineVisualizerFrameData, mode: String, width: Int, height: Int) {
        val bands = frame.spectrumBands
        if (bands.isEmpty()) return
        val pulse = frame.beatPulse.coerceIn(0f, 1f)
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val minDimension = min(width, height).toFloat()
        val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 216, 197); style = Paint.Style.FILL }
        val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(155, 123, 255); style = Paint.Style.STROKE; strokeWidth = minDimension * 0.004f }

        when (mode) {
            "Spectrum" -> {
                val count = bands.size
                val gap = width * 0.004f
                val totalWidth = width * 0.82f
                val barWidth = (totalWidth - gap * (count - 1)) / count
                val left = width * 0.09f
                bands.forEachIndexed { index, level ->
                    val barHeight = height * (0.025f + level * 0.34f) * (1f + pulse * 0.18f)
                    val x = left + index * (barWidth + gap)
                    val rect = RectF(x, centerY - barHeight * 0.5f, x + barWidth, centerY + barHeight * 0.5f)
                    canvas.drawRoundRect(rect, barWidth * 0.35f, barWidth * 0.35f, primary)
                }
            }
            "Radial" -> {
                val innerRadius = minDimension * 0.16f
                val maxLength = minDimension * 0.24f
                bands.forEachIndexed { index, level ->
                    val angle = (2.0 * PI * index / bands.size - PI / 2.0).toFloat()
                    val outer = innerRadius + maxLength * level * (1f + pulse * 0.2f)
                    primary.strokeWidth = minDimension * 0.012f
                    canvas.drawLine(
                        centerX + cos(angle) * innerRadius,
                        centerY + sin(angle) * innerRadius,
                        centerX + cos(angle) * outer,
                        centerY + sin(angle) * outer,
                        primary
                    )
                }
            }
            "Waveform" -> {
                val samples = frame.waveform
                if (samples.isNotEmpty()) {
                    val path = Path()
                    samples.forEachIndexed { index, value ->
                        val x = width * index.toFloat() / (samples.size - 1).coerceAtLeast(1)
                        val y = centerY - value * height * 0.22f * (1f + pulse * 0.15f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    primary.style = Paint.Style.STROKE
                    primary.strokeWidth = minDimension * 0.008f
                    primary.strokeCap = Paint.Cap.ROUND
                    canvas.drawPath(path, primary)
                }
            }
            "Rings" -> {
                for (index in 0 until 7) {
                    val radius = minDimension * (0.08f + index * 0.055f) * (1f + pulse * 0.1f)
                    secondary.alpha = 190 - index * 16
                    secondary.strokeWidth = minDimension * (0.003f + frame.rms * 0.008f)
                    canvas.drawCircle(centerX, centerY, radius, secondary)
                }
            }
            "Particles" -> {
                val count = 72
                for (index in 0 until count) {
                    val level = bands[index % bands.size]
                    val angle = 2.0 * PI * index / count + frame.timestampMs / 8_000.0
                    val radius = minDimension * (0.12f + level * 0.25f + pulse * 0.035f)
                    val x = centerX + cos(angle).toFloat() * radius
                    val y = centerY + sin(angle).toFloat() * radius
                    val dotRadius = minDimension * (0.003f + level * 0.008f)
                    primary.alpha = (130 + level * 125).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, dotRadius, primary)
                }
            }
        }
    }

    private fun drawCaptions(canvas: Canvas, captions: List<String>, width: Int, height: Int) {
        if (captions.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = height * 0.035f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setShadowLayer(height * 0.006f, 0f, height * 0.002f, Color.BLACK)
        }
        val lineHeight = paint.textSize * 1.25f
        val startY = height * 0.84f - (captions.size - 1) * lineHeight * 0.5f
        captions.forEachIndexed { index, text ->
            canvas.drawText(text, width * 0.5f, startY + index * lineHeight, paint)
        }
    }
}
