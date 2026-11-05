package com.creatorstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VisualMint = Color(0xFF34D8C5)
private val VisualViolet = Color(0xFF9B7BFF)

@Composable
fun VisualizerPage(
    spectrum: List<Float>,
    waveform: List<Float>,
    mode: String,
    running: Boolean,
    bpm: Int?,
    beatPulse: Int,
    message: String,
    onModeChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val pulseScale by animateFloatAsState(if (beatPulse % 2 == 0) 1f else 1.055f, label = "beat-pulse")
    Column(Modifier.fillMaxWidth()) {
        Text("VISUALIZER STUDIO", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Live FFT spectrum and waveform from the selected audio output", color = Color(0xFF9AA4B8), fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Spectrum", "Radial", "Waveform").forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = { onModeChange(item) },
                    label = { Text(item, fontSize = 10.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(250.dp).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .background(Color(0xFF111827), RoundedCornerShape(20.dp)).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
                "Radial" -> RadialSpectrum(spectrum)
                "Waveform" -> WaveformDisplay(waveform)
                else -> SpectrumDisplay(spectrum)
            }
            Text(
                if (running) "LIVE ANALYSIS" else "PREVIEW",
                color = VisualMint,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Spacer(Modifier.height(10.dp))
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
            "Live preview analysis only. Offline visualizer rendering and GPU/3D modes are separate milestones.",
            color = Color(0xFF7F8BA2), fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
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
