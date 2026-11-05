package studio.viso.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.viso.app.editor.EditorProject
import studio.viso.app.editor.TimelineClip
import studio.viso.app.editor.TimelineEditor
import studio.viso.app.editor.TrackKind
import studio.viso.app.editor.updateClipAudio

private val TimelinePanelBg = Color(0xFF101827)
private val TimelineAccent = Color(0xFF34D8C5)

@Composable
fun TimelinePanel(
    project: EditorProject,
    selectedClipId: String?,
    playheadMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectClip: (String) -> Unit,
    onPlayheadChange: (Long) -> Unit,
    onProjectChange: (EditorProject) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val duration = project.durationMs.coerceAtLeast(1_000L)
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
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.padding(start = 82.dp, bottom = 5.dp)) {
                    val seconds = (0..(duration / 1000L).toInt().coerceAtMost(180)).toList()
                    seconds.forEach { second ->
                        Text(if (second % 5 == 0) "${second}s" else "·", color = Color(0xFF8996AC), fontSize = 8.sp,
                            modifier = Modifier.width(32.dp))
                    }
                }
                project.tracks.forEach { track ->
                    Row(Modifier.height(48.dp).padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(track.name.uppercase(), color = Color(0xFFADB8CC), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(78.dp))
                        Box(Modifier.height(40.dp).width(((duration / 1000f).coerceAtMost(180f) * 32f).dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A2536))) {
                            track.clips.forEach { clip ->
                                val left = (clip.timelineStartMs / 1000f * 32f).dp
                                val width = (clip.durationMs / 1000f * 32f).coerceAtLeast(2.5f).dp
                                val tint = when (track.kind) {
                                    TrackKind.VIDEO -> Color(0xFF9278FF)
                                    TrackKind.AUDIO -> Color(0xFF27CBB7)
                                    TrackKind.OVERLAY -> Color(0xFFFF75AD)
                                    TrackKind.CAPTION -> Color(0xFFFFC45C)
                                    TrackKind.EFFECT -> Color(0xFF54B7FF)
                                }
                                Box(
                                    Modifier.offset(x = left).width(width).fillMaxHeight().padding(horizontal = 1.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (clip.id == selectedClipId) tint else tint.copy(alpha = 0.58f))
                                        .clickable { onSelectClip(clip.id) }.padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
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
                    onClick = { onProjectChange(TimelineEditor.deleteClip(project, selected.id)) },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7790))
                ) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)); Text("Delete", fontSize = 9.sp) }
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
    onSettingsChange: (studio.viso.app.editor.ClipAudioSettings) -> Unit
) {
    var volumeValue by remember(clip.id, clip.audio.volume) { mutableFloatStateOf(clip.audio.volume) }
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
            value = volumeValue.coerceIn(0f, 2f),
            onValueChange = { volumeValue = it },
            onValueChangeFinished = { onSettingsChange(clip.audio.copy(volume = volumeValue)) },
            valueRange = 0f..2f,
            colors = SliderDefaults.colors(thumbColor = TimelineAccent, activeTrackColor = TimelineAccent)
        )
        Text("Saved per clip • mute and gain are stored with this project", color = Color(0xFF8996AC), fontSize = 8.sp)
    }
}
