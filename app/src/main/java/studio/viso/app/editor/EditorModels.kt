package com.creatorstudio.app.editor

import java.util.UUID

enum class MediaKind { VIDEO, AUDIO, IMAGE, TEXT }
enum class CanvasContentMode { FIT, FILL, CROP }
enum class TrackKind { VIDEO, AUDIO, OVERLAY, CAPTION, EFFECT }

data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
)

data class BeatResponseSettings(
    val enabled: Boolean = false,
    val scaleAmount: Float = 0.16f,
    val sensitivity: Float = 1f
) {
    init {
        require(scaleAmount in 0f..0.5f) { "Beat scale amount must be between 0 and 0.5" }
        require(sensitivity in 0.25f..2f) { "Beat sensitivity must be between 0.25 and 2" }
    }
}

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
    val contentMode: CanvasContentMode = CanvasContentMode.FIT,
    val beatResponse: BeatResponseSettings = BeatResponseSettings(),
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
    val clips: List<TimelineClip> = emptyList(),
    val gain: Float = 1f,
    val solo: Boolean = false
) {
    init { require(gain in 0f..1f) { "Track gain must be between 0 and 1" } }
}

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
        require(mode in setOf("Spectrum", "Radial", "Waveform", "Rings", "Particles")) { "Unsupported visualizer mode" }
        require(foregroundScale in 0.5f..2f) { "Foreground scale must be between 0.5 and 2" }
        require(beatSensitivity in 1f..1.5f) { "Beat sensitivity must be between 1 and 1.5" }
    }
}

/** Master audio processing controls applied to live preview audio sessions. */
data class AudioProcessingSettings(
    val eqGainsDb: List<Float> = List(5) { 0f },
    val bassBoostDb: Float = 0f,
    val compressorAmount: Float = 0f
) {
    init {
        require(eqGainsDb.size == 5) { "Five EQ bands are required" }
        require(eqGainsDb.all { it in -12f..12f }) { "EQ gain must be between -12 and +12 dB" }
        require(bassBoostDb in 0f..12f) { "Bass boost must be between 0 and 12 dB" }
        require(compressorAmount in 0f..1f) { "Compressor amount must be between 0 and 1" }
    }
}

