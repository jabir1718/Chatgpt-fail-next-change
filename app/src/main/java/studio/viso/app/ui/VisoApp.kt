package com.creatorstudio.app.ui

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.creatorstudio.app.editor.EditorProject
import com.creatorstudio.app.editor.MediaKind
import com.creatorstudio.app.editor.ProjectStore
import com.creatorstudio.app.editor.TimelineEditor
import com.creatorstudio.app.editor.createStarterProject
import com.creatorstudio.app.editor.importAsset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect

private val Ink = Color(0xFF080A12)
private val Panel = Color(0xFF141A29)
private val Violet = Color(0xFF9B7BFF)
private val Mint = Color(0xFF34D8C5)
private val Pink = Color(0xFFFF6FAE)

private data class StudioMedia(val uri: Uri, val name: String, val kind: String)

@Composable
fun CreatorStudioApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Studio", "Audio", "Visuals", "Lyrics", "Export")
    val context = LocalContext.current
    val media = remember { mutableStateListOf<StudioMedia>() }
    var project by remember { mutableStateOf(createStarterProject()) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var projectLoaded by remember { mutableStateOf(false) }
    val undoStack = remember { mutableStateListOf<EditorProject>() }
    val redoStack = remember { mutableStateListOf<EditorProject>() }
    val scope = rememberCoroutineScope()
    val projectStore = remember(context) { ProjectStore(context) }

    fun commitProject(next: EditorProject) {
        if (next == project) return
        undoStack.add(project)
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        project = next
    }

    fun undoProject() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(project)
            project = undoStack.removeAt(undoStack.lastIndex)
        }
    }

    fun redoProject() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(project)
            project = redoStack.removeAt(redoStack.lastIndex)
        }
    }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var volume by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import video, audio or PNG to begin") }
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) {
            runCatching { projectStore.listProjects().firstOrNull()?.let { projectStore.load(it.id) } }.getOrNull()
        }
        if (restored != null) {
            project = restored
            media.clear()
            restored.assets.forEach { asset ->
                media.add(StudioMedia(Uri.parse(asset.uri), asset.displayName, asset.kind.name))
            }
            selectedClipId = restored.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull()?.id
            selectedIndex = media.indexOfFirst { item -> item.uri.toString() == restored.assets.firstOrNull { it.id == restored.tracks.asSequence().flatMap { row -> row.clips.asSequence() }.firstOrNull()?.mediaId }?.uri }
        }
        projectLoaded = true
    }

    LaunchedEffect(project, projectLoaded) {
        if (projectLoaded) withContext(Dispatchers.IO) { runCatching { projectStore.save(project) } }
    }

    LaunchedEffect(isPlaying, selectedClipId) {
        while (isPlaying) {
            val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
            if (clip != null) {
                val position = player.currentPosition
                if (position >= clip.sourceEndMs) {
                    player.pause()
                    isPlaying = false
                    playheadMs = clip.timelineEndMs
                } else {
                    playheadMs = clip.timelineStartMs + (position - clip.sourceStartMs).coerceAtLeast(0L)
                }
            }
            delay(180)
        }
    }

    fun importPickedMedia(uri: Uri, kind: MediaKind) {
        scope.launch {
            status = "Reading media information…"
            val details = withContext(Dispatchers.IO) {
                val name = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Imported media"
                val mime = context.contentResolver.getType(uri)
                val duration = if (kind == MediaKind.IMAGE) 5_000L else runCatching {
                    MediaMetadataRetriever().let { retriever ->
                        try {
                            retriever.setDataSource(context, uri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        } finally {
                            retriever.release()
                        }
                    }
                }.getOrNull()
                Triple(name, mime, duration)
            }
            val duration = details.third?.takeIf { it > 0L } ?: if (kind == MediaKind.IMAGE) 5_000L else 10_000L
            val stableUri = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    uri
                } catch (_: Exception) {
                    val extension = when (kind) {
                        MediaKind.VIDEO -> "mp4"
                        MediaKind.AUDIO -> "audio"
                        MediaKind.IMAGE -> "img"
                        MediaKind.TEXT -> "txt"
                    }
                    val destination = File(context.filesDir, "creator-media/${UUID.randomUUID()}.$extension")
                    destination.parentFile?.mkdirs()
                    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Unable to read selected media")
                    if (copied <= 0L) throw IllegalStateException("Selected media is empty")
                    Uri.fromFile(destination)
                }
            }
            val imported = project.importAsset(stableUri, details.first, kind, duration, details.second)
            commitProject(imported.project)
            val kindLabel = when (kind) { MediaKind.VIDEO -> "VIDEO"; MediaKind.AUDIO -> "AUDIO"; else -> "IMAGE" }
            val item = StudioMedia(stableUri, details.first, kindLabel)
            media.add(item)
            selectedIndex = media.lastIndex
            selectedClipId = imported.clipId
            playheadMs = 0L
            if (kind != MediaKind.IMAGE) {
                player.setMediaItem(MediaItem.fromUri(stableUri))
                player.prepare()
                player.volume = volume
                player.playbackParameters = PlaybackParameters(speed, pitch)
                isPlaying = false
            } else {
                player.pause()
                isPlaying = false
            }
            status = if (details.third == null && kind != MediaKind.IMAGE)
                "Imported • duration estimate used because source metadata was unavailable"
            else "Imported into ${kindLabel.lowercase()} timeline"
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    fun selectMedia(item: StudioMedia) {
        val index = media.indexOf(item)
        selectedIndex = index
        if (item.kind != "IMAGE") {
            player.setMediaItem(MediaItem.fromUri(item.uri))
            player.prepare()
            player.volume = volume
            player.playbackParameters = PlaybackParameters(speed, pitch)
            isPlaying = false
            status = "Ready • ${item.name}"
        } else {
            player.pause()
            isPlaying = false
            status = "Image layer selected • ${item.name}"
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPickedMedia(it, MediaKind.VIDEO) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPickedMedia(it, MediaKind.AUDIO) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPickedMedia(it, MediaKind.IMAGE) }
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101522), contentColor = Color.White) {
                val icons = listOf(Icons.Default.Movie, Icons.Default.GraphicEq, Icons.Default.AutoAwesome, Icons.Default.Subtitles, Icons.Default.LibraryMusic)
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(selected = selectedTab == index, onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) })
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF11172A), Ink)))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Header()
            when (selectedTab) {
                0 -> StudioPage(
                    media = media, selectedIndex = selectedIndex, player = player,
                    isPlaying = isPlaying, status = status,
                    onAddVideo = { videoPicker.launch(arrayOf("video/*")) },
                    onAddAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onAddImage = { imagePicker.launch(arrayOf("image/*")) },
                    onSelect = { item ->
                        selectMedia(item)
                        selectedClipId = project.assets.firstOrNull { it.uri == item.uri.toString() }?.let { asset ->
                            project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.mediaId == asset.id }?.id
                        }
                        val selectedClip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
                        if (selectedClip != null) {
                            player.volume = if (selectedClip.audio.muted) 0f else selectedClip.audio.volume.coerceIn(0f, 1f)
                            player.playbackParameters = PlaybackParameters(selectedClip.audio.playbackRate, 1f)
                        }
                    },
                    onDelete = { index ->
                        val removed = media[index]
                        val asset = project.assets.firstOrNull { it.uri == removed.uri.toString() }
                        if (asset != null) {
                            val nextTracks = project.tracks.map { track ->
                                track.copy(clips = track.clips.filterNot { it.mediaId == asset.id })
                            }
                            commitProject(project.copy(assets = project.assets.filterNot { it.id == asset.id }, tracks = nextTracks))
                        }
                        if (index == selectedIndex) { player.stop(); isPlaying = false; selectedIndex = -1; selectedClipId = null }
                        media.removeAt(index)
                        if (selectedIndex > index) selectedIndex--
                        status = "Media removed from project timeline"
                    },
                    onPlay = {
                        if (selectedIndex >= 0 && media.getOrNull(selectedIndex)?.kind != "IMAGE") {
                            if (player.isPlaying) { player.pause(); isPlaying = false }
                            else { player.play(); isPlaying = true }
                        } else status = "Select an audio or video clip first"
                    },
                    volume = volume, onVolume = { volume = it; player.volume = it },
                    pitch = pitch, onPitch = { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) },
                    speed = speed, onSpeed = { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) }
                )
                1 -> AudioPage(volume, { volume = it; player.volume = it }, pitch, { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) }, speed, { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) })
                2 -> ToolPage("Visualizer Studio", "Visual design controls • audio-reactive engine is a later milestone", listOf("Spectrum Visualizer", "Circular / Radial", "Waveform", "3D Particles", "Background Artwork", "Foreground PNG", "Beat Pulse", "Green Screen"))
                3 -> ToolPage("AI Lyrics & Captions", "Transcription integration is not connected yet", listOf("Auto Transcribe", "Language Detection", "Roman Urdu", "Urdu Script", "Roman English", "Word Sync", "Emoji Captions", "Voice Typing"))
                else -> ToolPage("Export & Share", "Render/export engine is not connected yet", listOf("TikTok 9:16", "YouTube Shorts", "Instagram Reels", "1080p / 4K", "Frame Rate", "Bitrate", "Save to Device", "Share Video"))
            }
            if (selectedTab == 0) {
                Spacer(Modifier.height(14.dp))
                TimelinePanel(
                    project = project,
                    selectedClipId = selectedClipId,
                    playheadMs = playheadMs,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onSelectClip = { clipId ->
                        selectedClipId = clipId
                        val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == clipId }
                        val asset = project.assets.firstOrNull { it.id == clip?.mediaId }
                        val item = media.firstOrNull { it.uri.toString() == asset?.uri }
                        if (item != null) selectMedia(item)
                        if (clip != null) {
                            playheadMs = clip.timelineStartMs
                            player.seekTo(clip.sourceStartMs)
                            player.volume = if (clip.audio.muted) 0f else clip.audio.volume.coerceIn(0f, 1f)
                            player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, 1f)
                        }
                    },
                    onPlayheadChange = { next ->
                        playheadMs = next
                        val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
                        if (clip != null) player.seekTo((clip.sourceStartMs + (next - clip.timelineStartMs).coerceAtLeast(0L)).coerceIn(clip.sourceStartMs, clip.sourceEndMs))
                    },
                    onProjectChange = { commitProject(it) },
                    onUndo = { undoProject() },
                    onRedo = { redoProject() }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Violet, Pink))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GraphicEq, null, tint = Color.White)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("CREATOR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.5.sp)
            Text("STUDIO", color = Color(0xFF9AA4B8), fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Surface(shape = CircleShape, color = Panel) { Text("V1", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) }
    }
}

