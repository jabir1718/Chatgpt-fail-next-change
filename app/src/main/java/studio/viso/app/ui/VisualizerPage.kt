package com.creatorstudio.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creatorstudio.app.editor.MediaAsset
import com.creatorstudio.app.editor.VisualizerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VisualMint = Color(0xFF34D8C5)
private val VisualViolet = Color(0xFF9B7BFF)

@Composable
fun VisualizerPage(
    spectrum: List<Float>,
    waveform: List<Float>,
    running: Boolean,
    bpm: Int?,
    beatPulse: Int,
    message: String,
    images: List<MediaAsset>,
    settings: VisualizerSettings,
    onSettingsChange: (VisualizerSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val backgroundAsset = images.firstOrNull { it.id == settings.backgroundAssetId }
    val foregroundAsset = images.firstOrNull { it.id == settings.foregroundAssetId }
    val backgroundBitmap = rememberProjectBitmap(backgroundAsset?.uri)
    val foregroundBitmap = rememberProjectBitmap(foregroundAsset?.uri)
    val pulseScale by animateFloatAsState(
        if (beatPulse % 2 == 0) 1f else settings.beatSensitivity,
        label = "foreground-beat-pulse"
    )

    Column(Modifier.fillMaxWidth()) {
        Text("VISUALIZER STUDIO", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Live FFT, waveform and beat response from the selected audio output", color = Color(0xFF9AA4B8), fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Spectrum", "Radial", "Waveform").forEach { item ->
                FilterChip(
                    selected = settings.mode == item,
                    onClick = { onSettingsChange(settings.copy(mode = item)) },
                    label = { Text(item, fontSize = 10.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageLayerPicker(
                title = "Background image",
                images = images,
                selectedId = settings.backgroundAssetId,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(backgroundAssetId = it)) }
            )
            ImageLayerPicker(
                title = "Foreground PNG",
                images = images,
                selectedId = settings.foregroundAssetId,
                modifier = Modifier.weight(1f),
                onSelected = { onSettingsChange(settings.copy(foregroundAssetId = it)) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(270.dp)
                .background(Color(0xFF111827), RoundedCornerShape(20.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (backgroundBitmap != null) {
                Image(
                    bitmap = backgroundBitmap,
                    contentDescription = backgroundAsset?.displayName ?: "Visualizer background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            when (settings.mode) {
                "Radial" -> RadialSpectrum(spectrum)
                "Waveform" -> WaveformDisplay(waveform)
                else -> SpectrumDisplay(spectrum)
            }
            if (foregroundBitmap != null) {
                Image(
                    bitmap = foregroundBitmap,
                    contentDescription = foregroundAsset?.displayName ?: "Foreground PNG",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.Center)
                        .size((112f * settings.foregroundScale).dp)
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                )
            }
            Text(
                if (running) "LIVE ANALYSIS" else "PREVIEW",
                color = VisualMint,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        if (foregroundAsset != null) {
            Text("FOREGROUND SCALE  ${"%.2f".format(settings.foregroundScale)}×", color = Color(0xFFB7C4D8), fontSize = 9.sp)
            Slider(
                value = settings.foregroundScale,
                onValueChange = { onSettingsChange(settings.copy(foregroundScale = it)) },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(thumbColor = VisualViolet, activeTrackColor = VisualViolet)
            )
            Text("BEAT RESPONSE  ${"%.2f".format(settings.beatSensitivity)}×", color = Color(0xFFB7C4D8), fontSize = 9.sp)
            Slider(
                value = settings.beatSensitivity,
                onValueChange = { onSettingsChange(settings.copy(beatSensitivity = it)) },
                valueRange = 1f..1.5f,
                colors = SliderDefaults.colors(thumbColor = VisualViolet, activeTrackColor = VisualViolet)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = if (running) VisualMint else Color(0xFFB7C4D8), fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("BPM ESTIMATE  ${bpm?.toString() ?: "—"}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("BEAT PULSES  $beatPulse", color = VisualMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !running, modifier = Modifier.weight(1f)) {
                Text("Start live visualizer", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f)) {
                Text("Stop", fontSize = 11.sp)
            }
        }
        Text(
            "Background and foreground image choices are saved in this project. Foreground PNG reacts to detected beats. Offline rendering and GPU/3D modes are separate milestones.",
            color = Color(0xFF7F8BA2), fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ImageLayerPicker(
    title: String,
    images: List<MediaAsset>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = images.firstOrNull { it.id == selectedId }?.displayName ?: "None"
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp)) {
            Column {
                Text(title, fontSize = 8.sp, color = VisualMint)
                Text(selectedName, fontSize = 9.sp, maxLines = 1, color = Color.White)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelected(null); expanded = false })
            images.forEach { asset ->
                DropdownMenuItem(
                    text = { Text(asset.displayName, maxLines = 1) },
                    onClick = { onSelected(asset.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun rememberProjectBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uriString) {
        value = if (uriString == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val largestSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val sample = (largestSide / 1600).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun SpectrumDisplay(values: List<Float>) {
    Row(
        Modifier.fillMaxWidth().height(190.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        values.forEachIndexed { index, value ->
            Box(
                Modifier.width(7.dp).height((value.coerceIn(0f, 1f) * 170f + 3f).dp)
                    .background(if (index % 4 == 0) VisualViolet else VisualMint, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
private fun RadialSpectrum(values: List<Float>) {
    Canvas(Modifier.size(220.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.24f
        drawCircle(VisualViolet.copy(alpha = 0.16f), radius = base, center = center, style = Stroke(width = 2.dp.toPx()))
        val count = values.size.coerceAtLeast(1)
        values.forEachIndexed { index, raw ->
            val angle = (index.toDouble() / count * Math.PI * 2.0 - Math.PI / 2.0)
            val amount = raw.coerceIn(0f, 1f)
            val inner = base
            val outer = base + amount * base * 0.95f
            val start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner)
            val end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer)
            drawLine(if (index % 3 == 0) VisualViolet else VisualMint, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(VisualMint.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = center)
    }
}

@Composable
private fun WaveformDisplay(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        if (values.isEmpty()) {
            drawLine(Color(0xFF445064), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2.dp.toPx())
            return@Canvas
        }
        val path = Path()
        val middle = size.height / 2f
        values.forEachIndexed { index, sample ->
            val x = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
            val y = middle - sample.coerceIn(-1f, 1f) * middle * 0.88f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, VisualMint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