data class EditorProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val width: Int = 1080,
    val height: Int = 1920,
    val frameRate: Int = 30,
    val videoBitRate: Int = 8_000_000,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val assets: List<MediaAsset> = emptyList(),
    val tracks: List<TimelineTrack> = emptyList(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val audioProcessing: AudioProcessingSettings = AudioProcessingSettings()
) {
    init {
        require(width > 0 && height > 0) { "Canvas dimensions must be positive" }
        require(frameRate in 1..60) { "Frame rate must be between 1 and 60" }
        require(videoBitRate in 250_000..80_000_000) { "Video bitrate must be between 250 kbps and 80 Mbps" }
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

    fun duplicateClip(project: EditorProject, clipId: String): EditorProject {
        val track = project.tracks.firstOrNull { row -> row.clips.any { it.id == clipId } }
            ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val original = track.clips.first { it.id == clipId }
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            timelineStartMs = original.timelineEndMs
        )
        val updatedTrack = track.copy(clips = (track.clips + duplicate).sortedBy { it.timelineStartMs })
        return project.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == track.id) updatedTrack else it }
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

    /** Aligns a dragged clip edge to nearby clip edges, the playhead, or timeline zero. */
    fun snapClipStart(
        project: EditorProject,
        clipId: String,
        proposedStartMs: Long,
        playheadMs: Long,
        thresholdMs: Long = 220L
    ): Long {
        require(proposedStartMs >= 0L) { "Clip cannot start before zero" }
        require(thresholdMs >= 0L) { "Snap threshold cannot be negative" }
        val clip = project.tracks.asSequence().flatMap { it.clips.asSequence() }
            .firstOrNull { it.id == clipId } ?: error("Clip not found")
        val candidates = mutableListOf(0L, playheadMs.coerceAtLeast(0L))
        if (playheadMs >= clip.durationMs) candidates.add(playheadMs - clip.durationMs)
        project.tracks.asSequence().flatMap { it.clips.asSequence() }
            .filterNot { it.id == clipId }
            .forEach { other ->
                candidates.add(other.timelineStartMs)
                candidates.add(other.timelineEndMs)
                if (other.timelineStartMs >= clip.durationMs) candidates.add(other.timelineStartMs - clip.durationMs)
                if (other.timelineEndMs >= clip.durationMs) candidates.add(other.timelineEndMs - clip.durationMs)
            }
        return candidates.asSequence()
            .filter { it >= 0L && kotlin.math.abs(it - proposedStartMs) <= thresholdMs }
            .minByOrNull { kotlin.math.abs(it - proposedStartMs) }
            ?: proposedStartMs
    }

    fun moveClipToTrack(
        project: EditorProject,
        clipId: String,
        targetTrackId: String,
        newStartMs: Long? = null
    ): EditorProject {
        val sourceTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
            ?: error("Clip not found")
        require(!sourceTrack.locked) { "Source track is locked" }
        val targetTrack = project.tracks.firstOrNull { it.id == targetTrackId }
            ?: error("Target track not found")
        require(!targetTrack.locked) { "Target track is locked" }
        val clip = sourceTrack.clips.first { it.id == clipId }
        val requiredTrackKind = when (clip.kind) {
            MediaKind.VIDEO -> TrackKind.VIDEO
            MediaKind.AUDIO -> TrackKind.AUDIO
            MediaKind.IMAGE -> TrackKind.OVERLAY
            MediaKind.TEXT -> TrackKind.CAPTION
        }
        require(targetTrack.kind == requiredTrackKind) {
            "This clip type cannot be moved to the selected track"
        }
        val moved = clip.copy(timelineStartMs = newStartMs ?: clip.timelineStartMs)
        require(moved.timelineStartMs >= 0L) { "Clip cannot start before zero" }
        if (sourceTrack.id == targetTrack.id) {
            return moveClip(project, clipId, moved.timelineStartMs)
        }
        val tracks = project.tracks.map { track ->
            when (track.id) {
                sourceTrack.id -> track.copy(clips = track.clips.filterNot { it.id == clipId })
                targetTrack.id -> track.copy(clips = (track.clips + moved).sortedBy { it.timelineStartMs })
                else -> track
            }
        }
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = tracks)
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

    /** Removes a clip and closes the resulting gap on its own track. Other tracks are unchanged. */
    fun rippleDeleteClip(project: EditorProject, clipId: String): EditorProject {
        val track = project.tracks.firstOrNull { candidate -> candidate.clips.any { it.id == clipId } }
            ?: error("Clip not found")
        require(!track.locked) { "Track is locked" }
        val removed = track.clips.first { it.id == clipId }
        val gapEnd = removed.timelineEndMs
        val gapDuration = removed.durationMs
        val updated = track.copy(
            clips = track.clips.filterNot { it.id == clipId }.map { clip ->
                if (clip.timelineStartMs >= gapEnd) clip.copy(timelineStartMs = clip.timelineStartMs - gapDuration)
                else clip
            }.sortedBy { it.timelineStartMs }
        )
        return project.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == track.id) updated else it }
        )
    }

    fun setTrackLocked(project: EditorProject, trackId: String, locked: Boolean): EditorProject {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == trackId) track.copy(locked = locked) else it })
    }

    fun setTrackMuted(project: EditorProject, trackId: String, muted: Boolean): EditorProject {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == trackId) track.copy(muted = muted) else it })
    }

    fun setClipBeatResponse(project: EditorProject, clipId: String, settings: BeatResponseSettings): EditorProject {
        require(project.tracks.any { track -> track.clips.any { it.id == clipId } }) { "Clip not found" }
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(), tracks = project.tracks.map { track ->
            track.copy(clips = track.clips.map { clip -> if (clip.id == clipId) clip.copy(beatResponse = settings) else clip })
        })
    }

    fun setClipContentMode(project: EditorProject, clipId: String, mode: CanvasContentMode): EditorProject {
        return project.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(contentMode = mode) else clip
                })
            }
        )
    }

    fun setTrackGain(project: EditorProject, trackId: String, gain: Float): EditorProject {
        require(gain in 0f..1f) { "Track gain must be between 0 and 1" }
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == trackId) track.copy(gain = gain) else it })
    }

    fun setTrackSolo(project: EditorProject, trackId: String, solo: Boolean): EditorProject {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: error("Track not found")
        return project.copy(updatedAtEpochMs = System.currentTimeMillis(),
            tracks = project.tracks.map { if (it.id == trackId) track.copy(solo = solo) else it })
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