@Composable
private fun StudioPage(
    media: List<StudioMedia>, selectedIndex: Int, player: ExoPlayer, isPlaying: Boolean, status: String,
    onAddVideo: () -> Unit, onAddAudio: () -> Unit, onAddImage: () -> Unit,
    onSelect: (StudioMedia) -> Unit, onDelete: (Int) -> Unit, onPlay: () -> Unit,
    volume: Float, onVolume: (Float) -> Unit, pitch: Float, onPitch: (Float) -> Unit,
    speed: Float, onSpeed: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("My Project", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${media.size} imported assets • 9:16 vertical", color = Color(0xFF9AA4B8), fontSize = 11.sp)
        }
        Text("LOCAL PROJECT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))
    PreviewStage(player, media.getOrNull(selectedIndex), isPlaying, onPlay)
    Text(status, color = Mint, fontSize = 10.sp, modifier = Modifier.padding(vertical = 7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onAddVideo, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Violet), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp)) {
            Icon(Icons.Default.Movie, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Video", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onAddAudio, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp)) {
            Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Audio", fontSize = 11.sp)
        }
        OutlinedButton(onClick = onAddImage, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp)) {
            Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("PNG", fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionTitle("MEDIA BIN", "Timeline below")
    if (media.isEmpty()) {
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Your imported video, audio and image layers will appear here.", color = Color(0xFF9AA4B8), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
    } else {
        media.forEachIndexed { index, item ->
            val color = when (item.kind) { "VIDEO" -> Violet; "AUDIO" -> Mint; else -> Pink }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (index == selectedIndex) color.copy(alpha = 0.18f) else Panel)
                    .border(1.dp, if (index == selectedIndex) color else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(item) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(when (item.kind) { "VIDEO" -> Icons.Default.Movie; "AUDIO" -> Icons.Default.AudioFile; else -> Icons.Default.Image }, null, tint = color)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(item.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    Text(item.kind + " LAYER  •  " + (index + 1), color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onDelete(index) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFF9AA4B8)) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SectionTitle("PREVIEW AUDIO", "Active source")
    ControlSlider("Volume", volume, "0–100%", onVolume)
    ControlSlider("Pitch", pitch, "0.5×–2×", onPitch, 0.5f..2f)
    ControlSlider("Tempo", speed, "0.5×–2×", onSpeed, 0.5f..2f)
    Text("Pitch/tempo controls currently affect the active playback preview. Per-clip saved automation and export processing are not yet implemented.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
}

@Composable
private fun PreviewStage(player: ExoPlayer, item: StudioMedia?, isPlaying: Boolean, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.88f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111827)).border(1.dp, Color(0xFF51447B), RoundedCornerShape(22.dp))) {
        if (item?.kind == "VIDEO") {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                val bars = remember { List(36) { (0.2f + (it % 7) * 0.1f).coerceAtMost(0.9f) } }
                Row(Modifier.height(110.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    bars.forEachIndexed { i, h ->
                        Box(Modifier.width(5.dp).height((if (isPlaying) h * 110f else h * 75f).dp).clip(CircleShape).background(if (i % 3 == 0) Pink else Mint))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(item?.name ?: "YOUR CREATIVE CANVAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text(when (item?.kind) { "AUDIO" -> "Audio loaded • visualizer engine next"; "IMAGE" -> "Image layer imported"; else -> "Import video, audio or PNG to begin" }, color = Color(0xFFB9C2D5), fontSize = 10.sp)
            }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
            Text("PREVIEW", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
        }
        if (item?.kind != "VIDEO") {
            FilledIconButton(onClick = onPlay, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AudioPage(volume: Float, onVolume: (Float) -> Unit, pitch: Float, onPitch: (Float) -> Unit, speed: Float, onSpeed: (Float) -> Unit) {
    Text("Audio Lab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    Text("Live preview controls for the selected audio/video source", color = Color(0xFF9AA4B8), fontSize = 12.sp)
    Spacer(Modifier.height(18.dp))
    ControlSlider("Volume / Gain", volume, "0–100%", onVolume)
    ControlSlider("Pitch", pitch, "0.5×–2×", onPitch, 0.5f..2f)
    ControlSlider("Tempo", speed, "0.5×–2×", onSpeed, 0.5f..2f)
    ToolPage("Audio processing (planned)", "These processors need their own DSP implementation and export path", listOf("BPM Detection", "Bass & EQ", "Voice Changer", "Noise Reduction", "Reverb & Echo", "Compressor", "Vocal Isolation", "Audio Recording"))
}

@Composable
private fun ControlSlider(label: String, value: Float, rangeLabel: String, onValue: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(if (label == "Volume" || label == "Volume / Gain") "${(value * 100).toInt()}%" else "${"%.2f".format(value)}×", color = Mint, fontSize = 11.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValue, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
        Text(rangeLabel, color = Color(0xFF7F8BA2), fontSize = 9.sp)
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), letterSpacing = 0.5.sp)
        Text(detail, color = Color(0xFF8E99AE), fontSize = 9.sp)
    }
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun ToolPage(title: String, subtitle: String, tools: List<String>) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    Text(subtitle, color = Color(0xFF9AA4B8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
    tools.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { item ->
                Card(Modifier.weight(1f).padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Icon(Icons.Default.Tune, null, tint = Violet, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(item, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Planned module", color = Color(0xFF9AA4B8), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
