package com.creatorstudio.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.media.AudioManager
import android.graphics.BitmapFactory
import android.view.TextureView
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.ContextCompat
import com.creatorstudio.app.audio.AudioReactiveEngine
import com.creatorstudio.app.audio.AndroidAudioProcessor
import com.creatorstudio.app.audio.TimelineAudioPreviewController
import com.creatorstudio.app.audio.TimelineVideoPreviewController
import com.creatorstudio.app.audio.TimelineVideoPreviewLayer
import com.creatorstudio.app.audio.WaveformRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.creatorstudio.app.editor.AudioProcessingSettings
import com.creatorstudio.app.editor.CanvasContentMode
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
import com.creatorstudio.app.editor.OfflineAudioExportPipeline
import com.creatorstudio.app.editor.OfflineVisualizerVideoExportPipeline
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect

private val Ink = Color(0xFF080A12)
private val Panel = Color(0xFF141A29)
private val Violet = Color(0xFF9B7BFF)
private val Mint = Color(0xFF34D8C5)
private val Pink = Color(0xFFFF6FAE)

private data class StudioMedia(val uri: Uri, val name: String, val kind: String)

@OptIn(UnstableApi::class)
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
    var audioExporting by remember { mutableStateOf(false) }
    var audioExportCancel by remember { mutableStateOf<AtomicBoolean?>(null) }
    var audioExportProgress by remember { mutableFloatStateOf(0f) }
    var audioExportMessage by remember { mutableStateOf("Choose a destination to export the timeline audio mix.") }
    var exportRetryKind by remember { mutableStateOf<String?>(null) }

    val audioMixExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { destinationUri ->
        if (destinationUri != null && !audioExporting) {
            val exportProject = project
            val cancelFlag = AtomicBoolean(false)
            scope.launch {
                audioExportCancel = cancelFlag
                audioExporting = true
                audioExportProgress = 0f
                audioExportMessage = "Preparing offline audio mix..."
                val temporaryOutput = File(context.cacheDir, "creator_audio_mix_${UUID.randomUUID()}.m4a")
                try {
                    withContext(Dispatchers.IO) {
                        val result = OfflineAudioExportPipeline().exportAudioMix(
                            context = context,
                            project = exportProject,
                            cacheDirectory = File(context.cacheDir, "creator-pcm-cache"),
                            outputFile = temporaryOutput,
                            shouldCancel = { cancelFlag.get() },
                            onProgress = { value -> audioExportProgress = value }
                        )
                        val output = context.contentResolver.openOutputStream(destinationUri, "w")
                            ?: error("Could not open the selected save destination")
                        output.use { stream ->
                            result.file.inputStream().use { input -> input.copyTo(stream) }
                        }
                    }
                    exportRetryKind = null
                    audioExportMessage = "Audio mix saved successfully."
                } catch (error: Exception) {
                    if (error is CancellationException) {
                        exportRetryKind = null
                        audioExportMessage = "Export cancelled."
                    } else {
                        exportRetryKind = "audio"
                        audioExportMessage = "Export failed: ${error.message ?: "Unknown error"}"
                    }
                } finally {
                    temporaryOutput.delete()
                    audioExportCancel = null
                    audioExporting = false
                }
            }
        }
    }
    val visualizerVideoExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { destinationUri ->
        if (destinationUri != null && !audioExporting) {
            val exportProject = project
            val cancelFlag = AtomicBoolean(false)
            scope.launch {
                audioExportCancel = cancelFlag
                audioExporting = true
                audioExportProgress = 0f
                audioExportMessage = "Preparing audio-reactive MP4..."
                val temporaryOutput = File(context.cacheDir, "creator_visualizer_${UUID.randomUUID()}.mp4")
                try {
                    withContext(Dispatchers.IO) {
                        val result = OfflineVisualizerVideoExportPipeline().export(
                            context = context,
                            project = exportProject,
                            cacheDirectory = File(context.cacheDir, "creator-video-cache"),
                            videoBitRate = exportProject.videoBitRate,
                            outputFile = temporaryOutput,
                            shouldCancel = { cancelFlag.get() },
                            onProgress = { value -> audioExportProgress = value }
                        )
                        val output = context.contentResolver.openOutputStream(destinationUri, "w")
                            ?: error("Could not open the selected MP4 save destination")
                        output.use { stream ->
                            result.file.inputStream().use { input -> input.copyTo(stream) }
                        }
                    }
                    exportRetryKind = null
                    audioExportMessage = "Visualizer MP4 saved successfully."
                } catch (error: Exception) {
                    if (error is CancellationException) {
                        exportRetryKind = null
                        audioExportMessage = "Export cancelled."
                    } else {
                        exportRetryKind = "video"
                        audioExportMessage = "Video export failed: ${error.message ?: "Unknown error"}"
                    }
                } finally {
                    temporaryOutput.delete()
                    audioExportCancel = null
                    audioExporting = false
                }
            }
        }
    }

    val selectedAudioSessionId = remember(context) {
        runCatching { (context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId() }.getOrDefault(0)
    }
    val player = remember(context, selectedAudioSessionId) {
        ExoPlayer.Builder(context).build().apply {
            if (selectedAudioSessionId > 0) setAudioSessionId(selectedAudioSessionId)
        }
    }
    val selectedAudioProcessor = remember { AndroidAudioProcessor() }
    val timelineAudioPreview = remember(context) { TimelineAudioPreviewController(context) }
    val timelineVideoPreview = remember(context) { TimelineVideoPreviewController(context) }
    var timelineVideoLayers by remember { mutableStateOf(emptyList<TimelineVideoPreviewLayer>()) }
    val audioReactiveEngine = remember {
        AudioReactiveEngine(
            onSpectrum = { spectrumLevels = it },
            onWaveform = { waveformSamples = it },
            onBeat = { beatPulseCount++ },
            onBpm = { detectedBpm = it },
            onError = { visualizerMessage = it; visualizerRunning = false }
        )
    }

    LaunchedEffect(project.audioProcessing, selectedAudioSessionId) {
        if (selectedAudioSessionId > 0) selectedAudioProcessor.apply(selectedAudioSessionId, project.audioProcessing)
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
            val videoSnapshot = timelineVideoPreview.update(project)
            timelineVideoLayers = videoSnapshot.layers
            playheadMs = nextPosition
            if (!timelineAudioPreview.isPlaying) {
                timelineVideoPreview.stop()
                timelineVideoLayers = emptyList()
                timelineMixing = false
                isPlaying = false
                player.volume = volume
                status = "Timeline playback finished"
            }
            delay(60)
        }
    }

    DisposableEffect(player, timelineAudioPreview, timelineVideoPreview, selectedAudioProcessor) {
        onDispose {
            audioReactiveEngine.stop()
            timelineAudioPreview.release()
            timelineVideoPreview.release()
            if (selectedAudioSessionId > 0) selectedAudioProcessor.release(selectedAudioSessionId)
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
                    timelineVideoLayers = timelineVideoLayers, timelineMixing = timelineMixing,
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
                            timelineVideoPreview.pause()
                            timelineVideoLayers = timelineVideoPreview.snapshot().layers
                            timelineMixing = false
                            isPlaying = false
                            player.volume = volume
                            status = "Timeline audio preview paused"
                        } else if (project.tracks.any { track -> track.clips.any { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO } }) {
                            player.pause()
                            player.volume = 0f
                            timelineAudioPreview.play(project, playheadMs)
                            timelineVideoLayers = timelineVideoPreview.play(project, playheadMs).layers
                            timelineMixing = timelineAudioPreview.isPlaying
                            isPlaying = timelineMixing
                            status = if (timelineMixing) "Playing synchronized timeline video and audio clips" else "No audible clips at this playhead"
                        } else status = "Add an audio or video clip to the timeline first"
                    },
                    volume = volume, onVolume = { volume = it; player.volume = it },
                    pitch = pitch, onPitch = { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) },
                    speed = speed, onSpeed = { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) }
                )
                1 -> AudioPage(
                    volume, { volume = it; player.volume = it },
                    pitch, { pitch = it; player.playbackParameters = PlaybackParameters(speed, it) },
                    speed, { speed = it; player.playbackParameters = PlaybackParameters(it, pitch) },
                    settings = project.audioProcessing,
                    onSettingsChange = { next -> commitProject(project.copy(audioProcessing = next, updatedAtEpochMs = System.currentTimeMillis())) }
                )
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
                else -> ExportPage(
                    project = project,
                    onSettingsChange = { next -> commitProject(next.copy(updatedAtEpochMs = System.currentTimeMillis())) },
                    exporting = audioExporting,
                    progress = audioExportProgress,
                    message = audioExportMessage,
                    onCancel = { audioExportCancel?.set(true); audioExportMessage = "Cancelling export..." },
                    onRetry = {
                        when (exportRetryKind) {
                            "audio" -> {
                                val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "CreatorStudio" }
                                audioMixExportLauncher.launch("${safeName}_AudioMix.m4a")
                            }
                            "video" -> {
                                val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "CreatorStudio" }
                                visualizerVideoExportLauncher.launch("${safeName}_Visualizer.mp4")
                            }
                        }
                    },
                    onExportAudio = {
                        val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "CreatorStudio" }
                        audioMixExportLauncher.launch("${safeName}_AudioMix.m4a")
                    },
                    onExportVideo = {
                        val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "CreatorStudio" }
                        visualizerVideoExportLauncher.launch("${safeName}_Visualizer.mp4")
                    }
                )
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
                            timelineVideoPreview.pause()
                            timelineVideoLayers = timelineVideoPreview.snapshot().layers
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
                            timelineVideoLayers = timelineVideoPreview.seek(project, next).layers
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
    timelineVideoLayers: List<TimelineVideoPreviewLayer>, timelineMixing: Boolean,
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
    PreviewStage(player, media.getOrNull(selectedIndex), isPlaying, onPlay, canvasWidth.toFloat() / canvasHeight.toFloat(), timelineVideoLayers, timelineMixing)
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

