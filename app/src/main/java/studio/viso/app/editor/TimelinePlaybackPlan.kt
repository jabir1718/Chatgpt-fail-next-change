package com.creatorstudio.app.editor

/**
 * Immutable snapshot of which clips should contribute to a timeline preview at a playhead position.
 * This is a scheduling model only; it does not start media decoders or mix audio itself.
 */
data class ScheduledClip(
    val clip: TimelineClip,
    val trackId: String,
    val trackKind: TrackKind,
    val trackMuted: Boolean,
    val sourcePositionMs: Long,
    val effectiveVolume: Float,
    val audible: Boolean
)

data class TimelinePlaybackPlan(
    val playheadMs: Long,
    val activeClips: List<ScheduledClip>,
    val audioClips: List<ScheduledClip>,
    val videoClips: List<ScheduledClip>
) {
    companion object {
        fun at(project: EditorProject, playheadMs: Long): TimelinePlaybackPlan {
            require(playheadMs >= 0L) { "Playhead cannot be negative" }
            val active = project.tracks.flatMap { track ->
                track.clips.asSequence()
                    .filter { playheadMs >= it.timelineStartMs && playheadMs < it.timelineEndMs }
                    .map { clip ->
                        val elapsed = playheadMs - clip.timelineStartMs
                        val sourcePosition = (clip.sourceStartMs + elapsed)
                            .coerceIn(clip.sourceStartMs, clip.sourceEndMs)
                        val hasAudio = clip.kind == MediaKind.AUDIO || clip.kind == MediaKind.VIDEO
                        val muted = clip.audio.muted || track.muted
                        ScheduledClip(
                            clip = clip,
                            trackId = track.id,
                            trackKind = track.kind,
                            trackMuted = track.muted,
                            sourcePositionMs = sourcePosition,
                            effectiveVolume = if (hasAudio && !muted) clip.audio.volume else 0f,
                            audible = hasAudio && !muted && clip.audio.volume > 0f
                        )
                    }
            }.sortedWith(compareBy<ScheduledClip> { it.trackKind.ordinal }.thenBy { it.clip.timelineStartMs })
            return TimelinePlaybackPlan(
                playheadMs = playheadMs,
                activeClips = active,
                audioClips = active.filter { it.clip.kind == MediaKind.AUDIO && it.audible },
                videoClips = active.filter { it.clip.kind == MediaKind.VIDEO }
            )
        }
    }
}
