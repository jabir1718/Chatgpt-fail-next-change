package com.creatorstudio.app.editor

import java.util.UUID

enum class MediaKind { VIDEO, AUDIO, IMAGE, TEXT }
enum class TrackKind { VIDEO, AUDIO, OVERLAY, CAPTION, EFFECT }

data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
)

data class ClipAudioSettings(
    val volume: Float = 1f,
    val muted: Boolean = false,
    val pitchSemitones: Float = 0f,
    val playbackRate: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class CaptionSegment(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val languageTag: String? = null
) {
    init {
        require(startMs >= 0L) { "Caption start must not be negative" }
        require(endMs > startMs) { "Caption end must be after start" }
    }
}

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val mediaId: String,
    val kind: MediaKind,
    val timelineStartMs: Long,
    val durationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceStartMs + durationMs,
    val transform: Transform = Transform(),
    val audio: ClipAudioSettings = ClipAudioSettings(),
    val captions: List<CaptionSegment> = emptyList()
) {
    init {
        require(timelineStartMs >= 0L) { "Timeline start must not be negative" }
        require(durationMs > 0L) { "Clip duration must be positive" }
        require(sourceStartMs >= 0L) { "Source start must not be negative" }
        require(sourceEndMs > sourceStartMs) { "Source end must be after source start" }
        require(sourceEndMs - sourceStartMs == durationMs) {
            "Source range and timeline duration must match"
        }
        require(audio.volume in 0f..1f) { "Preview volume must be between 0 and 1" }
        require(audio.playbackRate in 0.25f..4f) { "Playback rate must be between 0.25 and 4" }
    }

    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: TrackKind,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val clips: List<TimelineClip> = emptyList()
)

data class MediaAsset(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val kind: MediaKind,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val importedAtEpochMs: Long = System.currentTimeMillis()
)

data class VisualizerSettings(
    val backgroundAssetId: String? = null,
    val foregroundAssetId: String? = null,
    val mode: String = "Spectrum",
    val foregroundScale: Float = 1f,
    val beatSensitivity: Float = 1.08f
) {
    init {
        require(mode in setOf("Spectrum", "Radial", "Waveform")) { "Unsupported visualizer mode" }
        require(foregroundScale in 0.5f..2f) { "Foreground scale must be between 0.5 and 2" }
        require(beatSensitivity in 1f..1.5f) { "Beat sensitivity must be between 1 and 1.5" }
    }
}

data class EditorProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val width: Int = 1080,
    val height: Int = 1920,
    val frameRate: Int = 30,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val assets: List<MediaAsset> = emptyList(),
    val tracks: List<TimelineTrack> = emptyList(),
    val visualizer: VisualizerSettings = VisualizerSettings()
) {
    init {
        require(width > 0 && height > 0) { "Canvas dimensions must be positive" }
        require(frameRate in 1..120) { "Frame rate must be between 1 and 120" }
        require(assets.map { it.id }.distinct().size == assets.size) { "Media IDs must be unique" }
        require(tracks.map { it.id }.distinct().size == tracks.size) { "Track IDs must be unique" }
        val allClips = tracks.flatMap { it.clips }
        require(allClips.map { it.id }.distinct().size == allClips.size) { "Clip IDs must be unique" }
        require(allClips.all { clip -> assets.any { it.id == clip.mediaId } }) {
            "Every timeline clip must reference an imported media asset"
        }
        require(visualizer.backgroundAssetId == null || assets.any { it.id == visualizer.backgroundAssetId && it.kind == MediaKind.IMAGE }) {
            "Visualizer background must reference an imported image"
        }
        require(visualizer.foregroundAssetId == null || assets.any { it.id == visualizer.foregroundAssetId && it.kind == MediaKind.IMAGE }) {
            "Visualizer foreground must reference an imported image"
        }
    }

    val durationMs: Long get() = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 0L
}

/** Immutable editing operations. Each method returns a new project so callers can add undo/redo safely. */
object TimelineEditor {
    fun addTrack(project: EditorProject, name: String, kind: TrackKind): EditorProject {
        require(name.isNotBlank()) { "Track name cannot be blank" }
        return project.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks + TimelineTrack(name = name, kind = kind)
        )
    }

    fun addAsset(project: EditorProject, asset: MediaAsset): EditorProject {
        require(project.assets.none { it.id == asset.id }) { "Media asset already exists" }
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), assets = project.assets + asset)
    }

    fun addClip(project: EditorProject, trackId: String, clip: TimelineClip): EditorProject {
        require(project.assets.any { it.id == clip.mediaId }) { "Clip media asset was not imported" }
        require(project.tracks.none { track -> track.clips.any { it.id == clip.id } }) { "Clip already exists" }
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        require(!track.locked) { "Track is locked" }
        val updated = track.copy(clips = (track.clips + clip).sortedBy { it.timelineStartMs })
        return project.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == trackId) updated else it }
        )
    }

    fun moveClip(project: EditorProject, clipId: String, newStartMs: Long): EditorProject {
        require(newStartMs >= 0L) { "Clip cannot start before zero" }
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val updated = track.copy(
            clips = track.clips.map {
                if (it.id == clipId) it.copy(timelineStartMs = newStartMs) else it
            }.sortedBy { it.timelineStartMs }
        )
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { if (it.id == track.id) updated else it })
    }

    fun trimStart(project: EditorProject, clipId: String, newStartMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val updated = track.copy(clips = track.clips.map { clip ->
            if (clip.id != clipId) clip else {
                val delta = newStartMs - clip.timelineStartMs
                require(delta >= 0L && delta < clip.durationMs) { "Trim start must remain inside clip" }
                clip.copy(
                    timelineStartMs = newStartMs,
                    durationMs = clip.durationMs - delta,
                    sourceStartMs = clip.sourceStartMs + delta
                )
            }
        })
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { if (it.id == track.id) updated else it })
    }

    fun trimEnd(project: EditorProject, clipId: String, newEndMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val updated = track.copy(clips = track.clips.map { clip ->
            if (clip.id != clipId) clip else {
                val duration = newEndMs - clip.timelineStartMs
                require(duration > 0L && duration <= clip.durationMs) { "Trim end must remain inside clip" }
                clip.copy(durationMs = duration, sourceEndMs = clip.sourceStartMs + duration)
            }
        })
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { if (it.id == track.id) updated else it })
    }

    fun split(project: EditorProject, clipId: String, atTimelineMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val original = track.clips.first { it.id == clipId }
        require(atTimelineMs > original.timelineStartMs && atTimelineMs < original.timelineEndMs) {
            "Split point must be inside the clip"
        }
        val leftDuration = atTimelineMs - original.timelineStartMs
        val rightDuration = original.durationMs - leftDuration
        val left = original.copy(durationMs = leftDuration, sourceEndMs = original.sourceStartMs + leftDuration)
        val right = original.copy(
            id = UUID.randomUUID().toString(),
            timelineStartMs = atTimelineMs,
            durationMs = rightDuration,
            sourceStartMs = original.sourceStartMs + leftDuration
        )
        val clips = track.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) }
            .sortedBy { it.timelineStartMs }
        val updated = track.copy(clips = clips)
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { if (it.id == track.id) updated else it })
    }

    fun deleteClip(project: EditorProject, clipId: String): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val updated = track.copy(clips = track.clips.filterNot { it.id == clipId })
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { if (it.id == track.id) updated else it })
    }

    fun deleteTrack(project: EditorProject, trackId: String): EditorProject {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        require(!track.locked) { "Track is locked" }
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.filterNot { it.id == trackId })
    }
}
