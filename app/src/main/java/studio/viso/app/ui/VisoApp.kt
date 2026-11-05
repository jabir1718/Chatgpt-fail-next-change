package studio.viso.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF080A12)
private val Panel = Color(0xFF141A29)
private val Violet = Color(0xFF9B7BFF)
private val Mint = Color(0xFF34D8C5)
private val Pink = Color(0xFFFF6FAE)

@Composable
fun VisoApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTool by remember { mutableStateOf("Visualizer") }
    var isPlaying by remember { mutableStateOf(false) }
    var intensity by remember { mutableFloatStateOf(0.72f) }
    val tabs = listOf("Studio", "Audio", "Visuals", "Lyrics", "Export")

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101522), contentColor = Color.White) {
                val icons = listOf(Icons.Default.Movie, Icons.Default.GraphicEq, Icons.Default.AutoAwesome, Icons.Default.Subtitles, Icons.Default.LibraryMusic)
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF11172A), Ink)))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Header()
            when (selectedTab) {
                0 -> StudioPage(
                    isPlaying = isPlaying,
                    onPlay = { isPlaying = !isPlaying },
                    selectedTool = selectedTool,
                    onTool = { selectedTool = it },
                    intensity = intensity,
                    onIntensity = { intensity = it }
                )
                1 -> ToolPage("Audio Lab", "Independent controls for every audio clip", listOf("Pitch & Formant", "Tempo / Speed", "BPM Detection", "Bass & EQ", "Voice Changer", "Noise Cleanup", "Reverb & Echo", "Vocal Isolation"))
                2 -> ToolPage("Visual Effects", "Build a layered, beat-reactive scene", listOf("3D Spectrum", "Circular Visualizer", "Particles & Glow", "PNG Cutout", "Background Removal", "Green Screen", "Image Beat Pulse", "Video Overlays"))
                3 -> ToolPage("AI Lyrics", "Automatic transcription and synced captions", listOf("Auto Transcribe", "Detect Language", "Roman Urdu", "Urdu Script", "Roman English", "Word Timing", "Emoji Captions", "Voice Typing"))
                else -> ToolPage("Export & Share", "Prepare the right format for every platform", listOf("TikTok 9:16", "YouTube Shorts", "Instagram Reels", "1080p / 4K", "Frame Rate", "Bitrate", "Save to Device", "Share Video"))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Violet, Pink))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("VISO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp, letterSpacing = 2.sp)
            Text("CREATOR STUDIO", color = Color(0xFF9AA4B8), fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Surface(shape = CircleShape, color = Panel) {
            Icon(Icons.Default.Settings, null, tint = Color.LightGray, modifier = Modifier.padding(10.dp).size(19.dp))
        }
    }
}

@Composable
private fun StudioPage(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    selectedTool: String,
    onTool: (String) -> Unit,
    intensity: Float,
    onIntensity: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("My Project", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Untitled • 9:16 vertical", color = Color(0xFF9AA4B8), fontSize = 12.sp)
        }
        Text("AUTO-SAVED", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
    PreviewStage(isPlaying)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Add Media")
        }
        OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Image, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Add PNG")
        }
    }
    Spacer(Modifier.height(18.dp))
    SectionTitle("QUICK TOOLBOX", "All tools grouped by task")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Visualizer", "Layers", "Audio", "Lyrics", "Effects", "Green Screen").forEach { tool ->
            val active = selectedTool == tool
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (active) Violet.copy(alpha = 0.22f) else Panel,
                modifier = Modifier.border(1.dp, if (active) Violet else Color(0xFF283044), RoundedCornerShape(12.dp))
                    .clickable { onTool(tool) }
            ) {
                Text(tool, color = if (active) Color.White else Color(0xFFB7C0D2), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selectedTool, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("OPEN PANEL  ↗", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (selectedTool) {
                    "Visualizer" -> "3D spectrum • Circular • Waveform • Beat response"
                    "Layers" -> "Background image • Foreground PNG • Stickers • Blend"
                    "Audio" -> "Pitch • Tempo • BPM • Bass • Per-clip volume"
                    "Lyrics" -> "Auto transcript • Language • Word sync • Emoji"
                    "Effects" -> "Glow • Particles • Transitions • Color grade"
                    else -> "Chroma key • Subject cutout • Replace background"
                },
                color = Color(0xFF9AA4B8), fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("Beat sensitivity", color = Color.White, fontSize = 11.sp)
            Slider(value = intensity, onValueChange = onIntensity, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
        }
    }
    Spacer(Modifier.height(18.dp))
    SectionTitle("MULTI-TRACK TIMELINE", "00:12 / 00:30")
    TimelineTrack("VIDEO", listOf(Color(0xFF6756A8), Color(0xFF347B88), Color(0xFF9A557B)))
    TimelineTrack("AUDIO", listOf(Mint, Color(0xFF278EAF), Mint, Color(0xFF278EAF)))
    TimelineTrack("PNG / TEXT", listOf(Pink, Violet))
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Clip properties", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Selected: Audio 01", color = Mint, fontSize = 10.sp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PropertyChip("Volume", "100%", Modifier.weight(1f))
        PropertyChip("Pitch", "+0", Modifier.weight(1f))
        PropertyChip("Tempo", "1.0×", Modifier.weight(1f))
        PropertyChip("BPM", "Auto", Modifier.weight(1f))
    }
}

