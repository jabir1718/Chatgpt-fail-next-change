package com.creatorstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToLong
import com.creatorstudio.app.editor.EditorProject
import com.creatorstudio.app.editor.TimelineClip
import com.creatorstudio.app.editor.TimelineEditor
import com.creatorstudio.app.editor.TrackKind
import com.creatorstudio.app.editor.updateClipAudio
import com.creatorstudio.app.editor.pitchRatio

private val TimelinePanelBg = Color(0xFF101827)
private val TimelineAccent = Color(0xFF34D8C5)

@Composable
fun TimelinePanel(
    project: EditorProject,
    selectedClipId: String?,
    playheadMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    waveforms: Map<String, List<Float>> = emptyMap(),
    onSelectClip: (String) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onProjectChange: (EditorProject) -> Unit,
    onAddTrack: (String, TrackKind) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val duration = project.durationMs.coerceAtLeast(1_000L)
    val density = LocalDensity.current
    var pixelsPerSecond by remember(project.id) { mutableFloatStateOf(24f) }
    var snappingEnabled by remember(project.id) { mutableStateOf(true) }
    var showMoveMenu by remember(selectedClipId) { mutableStateOf(false) }
    val totalSeconds = (duration / 1_000L).toInt().coerceAtLeast(1)
    val tickStep = when {
        totalSeconds > 1_800 -> 60
        totalSeconds > 600 -> 30
        totalSeconds > 180 -> 10
        totalSeconds > 60 -> 5
        else -> 1
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(TimelinePanelBg).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MULTI-TRACK TIMELINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${project.tracks.size} tracks • ${project.assets.size} media assets", color = Color(0xFF9AA4B8), fontSize = 9.sp)
            }
            IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.Default.Undo, "Undo", tint = if (canUndo) TimelineAccent else Color.Gray) }
            IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.Default.Redo, "Redo", tint = if (canRedo) TimelineAccent else Color.Gray) }
            IconButton(onClick = { pixelsPerSecond = (pixelsPerSecond - 4f).coerceAtLeast(12f) }) { Text("−", color = Color.White) }
            Text("${pixelsPerSecond.toInt()} px/s", color = Color(0xFF9AA4B8), fontSize = 8.sp)
            IconButton(onClick = { pixelsPerSecond = (pixelsPerSecond + 4f).coerceAtMost(48f) }) { Text("+", color = Color.White) }
            TextButton(onClick = { snappingEnabled = !snappingEnabled }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text(if (snappingEnabled) "SNAP ON" else "SNAP OFF", color = if (snappingEnabled) TimelineAccent else Color(0xFF8996AC), fontSize = 8.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("ADD TRACK", color = Color(0xFF9AA4B8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "VIDEO" to TrackKind.VIDEO,
                "AUDIO" to TrackKind.AUDIO,
                "OVERLAY" to TrackKind.OVERLAY,
                "CAPTION" to TrackKind.CAPTION,
                "EFFECT" to TrackKind.EFFECT
            ).forEach { (label, kind) ->
                TextButton(
                    onClick = { onAddTrack(label.replaceFirstChar { it.uppercase() }, kind) },
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                    Text(label, fontSize = 8.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.padding(start = 86.dp, bottom = 5.dp)) {
                    (0..totalSeconds step tickStep).forEach { second ->
                        Text("${second}s", color = Color(0xFF8996AC), fontSize = 8.sp,
                            modifier = Modifier.width((tickStep * pixelsPerSecond).dp))
                    }
                }
                project.tracks.forEach { track ->
                    Row(Modifier.height(62.dp).padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(82.dp), verticalArrangement = Arrangement.Center) {
                            Text(track.name.uppercase(), color = if (track.locked) Color(0xFFFFC45C) else Color(0xFFADB8CC), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onProjectChange(TimelineEditor.setTrackLocked(project, track.id, !track.locked)) }, modifier = Modifier.size(20.dp)) {
                                    Icon(if (track.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (track.locked) "Unlock track" else "Lock track", tint = if (track.locked) Color(0xFFFFC45C) else Color(0xFF8996AC), modifier = Modifier.size(12.dp))
                                }
                                IconButton(onClick = { onProjectChange(TimelineEditor.setTrackMuted(project, track.id, !track.muted)) }, modifier = Modifier.size(20.dp)) {
                                    Icon(if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = if (track.muted) "Unmute track" else "Mute track", tint = if (track.muted) Color(0xFFFFC45C) else Color(0xFF8996AC), modifier = Modifier.size(12.dp))
                                }
                                if (track.kind == TrackKind.AUDIO || track.kind == TrackKind.VIDEO) {
                                    TextButton(onClick = { onProjectChange(TimelineEditor.setTrackSolo(project, track.id, !track.solo)) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(20.dp)) {
                                        Text(if (track.solo) "S" else "s", color = if (track.solo) Color(0xFFFFC45C) else Color(0xFF8996AC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (track.kind == TrackKind.AUDIO || track.kind == TrackKind.VIDEO) {
                                Text("${(track.gain * 100).toInt()}%", color = TimelineAccent, fontSize = 7.sp)
                                Slider(
                                    value = track.gain,
                                    onValueChange = { gain -> onProjectChange(TimelineEditor.setTrackGain(project, track.id, gain)) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.width(78.dp).height(18.dp),
                                    colors = SliderDefaults.colors(thumbColor = TimelineAccent, activeTrackColor = TimelineAccent)
                                )
                            }
                        }
                        Box(Modifier.height(40.dp).width((totalSeconds * pixelsPerSecond).dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2536))) {
                            track.clips.forEach { clip ->
                                var dragOffsetPx by remember(clip.id) { mutableFloatStateOf(0f) }
                                val left = (clip.timelineStartMs / 1000f * pixelsPerSecond).dp
                                val dragOffsetDp = with(density) { dragOffsetPx.toDp() }
                                val width = (clip.durationMs / 1000f * pixelsPerSecond).coerceAtLeast(8f).dp
                                val tint = when (track.kind) {
                                    TrackKind.VIDEO -> Color(0xFF9278FF)
                                    TrackKind.AUDIO -> Color(0xFF27CBB7)
                                    TrackKind.OVERLAY -> Color(0xFFFF75AD)
                                    TrackKind.CAPTION -> Color(0xFFFFC45C)
                                    TrackKind.EFFECT -> Color(0xFF54B7FF)
                                }
                                Box(
                                    Modifier.offset(x = left + dragOffsetDp).width(width).fillMaxHeight().padding(horizontal = 1.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (clip.id == selectedClipId) tint else tint.copy(alpha = 0.58f))
                                        .pointerInput(clip.id, pixelsPerSecond, track.locked, snappingEnabled, playheadMs) {
                                            if (!track.locked) {
                                                detectDragGestures(
                                                    onDragStart = { onSelectClip(clip.id) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetPx += dragAmount.x
                                                    },
                                                    onDragEnd = {
                                                        val deltaMs = (dragOffsetPx / density.density / pixelsPerSecond * 1_000f).roundToLong()
                                                        dragOffsetPx = 0f
                                                        if (deltaMs != 0L) {
                                                            val proposedStart = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
                                                            val finalStart = if (snappingEnabled) {
                                                                TimelineEditor.snapClipStart(project, clip.id, proposedStart, playheadMs)
                                                            } else proposedStart
                                                            onProjectChange(TimelineEditor.moveClip(project, clip.id, finalStart))
                                                        }
                                                    },
                                                    onDragCancel = { dragOffsetPx = 0f }
                                                )
                                            }
                                        }
                                        .clickable { onSelectClip(clip.id) }.padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val waveform = waveforms[clip.mediaId]
                                    if (waveform != null && (clip.kind == com.creatorstudio.app.editor.MediaKind.AUDIO || clip.kind == com.creatorstudio.app.editor.MediaKind.VIDEO)) {
                                        Canvas(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)) {
                                            val step = (size.width / waveform.size.coerceAtLeast(1)).coerceAtLeast(1f)
                                            waveform.forEachIndexed { index, amplitude ->
                                                val x = index * step + step * 0.5f
                                                val halfHeight = (amplitude * size.height * 0.46f).coerceAtLeast(1f)
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.42f),
                                                    start = Offset(x, size.height * 0.5f - halfHeight),
                                                    end = Offset(x, size.height * 0.5f + halfHeight),
                                                    strokeWidth = step.coerceAtMost(2.2f)
                                                )
                                            }
                                        }
                                    } else if (clip.kind == com.creatorstudio.app.editor.MediaKind.AUDIO || clip.kind == com.creatorstudio.app.editor.MediaKind.VIDEO) {
                                        Text("DECODING WAVEFORM", color = Color.White.copy(alpha = 0.42f), fontSize = 6.sp)
                                    }
                                    Text(project.assets.firstOrNull { it.id == clip.mediaId }?.displayName ?: "Clip",
                                        color = Color.White, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("PLAYHEAD  ${formatTimelineTime(playheadMs)} / ${formatTimelineTime(duration)}",
            color = TimelineAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = playheadMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { onPlayheadChange(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(thumbColor = TimelineAccent, activeTrackColor = TimelineAccent)
        )
        val selected = project.tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == selectedClipId }
        if (selected != null) {
            Text("SELECTED CLIP  •  ${project.assets.firstOrNull { it.id == selected.mediaId }?.displayName ?: selected.id}",
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            val sourceTrack = project.tracks.firstOrNull { row -> row.clips.any { it.id == selected.id } }
            val sourceTrackId = sourceTrack?.id
            val selectedTrackLocked = sourceTrack?.locked == true
            val destinationKind = when (selected.kind) {
                com.creatorstudio.app.editor.MediaKind.VIDEO -> TrackKind.VIDEO
                com.creatorstudio.app.editor.MediaKind.AUDIO -> TrackKind.AUDIO
                com.creatorstudio.app.editor.MediaKind.IMAGE -> TrackKind.OVERLAY
                com.creatorstudio.app.editor.MediaKind.TEXT -> TrackKind.CAPTION
            }
            val compatibleTracks = project.tracks.filter { it.id != sourceTrackId && it.kind == destinationKind && !it.locked }
            Box {
                TextButton(
                    onClick = { showMoveMenu = true },
                    enabled = compatibleTracks.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("MOVE TO TRACK", fontSize = 8.sp) }
                DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                    compatibleTracks.forEach { destination ->
                        DropdownMenuItem(
                            text = { Text(destination.name) },
                            onClick = {
                                onProjectChange(TimelineEditor.moveClipToTrack(project, selected.id, destination.id))
                                showMoveMenu = false
                            }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val start = (selected.timelineStartMs - 1_000L).coerceAtLeast(0L)
                        onProjectChange(TimelineEditor.moveClip(project, selected.id, start))
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)
                ) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp)); Text("−1s", fontSize = 9.sp) }
                OutlinedButton(
                    onClick = {
                        onProjectChange(TimelineEditor.moveClip(project, selected.id, selected.timelineStartMs + 1_000L))
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)
                ) { Text("+1s", fontSize = 9.sp); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp)) }
                OutlinedButton(
                    onClick = {
                        if (playheadMs > selected.timelineStartMs && playheadMs < selected.timelineEndMs) {
                            onProjectChange(TimelineEditor.split(project, selected.id, playheadMs))
                        }
                    }, enabled = playheadMs > selected.timelineStartMs && playheadMs < selected.timelineEndMs,
                    modifier = Modifier.weight(1.2f), contentPadding = PaddingValues(horizontal = 3.dp)
                ) { Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(14.dp)); Text("Split", fontSize = 9.sp) }
                OutlinedButton(
                    onClick = {
                        onProjectChange(TimelineEditor.duplicateClip(project, selected.id))
                    },
                    enabled = !selectedTrackLocked,
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)
                ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp)); Text("Copy", fontSize = 9.sp) }
                OutlinedButton(
                    onClick = { onProjectChange(TimelineEditor.deleteClip(project, selected.id)) },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7790))
                ) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)); Text("Delete", fontSize = 9.sp) }
                OutlinedButton(
                    onClick = { onProjectChange(TimelineEditor.rippleDeleteClip(project, selected.id)) },
                    modifier = Modifier.weight(1.1f), contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFC45C))
                ) { Text("Ripple", fontSize = 9.sp) }
            }
            ClipTrimControls(selected, onTrimStart = { start ->
                onProjectChange(TimelineEditor.trimStart(project, selected.id, start))
            }, onTrimEnd = { end ->
                onProjectChange(TimelineEditor.trimEnd(project, selected.id, end))
            })
            ClipAudioControls(selected, onSettingsChange = { settings ->
                onProjectChange(project.updateClipAudio(selected.id) { settings })
            })
        } else {
            Text("Tap a clip block to select it. Import media to populate the correct track.",
                color = Color(0xFF9AA4B8), fontSize = 9.sp)
        }
    }
}

