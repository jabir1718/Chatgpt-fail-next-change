package com.creatorstudio.app.audio

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.creatorstudio.app.editor.EditorProject
import com.creatorstudio.app.editor.MediaKind
import com.creatorstudio.app.editor.TimelinePlaybackPlan
import com.creatorstudio.app.editor.Transform

/**
 * Keeps all video clips active at a timeline position aligned to one monotonic
 * clock. Video players are muted because TimelineAudioPreviewController owns
 * the timeline soundtrack and mixes embedded video audio independently.
 */
data class TimelineVideoPreviewLayer(
    val clipId: String,
    val trackId: String,
    val zOrder: Int,
    val transform: Transform,
    val player: ExoPlayer
)

data class TimelineVideoPreviewSnapshot(
    val playheadMs: Long,
    val layers: List<TimelineVideoPreviewLayer>
)

class TimelineVideoPreviewController(context: Context) {
    private data class ActiveVideo(
        val player: ExoPlayer,
        val uri: String,
        var trackId: String,
        var zOrder: Int,
        var transform: Transform
    )

    private val appContext = context.applicationContext
    private val activeVideos = linkedMapOf<String, ActiveVideo>()
    private var anchorTimelineMs = 0L
    private var anchorRealtimeMs = 0L

    var isPlaying: Boolean = false
        private set

    fun play(project: EditorProject, fromTimelineMs: Long): TimelineVideoPreviewSnapshot {
        anchorTimelineMs = fromTimelineMs.coerceIn(0L, project.durationMs)
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        isPlaying = true
        reconcile(project, anchorTimelineMs, forceSeek = true)
        if (project.durationMs <= anchorTimelineMs) stop()
        return snapshot()
    }

    fun pause(): Long {
        val position = currentPosition()
        isPlaying = false
        anchorTimelineMs = position
        activeVideos.values.forEach { it.player.pause() }
        return position
    }

    fun seek(project: EditorProject, timelineMs: Long): TimelineVideoPreviewSnapshot {
        anchorTimelineMs = timelineMs.coerceIn(0L, project.durationMs)
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        reconcile(project, anchorTimelineMs, forceSeek = true)
        return snapshot()
    }

    fun update(project: EditorProject): TimelineVideoPreviewSnapshot {
        if (!isPlaying) return snapshot()
        val position = currentPosition()
        if (position >= project.durationMs) {
            anchorTimelineMs = project.durationMs
            stop()
            return TimelineVideoPreviewSnapshot(anchorTimelineMs, emptyList())
        }
        reconcile(project, position, forceSeek = false)
        return TimelineVideoPreviewSnapshot(position, snapshot().layers)
    }

    fun snapshot(): TimelineVideoPreviewSnapshot =
        TimelineVideoPreviewSnapshot(anchorTimelineMs, orderedLayers())

    fun stop() {
        isPlaying = false
        activeVideos.values.forEach { active ->
            active.player.playWhenReady = false
            active.player.clearVideoSurface()
            active.player.release()
        }
        activeVideos.clear()
        anchorRealtimeMs = 0L
    }

    fun release() = stop()

    private fun currentPosition(): Long {
        if (!isPlaying) return anchorTimelineMs
        return anchorTimelineMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtLeast(0L)
    }

    private fun reconcile(project: EditorProject, timelineMs: Long, forceSeek: Boolean) {
        val trackOrder = project.tracks.mapIndexed { index, track -> track.id to index }.toMap()
        val desired = TimelinePlaybackPlan.at(project, timelineMs).videoClips
            .filter { scheduled -> project.assets.any { it.id == scheduled.clip.mediaId && it.kind == MediaKind.VIDEO } }
            .sortedWith(
                compareBy<com.creatorstudio.app.editor.ScheduledClip> { trackOrder[it.trackId] ?: Int.MAX_VALUE }
                    .thenBy { it.clip.timelineStartMs }
                    .thenBy { it.clip.id }
            )
        val desiredIds = desired.mapTo(hashSetOf()) { it.clip.id }

        (activeVideos.keys - desiredIds).forEach { clipId ->
            activeVideos.remove(clipId)?.let { active ->
                active.player.playWhenReady = false
                active.player.clearVideoSurface()
                active.player.release()
            }
        }

        desired.forEach { scheduled ->
            val clip = scheduled.clip
            val asset = project.assets.first { it.id == clip.mediaId }
            val zOrder = (trackOrder[scheduled.trackId] ?: Int.MAX_VALUE) * 100_000 +
                (project.tracks.firstOrNull { it.id == scheduled.trackId }?.clips?.indexOfFirst { it.id == clip.id } ?: 0)
            var active = activeVideos[clip.id]
            if (active == null || active.uri != asset.uri) {
                active?.let { old ->
                    old.player.clearVideoSurface()
                    old.player.release()
                }
                val player = ExoPlayer.Builder(appContext).build()
                player.volume = 0f
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.setMediaItem(
                    MediaItem.Builder().setUri(Uri.parse(asset.uri)).apply {
                        if (!asset.mimeType.isNullOrBlank()) setMimeType(asset.mimeType)
                    }.build()
                )
                player.prepare()
                player.seekTo(scheduled.sourcePositionMs)
                active = ActiveVideo(player, asset.uri, scheduled.trackId, zOrder, clip.transform)
                activeVideos[clip.id] = active
            } else {
                val drift = kotlin.math.abs(active.player.currentPosition - scheduled.sourcePositionMs)
                if (forceSeek || (active.player.playbackState == Player.STATE_READY && drift > 280L)) {
                    active.player.seekTo(scheduled.sourcePositionMs)
                }
                active.trackId = scheduled.trackId
                active.zOrder = zOrder
                active.transform = clip.transform
            }

            active.player.volume = 0f
            active.player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, 1f)
            active.player.playWhenReady = isPlaying
        }
    }

    private fun orderedLayers(): List<TimelineVideoPreviewLayer> =
        activeVideos.map { (clipId, active) ->
            TimelineVideoPreviewLayer(
                clipId = clipId,
                trackId = active.trackId,
                zOrder = active.zOrder,
                transform = active.transform,
                player = active.player
            )
        }.sortedBy { it.zOrder }
}
