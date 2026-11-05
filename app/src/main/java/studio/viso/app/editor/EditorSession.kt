package com.creatorstudio.app.editor

import android.net.Uri
import java.util.UUID

fun createStarterProject(): EditorProject = EditorProject(
    name = "My Creator Project",
    tracks = listOf(
        TimelineTrack(name = "Video", kind = TrackKind.VIDEO),
        TimelineTrack(name = "Audio", kind = TrackKind.AUDIO),
        TimelineTrack(name = "Overlays", kind = TrackKind.OVERLAY),
        TimelineTrack(name = "Captions", kind = TrackKind.CAPTION),
        TimelineTrack(name = "Effects", kind = TrackKind.EFFECT)
    )
)

data class ImportedClipResult(val project: EditorProject, val assetId: String, val clipId: String)

fun EditorProject.importAsset(
    uri: Uri,
    displayName: String,
    kind: MediaKind,
    durationMs: Long,
    mimeType: String?
): ImportedClipResult {
    require(durationMs > 0L)
    val asset = MediaAsset(
        uri = uri.toString(),
        displayName = displayName.ifBlank { "Imported media" },
        kind = kind,
        durationMs = durationMs,
        mimeType = mimeType
    )
    var next = TimelineEditor.addAsset(this, asset)
    val trackKind = when (kind) {
        MediaKind.VIDEO -> TrackKind.VIDEO
        MediaKind.AUDIO -> TrackKind.AUDIO
        MediaKind.IMAGE -> TrackKind.OVERLAY
        MediaKind.TEXT -> TrackKind.CAPTION
    }
    var track = next.tracks.firstOrNull { it.kind == trackKind }
    if (track == null) {
        next = TimelineEditor.addTrack(next, trackKind.name.lowercase().replaceFirstChar { it.uppercase() }, trackKind)
        track = next.tracks.last()
    }
    val startMs = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
    val clip = TimelineClip(
        mediaId = asset.id,
        kind = kind,
        timelineStartMs = startMs,
        durationMs = durationMs
    )
    next = TimelineEditor.addClip(next, track.id, clip)
    return ImportedClipResult(next, asset.id, clip.id)
}

fun EditorProject.updateClipAudio(clipId: String, transform: (ClipAudioSettings) -> ClipAudioSettings): EditorProject {
    val track = tracks.firstOrNull { row -> row.clips.any { it.id == clipId } } ?: return this
    val clips = track.clips.map { clip ->
        if (clip.id == clipId) clip.copy(audio = transform(clip.audio)) else clip
    }
    return copy(
        updatedAtEpochMs = System.currentTimeMillis(),
        tracks = tracks.map { if (it.id == track.id) track.copy(clips = clips) else it }
    )
}

fun EditorProject.updateProjectName(name: String): EditorProject =
    copy(name = name.trim().ifBlank { "Untitled Project" }, updatedAtEpochMs = System.currentTimeMillis())

fun EditorProject.duplicateProject(copyName: String = "${name} Copy"): EditorProject {
    val newAssetIds = assets.associate { it.id to UUID.randomUUID().toString() }
    val copiedAssets = assets.map { asset -> asset.copy(id = newAssetIds.getValue(asset.id)) }
    val copiedTracks = tracks.map { track ->
        track.copy(
            id = UUID.randomUUID().toString(),
            clips = track.clips.map { clip ->
                clip.copy(
                    id = UUID.randomUUID().toString(),
                    mediaId = newAssetIds.getValue(clip.mediaId)
                )
            }
        )
    }
    val now = System.currentTimeMillis()
    return copy(
        id = UUID.randomUUID().toString(),
        name = copyName.trim().ifBlank { "${name} Copy" },
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
        assets = copiedAssets,
        tracks = copiedTracks
    )
}
