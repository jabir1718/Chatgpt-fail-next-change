package com.creatorstudio.app.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
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
                        drawLayerBitmap(canvas, bitmap, layer.transform, framePlan.width, framePlan.height, layerBeatScale(layer, framePlan), layer.contentMode)
                    }
                    drawCaptions(canvas, layer.activeCaptions, layer.clipLocalTimeMs, framePlan.width, framePlan.height)
                }
                RenderLayerRole.VIDEO -> {
                    decodedFrames[layer.clipId ?: layer.assetId]?.let { bitmap ->
                        drawLayerBitmap(canvas, bitmap, layer.transform, framePlan.width, framePlan.height, 1f, layer.contentMode)
                    }
                    drawCaptions(canvas, layer.activeCaptions, layer.clipLocalTimeMs, framePlan.width, framePlan.height)
                }
                RenderLayerRole.TEXT -> {
                    drawCaptions(canvas, layer.activeCaptions, layer.clipLocalTimeMs, framePlan.width, framePlan.height)
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

    private fun layerBeatScale(layer: RenderLayerPlan, framePlan: RenderFramePlan): Float {
        val response = layer.beatResponse
        if (!response.enabled) return 1f
        val pulse = (framePlan.visualizerFrame?.beatPulse ?: 0f).coerceIn(0f, 1f)
        return response.scaleForPulse(pulse)
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
            "3D Orb" -> drawOfflineOrb(canvas, frame, centerX, centerY, minDimension)
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

    /**
     * Deterministic software-rendered shaded orb for offline frames. It uses the
     * same 32-band/beat snapshot as live preview, without requiring a GL surface
     * or a device-specific GPU context inside the export worker.
     */
    private fun drawOfflineOrb(
        canvas: Canvas,
        frame: OfflineVisualizerFrameData,
        centerX: Float,
        centerY: Float,
        minDimension: Float
    ) {
        val geometry = OfflineOrbFrameMath.geometry(frame, minDimension.toInt(), minDimension.toInt())
        val radius = geometry.radiusPx
        val pulse = geometry.beatPulse
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(72, 0, 0, 0) }
        canvas.drawOval(
            RectF(centerX - radius * 0.78f, centerY + radius * 0.78f,
                centerX + radius * 0.78f, centerY + radius * 1.02f),
            shadow
        )

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((34 + pulse * 42f).toInt(), 37, 218, 208) }
        canvas.drawCircle(centerX, centerY, radius * 1.72f, halo)
        val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 115, 151, 255)
            style = Paint.Style.STROKE
            strokeWidth = minDimension * 0.0035f
        }
        canvas.drawCircle(centerX, centerY, radius * 1.43f, orbit)

        val bands = frame.spectrumBands
        bands.forEachIndexed { index, raw ->
            val level = raw.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            val angle = (2.0 * PI * index / bands.size.coerceAtLeast(1) + geometry.rotationRadians).toFloat()
            val inner = radius * 1.22f
            val outer = inner + minDimension * (0.018f + level * 0.092f) * (1f + pulse * 0.2f)
            val startX = centerX + cos(angle) * inner
            val startY = centerY + sin(angle) * inner
            val endX = centerX + cos(angle) * outer
            val endY = centerY + sin(angle) * outer
            val tint = if (index % 4 == 0) Color.rgb(155, 123, 255) else Color.rgb(52, 216, 205)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((55 + level * 95f).toInt().coerceIn(0, 150), Color.red(tint), Color.green(tint), Color.blue(tint))
                strokeWidth = minDimension * 0.012f
                strokeCap = Paint.Cap.ROUND
            }
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((120 + level * 135f).toInt().coerceIn(0, 255), Color.red(tint), Color.green(tint), Color.blue(tint))
                strokeWidth = minDimension * 0.0045f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(startX, startY, endX, endY, glow)
            canvas.drawLine(startX, startY, endX, endY, core)
        }

        val spherePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX - radius * 0.34f,
                centerY - radius * 0.40f,
                radius * 1.48f,
                intArrayOf(
                    Color.rgb(232, 255, 255),
                    Color.rgb(91, 231, 218),
                    Color.rgb(27, 112, 164),
                    Color.rgb(5, 11, 34)
                ),
                floatArrayOf(0f, 0.22f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, centerY, radius, spherePaint)

        // Audio-driven surface filaments approximate the rotating shader pattern
        // while remaining deterministic in the software export renderer.
        val clipPath = Path().apply {
            addCircle(centerX, centerY, radius * 0.995f, Path.Direction.CW)
        }
        val surfaceSave = canvas.save()
        canvas.clipPath(clipPath)
        for (ring in 0 until 3) {
            val wave = Path()
            val samples = 64
            for (sample in 0..samples) {
                val angle = (2.0 * PI * sample / samples).toFloat()
                val audio = OfflineOrbFrameMath.bandAtAngle(frame, angle + geometry.rotationRadians)
                val ripple = kotlin.math.sin(angle * 8f + geometry.rotationRadians * 3f + audio * 4f).toFloat()
                val ringRadius = radius * (0.27f + ring * 0.22f + audio * 0.045f + ripple * 0.009f)
                val x = centerX + cos(angle) * ringRadius
                val y = centerY + sin(angle) * ringRadius
                if (sample == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
            }
            val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val alpha = (28f + geometry.averageBandEnergy * 92f + pulse * 30f).toInt().coerceIn(0, 150)
                color = if (ring == 1) Color.argb(alpha, 175, 151, 255) else Color.argb(alpha, 120, 250, 238)
                style = Paint.Style.STROKE
                strokeWidth = minDimension * (0.0022f + geometry.averageBandEnergy * 0.0012f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(wave, surfacePaint)
        }
        canvas.restoreToCount(surfaceSave)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                centerX - radius * 0.34f,
                centerY - radius * 0.40f,
                radius * 0.52f,
                intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(centerX, centerY, radius, highlightPaint)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 117, 245, 239)
            style = Paint.Style.STROKE
            strokeWidth = minDimension * 0.004f
        }
        canvas.drawCircle(centerX, centerY, radius, rim)
    }

    private fun drawCaptions(canvas: Canvas, captions: List<CaptionSegment>, clipLocalTimeMs: Long, width: Int, height: Int) {
        if (captions.isEmpty()) return
        val lineHeights = captions.map { height * 0.035f * (it.fontSizeSp.coerceIn(10f, 48f) / 16f) * 1.25f }
        val totalHeight = lineHeights.sum()
        var baseline = height * ((captions.firstOrNull()?.verticalPositionPercent ?: 84f).coerceIn(10f, 90f) / 100f) - totalHeight * 0.5f
        captions.forEachIndexed { index, caption ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = caption.textColorArgb
                textAlign = Paint.Align.LEFT
                textSize = height * 0.035f * (caption.fontSizeSp.coerceIn(10f, 48f) / 16f)
                val typefaceStyle = (if (caption.isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL) or
                    (if (caption.isItalic) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
                typeface = android.graphics.Typeface.create(when (caption.fontFamily) {
                    CaptionFontFamily.SANS -> "sans-serif"
                    CaptionFontFamily.SERIF -> "serif"
                    CaptionFontFamily.MONOSPACE -> "monospace"
                    CaptionFontFamily.CURSIVE -> "cursive"
                }, typefaceStyle)
                if (caption.shadowStrength > 0f) {
                    val strength = caption.shadowStrength.coerceIn(0f, 1f)
                    setShadowLayer(height * 0.006f * strength, 0f, height * 0.002f * strength,
                        Color.argb((strength * 190f).toInt().coerceIn(0, 190), 0, 0, 0))
                }
            }
            val outlinePaint = Paint(paint).apply {
                color = caption.outlineColorArgb
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = height * 0.035f * (caption.outlineWidthSp.coerceIn(0f, 6f) / 16f) * 2f
                clearShadowLayer()
            }
            val activePaint = Paint(paint).apply { color = Color.rgb(8, 10, 18); clearShadowLayer() }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 216, 197) }
            val textWidth = paint.measureText(caption.text)
            val margin = width * 0.08f
            val x = when (caption.alignment) {
                CaptionAlignment.LEFT -> margin
                CaptionAlignment.CENTER -> (width - textWidth) * 0.5f
                CaptionAlignment.RIGHT -> width - margin - textWidth
            }
            if (caption.backgroundColorArgb != 0) {
                val padX = paint.textSize * 0.38f
                val padY = paint.textSize * 0.24f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = caption.backgroundColorArgb }
                canvas.drawRoundRect(RectF(x - padX, baseline - paint.textSize - padY, x + textWidth + padX, baseline + padY), paint.textSize * 0.2f, paint.textSize * 0.2f, bgPaint)
            }
            val activeIndex = caption.wordTimings.indexOfFirst { clipLocalTimeMs >= it.startMs && clipLocalTimeMs < it.endMs }
            val activeWord = caption.wordTimings.getOrNull(activeIndex)
            val wordStart = activeWord?.let { caption.text.indexOf(it.word) } ?: -1
            if (outlinePaint.strokeWidth > 0f) canvas.drawText(caption.text, x, baseline, outlinePaint)
            if (activeWord == null || wordStart < 0) {
                canvas.drawText(caption.text, x, baseline, paint)
            } else {
                val before = caption.text.substring(0, wordStart)
                val after = caption.text.substring(wordStart + activeWord.word.length)
                var wordX = x + paint.measureText(before)
                canvas.drawText(before, x, baseline, paint)
                val wordWidth = paint.measureText(activeWord.word)
                val pad = paint.textSize * 0.16f
                val rect = RectF(wordX - pad, baseline - paint.textSize * 0.88f, wordX + wordWidth + pad, baseline + paint.textSize * 0.18f)
                canvas.drawRoundRect(rect, paint.textSize * 0.18f, paint.textSize * 0.18f, highlightPaint)
                canvas.drawText(activeWord.word, wordX, baseline, activePaint)
                wordX += wordWidth
                canvas.drawText(after, wordX, baseline, paint)
            }
            baseline += lineHeights[index]
        }
    }}