@Composable
private fun ClipTrimControls(clip: TimelineClip, onTrimStart: (Long) -> Unit, onTrimEnd: (Long) -> Unit) {
    var startValue by remember(clip.id, clip.timelineStartMs) { mutableFloatStateOf(clip.timelineStartMs.toFloat()) }
    var endValue by remember(clip.id, clip.timelineEndMs) { mutableFloatStateOf(clip.timelineEndMs.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("TRIM START  •  ${formatTimelineTime(startValue.toLong())}", color = Color(0xFFB7C4D8), fontSize = 9.sp)
        Slider(
            value = startValue,
            onValueChange = { startValue = it },
            onValueChangeFinished = { onTrimStart(startValue.toLong()) },
            valueRange = clip.timelineStartMs.toFloat()..(clip.timelineEndMs - 250L).coerceAtLeast(clip.timelineStartMs).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC45C), activeTrackColor = Color(0xFFFFC45C))
        )
        Text("TRIM END  •  ${formatTimelineTime(endValue.toLong())}", color = Color(0xFFB7C4D8), fontSize = 9.sp)
        Slider(
            value = endValue,
            onValueChange = { endValue = it },
            onValueChangeFinished = { onTrimEnd(endValue.toLong()) },
            valueRange = (clip.timelineStartMs + 250L).toFloat()..clip.timelineEndMs.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC45C), activeTrackColor = Color(0xFFFFC45C))
        )
    }
}