@Composable
private fun PreviewStage(isPlaying: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.88f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241A4A), Color(0xFF091B2C), Color(0xFF25152D))))
            .border(1.dp, Color(0xFF51447B), RoundedCornerShape(24.dp))
    ) {
        val transition = rememberInfiniteTransition(label = "preview")
        val pulse by transition.animateFloat(
            initialValue = 0.82f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse"
        )
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.45f)
            val radius = size.minDimension * 0.245f * if (isPlaying) pulse else 1f
            drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = 0.3f), Color.Transparent), center, radius * 1.7f), radius * 1.7f, center)
            drawCircle(Color(0xFFB78CFF), radius, center, style = Stroke(width = 5.dp.toPx()))
            drawCircle(Mint.copy(alpha = 0.8f), radius * 1.12f, center, style = Stroke(width = 1.5.dp.toPx()))
            for (i in 0 until 64) {
                val angle = Math.PI * 2.0 * i / 64.0
                val wave = if (isPlaying) (0.65 + 0.35 * kotlin.math.sin(i * 1.7 + pulse * 5)).toFloat() else 0.55f
                val inner = radius * 1.08f
                val outer = radius * (1.08f + 0.12f * wave)
                val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner)
                val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer)
                drawLine(if (i % 2 == 0) Mint else Pink, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(94.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF6B4FA7), Color(0xFF163F58)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("YOUR ARTWORK", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 12.sp)
            Text("Visualizer + PNG + Lyrics", color = Color(0xFFD0C8E8), fontSize = 10.sp)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            color = Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)
        ) { Text("LIVE PREVIEW", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clickable { },
            color = Color.Black.copy(alpha = 0.48f), shape = CircleShape
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 17.dp, vertical = 9.dp)) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isPlaying) "Preview playing" else "Preview paused", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TimelineTrack(label: String, colors: List<Color>) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF9AA4B8), fontSize = 8.sp, modifier = Modifier.width(62.dp), fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF0E1320)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            colors.forEachIndexed { index, color ->
                Box(
                    Modifier.weight(if (index == 1) 1.5f else 1f).fillMaxSize()
                        .clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.72f))
                )
            }
        }
    }
}

@Composable
private fun PropertyChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(9.dp)
    ) {
        Text(label, color = Color(0xFF9AA4B8), fontSize = 9.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), letterSpacing = 0.6.sp)
        Text(detail, color = Color(0xFF8E99AE), fontSize = 9.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ToolPage(title: String, subtitle: String, tools: List<String>) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    Text(subtitle, color = Color(0xFF9AA4B8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
    tools.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                Card(
                    modifier = Modifier.weight(1f).padding(bottom = 10.dp).clickable { },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(Icons.Default.Tune, null, tint = Violet, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(item, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Tool panel", color = Color(0xFF9AA4B8), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