@OptIn(UnstableApi::class)
@Composable
private fun PreviewStage(
    player: ExoPlayer, item: StudioMedia?, isPlaying: Boolean, onPlay: () -> Unit, aspectRatio: Float,
    timelineVideoLayers: List<TimelineVideoPreviewLayer>, timelineMixing: Boolean
) {
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
        if (timelineMixing) {
            if (timelineVideoLayers.isNotEmpty()) {
                timelineVideoLayers.forEach { layer ->
                    androidx.compose.runtime.key(layer.clipId) {
                        AndroidView(
                            factory = { ctx -> TextureView(ctx).also { layer.player.setVideoTextureView(it) } },
                            update = { layer.player.setVideoTextureView(it) },
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                val sourceAspect = layer.videoAspectRatio
                                val fitScaleX = when (layer.contentMode) {
                                    CanvasContentMode.FIT -> if (sourceAspect > 0f && sourceAspect < aspectRatio) sourceAspect / aspectRatio else 1f
                                    CanvasContentMode.CROP -> if (sourceAspect > aspectRatio && aspectRatio > 0f) sourceAspect / aspectRatio else 1f
                                    CanvasContentMode.FILL -> 1f
                                }
                                val fitScaleY = when (layer.contentMode) {
                                    CanvasContentMode.FIT -> if (sourceAspect > aspectRatio && sourceAspect > 0f) aspectRatio / sourceAspect else 1f
                                    CanvasContentMode.CROP -> if (sourceAspect in 0.0001f..aspectRatio && aspectRatio > 0f) aspectRatio / sourceAspect else 1f
                                    CanvasContentMode.FILL -> 1f
                                }
                                scaleX = layer.transform.scaleX * fitScaleX
                                scaleY = layer.transform.scaleY * fitScaleY
                                rotationZ = layer.transform.rotationDegrees
                                alpha = layer.transform.opacity.coerceIn(0f, 1f)
                                translationX = layer.transform.x
                                translationY = layer.transform.y
                            }
                        )
                    }
                }
            } else {
                Text("TIMELINE PLAYBACK • NO VIDEO AT THIS POSITION", color = Color(0xFFB9C2D5), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.Center))
            }
        } else when (item?.kind) {
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
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val guideColor = Color.White.copy(alpha = 0.22f)
            val outerX = size.width * 0.05f
            val outerY = size.height * 0.05f
            val innerX = size.width * 0.10f
            val innerY = size.height * 0.10f
            drawRect(
                color = guideColor,
                topLeft = androidx.compose.ui.geometry.Offset(outerX, outerY),
                size = androidx.compose.ui.geometry.Size(size.width - outerX * 2f, size.height - outerY * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
            drawRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(innerX, innerY),
                size = androidx.compose.ui.geometry.Size(size.width - innerX * 2f, size.height - innerY * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
        Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
            Text("PREVIEW • SAFE GUIDES", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
        }
        if (item?.kind == "AUDIO") {
            FilledIconButton(onClick = onPlay, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AudioPage(
    volume: Float, onVolume: (Float) -> Unit,
    pitch: Float, onPitch: (Float) -> Unit,
    speed: Float, onSpeed: (Float) -> Unit,
    settings: AudioProcessingSettings,
    onSettingsChange: (AudioProcessingSettings) -> Unit
) {
    Text("Audio Lab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    Text("Live preview controls for the selected audio/video source", color = Color(0xFF9AA4B8), fontSize = 12.sp)
    Spacer(Modifier.height(18.dp))
    ControlSlider("Volume / Gain", volume, "0–100%", onVolume)
    ControlSlider("Pitch", pitch, "0.5×–2×", onPitch, 0.5f..2f)
    ControlSlider("Tempo", speed, "0.5×–2×", onSpeed, 0.5f..2f)
    AudioProcessingPanel(settings, onSettingsChange)
    Text("EQ, bass and compression use Android's per-audio-session DynamicsProcessing effect in live preview where supported. Settings are saved with the project; offline export processing is not connected yet.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
}

@Composable
private fun AudioProcessingPanel(settings: AudioProcessingSettings, onChange: (AudioProcessingSettings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    LaunchedEffect(settings) { draft = settings }
    val labels = listOf("80 Hz", "250 Hz", "1 kHz", "4 kHz", "12 kHz")
    Card(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("LIVE AUDIO PROCESSING", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Five-band EQ • bass boost • dynamics", color = Color(0xFF9AA4B8), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            labels.forEachIndexed { index, label ->
                val gain = draft.eqGainsDb[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("%+.1f dB".format(gain), color = Mint, fontSize = 10.sp)
                }
                Slider(value = gain,
                    onValueChange = { next -> draft = draft.copy(eqGainsDb = draft.eqGainsDb.toMutableList().also { it[index] = next }) },
                    onValueChangeFinished = { onChange(draft) }, valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
            }
            Divider(color = Color(0xFF30384A))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bass boost", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("+%.1f dB".format(draft.bassBoostDb), color = Mint, fontSize = 10.sp)
            }
            Slider(value = draft.bassBoostDb, onValueChange = { draft = draft.copy(bassBoostDb = it) },
                onValueChangeFinished = { onChange(draft) }, valueRange = 0f..12f,
                colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
            Divider(color = Color(0xFF30384A))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compressor", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("${(draft.compressorAmount * 100).toInt()}%", color = Mint, fontSize = 10.sp)
            }
            Slider(value = draft.compressorAmount, onValueChange = { draft = draft.copy(compressorAmount = it) },
                onValueChangeFinished = { onChange(draft) }, valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
            Text("Changes are saved when you release a slider.", color = Color(0xFF7F8BA2), fontSize = 9.sp)
        }
    }
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
private fun ExportPage(
    project: EditorProject,
    onSettingsChange: (EditorProject) -> Unit,
    exporting: Boolean,
    progress: Float,
    message: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onExportAudio: () -> Unit,
    onExportVideo: () -> Unit
) {
    val hasAudioClips = project.tracks.any { track ->
        (track.kind == com.creatorstudio.app.editor.TrackKind.AUDIO || track.kind == com.creatorstudio.app.editor.TrackKind.VIDEO) &&
            track.clips.any { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO }
    }
    val hasVisualizerAudioClips = project.tracks.any { track ->
        (track.kind == com.creatorstudio.app.editor.TrackKind.AUDIO || track.kind == com.creatorstudio.app.editor.TrackKind.VIDEO) &&
            track.clips.any { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO }
    }
    val hasSourceVideo = project.tracks.any { track -> track.clips.any { it.kind == MediaKind.VIDEO } }
    val hasStillImageLayers = project.visualizer.backgroundAssetId != null ||
        project.visualizer.foregroundAssetId != null ||
        project.tracks.any { track -> track.clips.any { it.kind == MediaKind.IMAGE } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("EXPORT STUDIO", color = Mint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            "Choose an audio mix or a rendered audio-reactive visualizer video.",
            color = Color(0xFFD4D9E5),
            fontSize = 12.sp
        )
        Text("AUDIO MASTER", color = Violet, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(
            "AAC-LC M4A with timeline clip volume/fades, track gain/mute/solo and offline master EQ, bass and compressor.",
            color = Color(0xFF9AA4B8),
            fontSize = 11.sp
        )
        Button(
            onClick = onExportAudio,
            enabled = hasAudioClips && !exporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EXPORT AUDIO MIX (.M4A)")
        }
        Divider(color = Color(0xFF30384A))
        Text("VISUALIZER VIDEO", color = Violet, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("ASPECT / CANVAS", color = Color(0xFFD4D9E5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(Triple("9:16", 1080, 1920), Triple("1:1", 1080, 1080), Triple("4:5", 1080, 1350), Triple("16:9", 1920, 1080)).forEach { preset ->
                val selected = project.width == preset.second && project.height == preset.third
                OutlinedButton(onClick = { onSettingsChange(project.copy(width = preset.second, height = preset.third)) }, enabled = !exporting, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 1.dp, vertical = 3.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Mint else Color.White)) {
                    Text(preset.first, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Text("FRAME RATE", color = Color(0xFFD4D9E5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                val selected = project.frameRate == fps
                OutlinedButton(onClick = { onSettingsChange(project.copy(frameRate = fps)) }, enabled = !exporting, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Mint else Color.White)) { Text("$fps FPS") }
            }
        }
        Text("VIDEO BITRATE", color = Color(0xFFD4D9E5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(4_000_000 to "4 Mbps", 8_000_000 to "8 Mbps", 12_000_000 to "12 Mbps", 20_000_000 to "20 Mbps").forEach { (rate, label) ->
                val selected = project.videoBitRate == rate
                OutlinedButton(onClick = { onSettingsChange(project.copy(videoBitRate = rate)) }, enabled = !exporting, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) Mint else Color.White)) { Text(label, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Text(
            "${project.width} × ${project.height}  •  ${project.frameRate} FPS  •  ${project.videoBitRate / 1_000_000} Mbps  •  H.264 + AAC",
            color = Color(0xFFD4D9E5),
            fontSize = 12.sp
        )
        Text(
            "The offline renderer analyzes the timeline audio, animates the selected visualizer, draws still-image/PNG layers and captions, then saves an MP4.",
            color = Color(0xFF9AA4B8),
            fontSize = 11.sp
        )
        Button(
            onClick = onExportVideo,
            enabled = hasVisualizerAudioClips && !exporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EXPORT VISUALIZER VIDEO (.MP4)")
        }
        if (!hasVisualizerAudioClips) {
            Text("Add an audio or video clip with an audio stream to drive the offline visualizer export.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
        }
        if (hasSourceVideo) {
            Text(
                "Source video frames are included in the picture composition. Embedded source-video audio is included when a decodable audio stream exists. Add an audio track if the video has no sound.",
                color = Color(0xFFFFC36B),
                fontSize = 10.sp
            )
        }
        if (!hasStillImageLayers) {
            Text("No artwork layer selected: the visualizer will render over a black background.", color = Color(0xFF9AA4B8), fontSize = 10.sp)
        }
        if (exporting) {
            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${(progress * 100).toInt()}%", color = Mint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) { Text("CANCEL EXPORT") }
            }
        }
        Text(
            message,
            color = if (message.contains("failed", ignoreCase = true)) Pink else Color(0xFF9AA4B8),
            fontSize = 11.sp
        )
        if (!exporting && message.contains("failed", ignoreCase = true)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("RETRY EXPORT — CHOOSE SAVE LOCATION")
            }
        }
        Text(
            "H.264/AAC encoder availability and render performance depend on the Android device. Source-video frames and decodable embedded audio are included; device codec compatibility, A/V sync and full GPU/3D rendering still need validation.",
            color = Color(0xFF9AA4B8),
            fontSize = 10.sp
        )
    }
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