private fun formatTimelineTime(valueMs: Long): String {
    val safe = valueMs.coerceAtLeast(0L)
    return "${safe / 60_000L}:${((safe / 1_000L) % 60L).toString().padStart(2, '0')}.${((safe % 1_000L) / 100L)}"
}

@Composable
private fun ClipAudioControls(
    clip: TimelineClip,
    onSettingsChange: (com.creatorstudio.app.editor.ClipAudioSettings) -> Unit
) {
    var volumeValue by remember(clip.id, clip.audio.volume) { mutableFloatStateOf(clip.audio.volume) }
    var pitchValue by remember(clip.id, clip.audio.pitchSemitones) { mutableFloatStateOf(clip.audio.pitchSemitones) }
    var rateValue by remember(clip.id, clip.audio.playbackRate) { mutableFloatStateOf(clip.audio.playbackRate) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CLIP VOLUME", color = Color(0xFFB7C4D8), fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("${(volumeValue * 100).toInt()}%", color = TimelineAccent, fontSize = 9.sp)
            OutlinedButton(
                onClick = { onSettingsChange(clip.audio.copy(muted = !clip.audio.muted)) },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)
            ) { Text(if (clip.audio.muted) "UNMUTE" else "MUTE", fontSize = 8.sp) }
        }
        Slider(
            value = volumeValue.coerceIn(0f, 1f),
            onValueChange = { volumeValue = it },
            onValueChangeFinished = { onSettingsChange(clip.audio.copy(volume = volumeValue)) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = TimelineAccent, activeTrackColor = TimelineAccent)
        )
        Text("PITCH  ${pitchValue.toInt()} semitones", color = Color(0xFFB7C4D8), fontSize = 9.sp)
        Slider(
            value = pitchValue.coerceIn(-12f, 12f),
            onValueChange = { pitchValue = it },
            onValueChangeFinished = { onSettingsChange(clip.audio.copy(pitchSemitones = pitchValue)) },
            valueRange = -12f..12f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF9278FF), activeTrackColor = Color(0xFF9278FF))
        )
        Text("TEMPO  ${"%.2f".format(rateValue)}×", color = Color(0xFFB7C4D8), fontSize = 9.sp)
        Slider(
            value = rateValue.coerceIn(0.25f, 4f),
            onValueChange = { rateValue = it },
            onValueChangeFinished = { onSettingsChange(clip.audio.copy(playbackRate = rateValue)) },
            valueRange = 0.25f..4f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF9278FF), activeTrackColor = Color(0xFF9278FF))
        )
        Text("Saved per clip • preview playback uses these settings; export DSP is not connected yet",
            color = Color(0xFF8996AC), fontSize = 8.sp)
    }
}
