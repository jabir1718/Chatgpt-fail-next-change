package com.creatorstudio.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import androidx.core.content.ContextCompat
import com.creatorstudio.app.audio.AudioReactiveEngine
import com.creatorstudio.app.audio.TimelineAudioPreviewController
import com.creatorstudio.app.audio.WaveformRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.creatorstudio.app.editor.EditorProject
import com.creatorstudio.app.editor.MediaKind
import com.creatorstudio.app.editor.VisualizerSettings
import com.creatorstudio.app.editor.ProjectStore
import com.creatorstudio.app.editor.ProjectSummary
import com.creatorstudio.app.editor.duplicateProject
import com.creatorstudio.app.editor.updateProjectName
import com.creatorstudio.app.editor.pitchRatio
import com.creatorstudio.app.editor.TimelineEditor
import com.creatorstudio.app.editor.createStarterProject
import com.creatorstudio.app.editor.importAsset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
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
    var spectrumLevels by remember { mutableStateOf(List(32) { 0f }) }
    var waveformSamples by remember { mutableStateOf(emptyList<Float>()) }
    var visualizerRunning by remember { mutableStateOf(false) }
    var detectedBpm by remember { mutableStateOf<Int?>(null) }
    var beatPulseCount by remember { mutableIntStateOf(0) }
    var visualizerMessage by remember { mutableStateOf("Select audio/video, start playback, then start live analysis.") }
    val undoStack = remember { mutableStateListOf<EditorProject>() }
    val redoStack = remember { mutableStateListOf<EditorProject>() }
    var savedProjects by remember { mutableStateOf<List<ProjectSummary>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val projectStore = remember(context) { ProjectStore(context) }
    val waveformRepository = remember(context) { WaveformRepository(context) }
    var cachedWaveforms by remember { mutableStateOf<Map<String, List<Float>>>(emptyMap()) }
    val persistenceMutex = remember { Mutex() }

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
    var timelineMixing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import video, audio or PNG to begin") }
    val player = remember { ExoPlayer.Builder(context).build() }
    val timelineAudioPreview = remember(context) { TimelineAudioPreviewController(context) }
    val audioReactiveEngine = remember {
        AudioReactiveEngine(
            onSpectrum = { spectrumLevels = it },
            onWaveform = { waveformSamples = it },
            onBeat = { beatPulseCount++ },
            onBpm = { detectedBpm = it },
            onError = { visualizerMessage = it; visualizerRunning = false }
        )
    }

    LaunchedEffect(selectedClipId, project.id) {
        val clip = project.tracks.asSequence()
            .flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == selectedClipId }
        val asset = clip?.let { selected -> project.assets.firstOrNull { it.id == selected.mediaId } }
        isPlaying = false
        player.pause()
        if (clip == null || asset == null || asset.kind == MediaKind.IMAGE) {
            player.stop()
        } else {
            player.setMediaItem(mediaItemFor(Uri.parse(asset.uri), asset.mimeType))
            player.prepare()
            player.seekTo(clip.sourceStartMs)
            val owningTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == clip.id } }
            player.volume = if (clip.audio.muted || owningTrack?.muted == true) 0f else clip.audio.volume.coerceIn(0f, 1f)
            player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, clip.audio.pitchRatio())
        }
    }

    fun activateProject(loaded: EditorProject) {
        project = loaded
        media.clear()
        loaded.assets.forEach { asset -> media.add(StudioMedia(Uri.parse(asset.uri), asset.displayName, asset.kind.name)) }
        val firstClip = loaded.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull()
        selectedClipId = firstClip?.id
        val firstAsset = loaded.assets.firstOrNull { it.id == firstClip?.mediaId }
        selectedIndex = media.indexOfFirst { it.uri.toString() == firstAsset?.uri }
        playheadMs = firstClip?.timelineStartMs ?: 0L
        player.stop()
        isPlaying = false
        if (firstAsset != null && firstAsset.kind != MediaKind.IMAGE) {
            player.setMediaItem(mediaItemFor(Uri.parse(firstAsset.uri), firstAsset.mimeType))
            player.prepare()
            val clip = firstClip
            if (clip != null) {
                player.volume = if (clip.audio.muted) 0f else clip.audio.volume.coerceIn(0f, 1f)
                player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, clip.audio.pitchRatio())
            }
        }
    }

    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) {
            runCatching { projectStore.listProjects().firstOrNull()?.let { projectStore.load(it.id) } }.getOrNull()
        }
        if (restored != null) activateProject(restored)
        projectLoaded = true
    }

    LaunchedEffect(project.assets) {
        val audioAssets = project.assets.filter { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO }
        for (asset in audioAssets) {
            if (cachedWaveforms.containsKey(asset.id)) continue
            val duration = asset.durationMs ?: continue
            val waveform = waveformRepository.getOrCreate(asset.uri, duration)
            if (waveform != null) cachedWaveforms = cachedWaveforms + (asset.id to waveform.samples)
        }
    }

    LaunchedEffect(project, projectLoaded) {
        if (projectLoaded) {
            val snapshot = project
            val result = persistenceMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        projectStore.save(snapshot)
                        projectStore.listProjects()
                    }
                }
            }
            result.onSuccess { savedProjects = it }
                .onFailure { status = "Project save failed: ${it.message ?: "storage error"}" }
        }
    }

    LaunchedEffect(isPlaying, selectedClipId, timelineMixing) {
        while (isPlaying && !timelineMixing) {
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
                player.setMediaItem(mediaItemFor(stableUri, details.second))
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

    LaunchedEffect(timelineMixing, project) {
        while (timelineMixing) {
            val nextPosition = timelineAudioPreview.update(project)
            playheadMs = nextPosition
            if (!timelineAudioPreview.isPlaying) {
                timelineMixing = false
                isPlaying = false
                player.volume = volume
                status = "Timeline audio preview finished"
            }
            delay(60)
        }
    }

    DisposableEffect(player, timelineAudioPreview) {
        onDispose {
            audioReactiveEngine.stop()
            timelineAudioPreview.release()
            player.release()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 2 && visualizerRunning) {
            audioReactiveEngine.stop()
            visualizerRunning = false
            visualizerMessage = "Live analysis stopped when leaving Visualizer Studio."
        }
    }

    fun selectMedia(item: StudioMedia) {
        val index = media.indexOf(item)
        selectedIndex = index
        if (item.kind != "IMAGE") {
            val mimeType = project.assets.firstOrNull { it.uri == item.uri.toString() }?.mimeType
        player.setMediaItem(mediaItemFor(item.uri, mimeType))
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

    fun startLiveVisualizer() {
        val active = media.getOrNull(selectedIndex)
        if (active == null || active.kind == "IMAGE") {
            visualizerMessage = "Import and select an audio or video source first."
            return
        }
        if (!player.isPlaying) {
            visualizerMessage = "Press Play on the selected source, then start live analysis."
            return
        }
        val started = audioReactiveEngine.start(player.audioSessionId)
        visualizerRunning = started
        if (started) visualizerMessage = "Live FFT and waveform capture active."
    }

    val visualizerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLiveVisualizer()
        else visualizerMessage = "Audio capture permission was denied. Enable it to use live analysis."
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
            if (selectedTab == 0) {
                ProjectManagerPanel(
                    projectName = project.name,
                    projects = savedProjects,
                    onNew = {
                        scope.launch {
                            val saved = persistenceMutex.withLock {
                                withContext(Dispatchers.IO) { runCatching { projectStore.save(project) } }
                            }
                            if (saved.isFailure) {
                                status = "Could not save current project: ${saved.exceptionOrNull()?.message}"
                            } else {
                                undoStack.clear(); redoStack.clear()
                                activateProject(createStarterProject())
                                status = "New project created"
                            }
                        }
                    },
                    onRename = { name -> commitProject(project.updateProjectName(name)) },
                    onOpen = { id ->
                        scope.launch {
                            val loaded = persistenceMutex.withLock {
                                withContext(Dispatchers.IO) {
                                    projectStore.save(project)
                                    projectStore.load(id)
                                }
                            }
                            undoStack.clear(); redoStack.clear()
                            activateProject(loaded)
                            status = "Opened ${loaded.name}"
                        }
                    },
                    onRefresh = {
                        scope.launch {
                            savedProjects = withContext(Dispatchers.IO) { projectStore.listProjects() }
                        }
                    },
                    onDuplicate = {
                        scope.launch {
                            val copy = persistenceMutex.withLock {
                                withContext(Dispatchers.IO) {
                                    projectStore.save(project)
                                    project.duplicateProject()
                                }
                            }
                            undoStack.clear(); redoStack.clear()
                            activateProject(copy)
                            status = "Project duplicated"
                        }
                    },
                    onDelete = {
                        val deletedId = project.id
                        undoStack.clear(); redoStack.clear()
                        activateProject(createStarterProject())
                        scope.launch {
                            val deleted = persistenceMutex.withLock {
                                withContext(Dispatchers.IO) { projectStore.delete(deletedId) }
                            }
                            status = if (deleted) "Project deleted" else "Could not delete saved project"
                        }
                    }
                )
            }
            when (selectedTab) {
                0 -> StudioPage(
                    media = media, selectedIndex = selectedIndex, player = player,
                    projectName = project.name,
                    canvasWidth = project.width, canvasHeight = project.height,
                    onCanvasChange = { width, height -> commitProject(project.copy(width = width, height = height, updatedAtEpochMs = System.currentTimeMillis())) },
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
                            player.seekTo(selectedClip.sourceStartMs)
                            playheadMs = selectedClip.timelineStartMs
                            player.volume = if (selectedClip.audio.muted) 0f else selectedClip.audio.volume.coerceIn(0f, 1f)
                            player.playbackParameters = PlaybackParameters(selectedClip.audio.playbackRate, selectedClip.audio.pitchRatio())
                        }
                    },
                    onDelete = { index ->
                        val removed = media[index]
                        val asset = project.assets.firstOrNull { it.uri == removed.uri.toString() }
                        if (asset != null) {
                            val nextTracks = project.tracks.map { track ->
                                track.copy(clips = track.clips.filterNot { it.mediaId == asset.id })
                            }
                            val nextVisualizer = project.visualizer.copy(
                                backgroundAssetId = project.visualizer.backgroundAssetId.takeUnless { it == asset.id },
                                foregroundAssetId = project.visualizer.foregroundAssetId.takeUnless { it == asset.id }
                            )
                            commitProject(project.copy(
                                assets = project.assets.filterNot { it.id == asset.id },
                                tracks = nextTracks,
                                visualizer = nextVisualizer
                            ))
                        }
                        if (index == selectedIndex) { player.stop(); isPlaying = false; selectedIndex = -1; selectedClipId = null }
                        media.removeAt(index)
                        if (selectedIndex > index) selectedIndex--
                        status = "Media removed from project timeline"
                    },
                    onPlay = {
                        if (timelineMixing) {
                            playheadMs = timelineAudioPreview.pause()
                            timelineMixing = false
                            isPlaying = false
                            player.volume = volume
                            status = "Timeline audio preview paused"
                        } else if (project.tracks.any { track -> track.clips.any { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO } }) {
                            player.pause()
                            player.volume = 0f
                            timelineAudioPreview.play(project, playheadMs)
                            timelineMixing = timelineAudioPreview.isPlaying
                            isPlaying = timelineMixing
                            status = if (timelineMixing) "Playing all active timeline audio clips" else "No audible clips at this playhead"
                        } else status = "Add an audio or video clip to the timeline first"
                    },
                    volume = volume, onVolume = { volume = it; player.volume = it },
                    pitch = pitch, onPitch = { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) },
                    speed = speed, onSpeed = { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) }
                )
                1 -> AudioPage(volume, { volume = it; player.volume = it }, pitch, { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) }, speed, { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) })
                2 -> VisualizerPage(
                    spectrum = spectrumLevels,
                    waveform = waveformSamples,
                    running = visualizerRunning,
                    bpm = detectedBpm,
                    beatPulse = beatPulseCount,
                    message = visualizerMessage,
                    images = project.assets.filter { it.kind == MediaKind.IMAGE },
                    settings = project.visualizer,
                    onSettingsChange = { settings ->
                        commitProject(project.copy(visualizer = settings, updatedAtEpochMs = System.currentTimeMillis()))
                    },
                    onStart = {
                        val active = media.getOrNull(selectedIndex)
                        if (active == null || active.kind == "IMAGE") {
                            visualizerMessage = "Import and select an audio or video source first."
                        } else if (!player.isPlaying) {
                            visualizerMessage = "Press Play on the selected source before starting live analysis."
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startLiveVisualizer()
                        } else {
                            visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStop = {
                        audioReactiveEngine.stop()
                        visualizerRunning = false
                        visualizerMessage = "Live analysis stopped."
                    }
                )
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
                    waveforms = cachedWaveforms,
                    onSelectClip = { clipId ->
                        if (timelineMixing) {
                            timelineAudioPreview.pause()
                            timelineMixing = false
                            isPlaying = false
                            player.volume = volume
                        }
                        selectedClipId = clipId
                        val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == clipId }
                        val asset = project.assets.firstOrNull { it.id == clip?.mediaId }
                        val item = media.firstOrNull { it.uri.toString() == asset?.uri }
                        if (item != null) selectMedia(item)
                        if (clip != null) {
                            playheadMs = clip.timelineStartMs
                            player.seekTo(clip.sourceStartMs)
                            val owningTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == clip.id } }
                            player.volume = if (clip.audio.muted || owningTrack?.muted == true) 0f else clip.audio.volume.coerceIn(0f, 1f)
                            player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, clip.audio.pitchRatio())
                        }
                    },
                    onPlayheadChange = { next ->
                        playheadMs = next
                        if (timelineMixing) {
                            timelineAudioPreview.seek(project, next)
                        } else {
                            val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
                            if (clip != null) player.seekTo((clip.sourceStartMs + (next - clip.timelineStartMs).coerceAtLeast(0L)).coerceIn(clip.sourceStartMs, clip.sourceEndMs))
                        }
                    },
                    onProjectChange = { next ->
                        val activeClip = next.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
                        if (activeClip != null) {
                            val owningTrack = next.tracks.firstOrNull { track -> track.clips.any { it.id == activeClip.id } }
                            player.volume = if (activeClip.audio.muted || owningTrack?.muted == true) 0f else activeClip.audio.volume.coerceIn(0f, 1f)
                            player.playbackParameters = PlaybackParameters(activeClip.audio.playbackRate, activeClip.audio.pitchRatio())
                        }
                        commitProject(next)
                    },
                    onAddTrack = { name, kind ->
                        commitProject(TimelineEditor.addTrack(project, name, kind))
                        status = "$name track added"
                    },
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
    projectName: String, canvasWidth: Int, canvasHeight: Int, onCanvasChange: (Int, Int) -> Unit,
    onAddVideo: () -> Unit, onAddAudio: () -> Unit, onAddImage: () -> Unit,
    onSelect: (StudioMedia) -> Unit, onDelete: (Int) -> Unit, onPlay: () -> Unit,
    volume: Float, onVolume: (Float) -> Unit, pitch: Float, onPitch: (Float) -> Unit,
    speed: Float, onSpeed: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(projectName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${media.size} imported assets • ${canvasWidth}×${canvasHeight}", color = Color(0xFF9AA4B8), fontSize = 11.sp)
        }
        Text("LOCAL PROJECT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple("9:16", 1080, 1920), Triple("1:1", 1080, 1080), Triple("4:5", 1080, 1350), Triple("16:9", 1920, 1080)).forEach { preset ->
            val selected = canvasWidth == preset.second && canvasHeight == preset.third
            OutlinedButton(
                onClick = { onCanvasChange(preset.second, preset.third) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Mint else Color.White)
            ) { Text(preset.first, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
        }
    }
    Spacer(Modifier.height(12.dp))
    PreviewStage(player, media.getOrNull(selectedIndex), isPlaying, onPlay, canvasWidth.toFloat() / canvasHeight.toFloat())
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
    Text("Global sliders affect the active preview. Per-clip volume, mute, pitch and tempo are saved from the selected timeline clip. Export DSP is not connected yet.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
}

@Composable
private fun PreviewStage(player: ExoPlayer, item: StudioMedia?, isPlaying: Boolean, onPlay: () -> Unit, aspectRatio: Float) {
    val context = LocalContext.current
    val selectedImageUri = item?.takeIf { it.kind == "IMAGE" }?.uri
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = selectedImageUri) {
        val uri = selectedImageUri
        value = if (uri != null) withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val largestSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val sample = (largestSide / 1600).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        } else null
    }
    Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 300.dp).aspectRatio(aspectRatio)
        .clip(RoundedCornerShape(22.dp)).background(Color(0xFF111827))
        .border(1.dp, Color(0xFF51447B), RoundedCornerShape(22.dp))) {
        when (item?.kind) {
            "VIDEO" -> AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxSize())
            "IMAGE" -> {
                if (imageBitmap != null) {
                    Image(bitmap = imageBitmap!!, contentDescription = item?.name ?: "Image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Loading image preview…", color = Color(0xFFB9C2D5), modifier = Modifier.align(Alignment.Center))
                }
            }
            "AUDIO" -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.GraphicEq, null, tint = Mint, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(14.dp))
                Text(item?.name ?: "Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text("Audio source ready • open Visualizer Studio for live FFT", color = Color(0xFFB9C2D5), fontSize = 10.sp)
            }
            else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.GraphicEq, null, tint = Mint, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(14.dp))
                Text("YOUR CREATIVE CANVAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Import video, audio or PNG to begin", color = Color(0xFFB9C2D5), fontSize = 10.sp)
            }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
            Text("PREVIEW", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
        }
        if (item?.kind == "AUDIO") {
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
    Text("Live beat/BPM estimate is available in Visualizer Studio. Per-clip volume, mute, pitch and tempo are saved in the timeline; these are currently preview settings.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
    ToolPage("Additional audio processors (planned)", "These processors need a dedicated DSP and offline export path", listOf("Parametric EQ", "Bass & EQ", "Voice Changer", "Noise Reduction", "Reverb & Echo", "Compressor", "Vocal Isolation", "Audio Recording"))
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

private fun mediaItemFor(uri: Uri, mimeType: String?): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
    return builder.build()
}
