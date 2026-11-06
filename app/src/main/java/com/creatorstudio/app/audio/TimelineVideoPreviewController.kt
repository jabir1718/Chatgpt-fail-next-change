package com.creatorstudio.app.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.creatorstudio.app.editor.BeatResponseSettings
import com.creatorstudio.app.editor.CaptionSegment
import com.creatorstudio.app.editor.CanvasContentMode
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
    val contentMode: CanvasContentMode,
    val videoAspectRatio: Float,
    val player: ExoPlayer?,
    val imageBitmap: Bitmap?,
    val beatResponse: BeatResponseSettings = BeatResponseSettings(),
    val activeCaptions: List<CaptionSegment> = emptyList()
)

data class TimelineVideoPreviewSnapshot(
    val playheadMs: Long,
    val layers: List<TimelineVideoPreviewLayer>
)

@OptIn(UnstableApi::class)
class TimelineVideoPreviewController(context: Context) {
    private data class ActiveVideo(
        val player: ExoPlayer,
        val uri: String,
        var trackId: String,
        var zOrder: Int,
        var transform: Transform,
        var contentMode: CanvasContentMode,
        var beatResponse: BeatResponseSettings,
        var activeCaptions: List<CaptionSegment>
    )

    private val appContext = context.applicationContext
    private val activeVideos = linkedMapOf<String, ActiveVideo>()
    private val activeImages = linkedMapOf<String, TimelineVideoPreviewLayer>()
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
        activeImages.values.forEach { it.imageBitmap?.recycle() }
        activeImages.clear()
        anchorRealtimeMs = 0L
    }

    fun release() = stop()

    private fun currentPosition(): Long {
        if (!isPlaying) return anchorTimelineMs
        return anchorTimelineMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtLeast(0L)
    }

    private fun reconcile(project: EditorProject, timelineMs: Long, forceSeek: Boolean) {
        val trackOrder = project.tracks.mapIndexed { index, track -> track.id to index }.toMap()
        val activePlan = TimelinePlaybackPlan.at(project, timelineMs).activeClips
        val desired = activePlan.filter { it.clip.kind == MediaKind.VIDEO }
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

        val desiredImages = activePlan.filter { it.clip.kind == MediaKind.IMAGE }
        val desiredImageIds = desiredImages.mapTo(hashSetOf()) { it.clip.id }
        (activeImages.keys - desiredImageIds).forEach { id -> activeImages.remove(id)?.imageBitmap?.recycle() }
        desiredImages.forEach { scheduled ->
            val clip = scheduled.clip
            val asset = project.assets.first { it.id == clip.mediaId }
            val zOrder = (trackOrder[scheduled.trackId] ?: Int.MAX_VALUE) * 100_000 +
                (project.tracks.firstOrNull { it.id == scheduled.trackId }?.clips?.indexOfFirst { it.id == clip.id } ?: 0)
            val previous = activeImages[clip.id]
            val bitmap = previous?.imageBitmap ?: runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(Uri.parse(asset.uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = (largest / 1600).coerceAtLeast(1) }
                appContext.contentResolver.openInputStream(Uri.parse(asset.uri))?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
            activeImages[clip.id] = TimelineVideoPreviewLayer(
                clipId = clip.id, trackId = scheduled.trackId, zOrder = zOrder,
                transform = clip.transform, contentMode = clip.contentMode,
                videoAspectRatio = 0f, player = null, imageBitmap = bitmap,
                beatResponse = clip.beatResponse,
                activeCaptions = activeCaptionsAt(clip, timelineMs)
            )
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
                active = ActiveVideo(player, asset.uri, scheduled.trackId, zOrder, clip.transform, clip.contentMode, clip.beatResponse, activeCaptionsAt(clip, timelineMs))
                activeVideos[clip.id] = active
            } else {
                val drift = kotlin.math.abs(active.player.currentPosition - scheduled.sourcePositionMs)
                if (forceSeek || (active.player.playbackState == Player.STATE_READY && drift > 280L)) {
                    active.player.seekTo(scheduled.sourcePositionMs)
                }
                active.trackId = scheduled.trackId
                active.zOrder = zOrder
                active.transform = clip.transform
                active.contentMode = clip.contentMode
                active.beatResponse = clip.beatResponse
                active.activeCaptions = activeCaptionsAt(clip, timelineMs)
            }

            active.player.volume = 0f
            active.player.playbackParameters = PlaybackParameters(clip.audio.playbackRate, 1f)
            active.player.playWhenReady = isPlaying
        }
    }

    private fun activeCaptionsAt(clip: com.creatorstudio.app.editor.TimelineClip, timelineMs: Long): List<CaptionSegment> {
        val elapsed = (timelineMs - clip.timelineStartMs).coerceAtLeast(0L)
        return clip.captions.filter { elapsed >= it.startMs && elapsed < it.endMs }
    }

    private fun orderedLayers(): List<TimelineVideoPreviewLayer> =
        (activeVideos.map { (clipId, active) ->
            TimelineVideoPreviewLayer(
                clipId = clipId,
                trackId = active.trackId,
                zOrder = active.zOrder,
                transform = active.transform,
                contentMode = active.contentMode,
                videoAspectRatio = active.player.videoSize.let { size ->
                    if (size.width > 0 && size.height > 0) {
                        val rotated = size.unappliedRotationDegrees == 90 || size.unappliedRotationDegrees == 270
                        (if (rotated) size.height else size.width).toFloat() /
                            (if (rotated) size.width else size.height).toFloat()
                    } else 0f
                },
                player = active.player,
                imageBitmap = null,
                beatResponse = active.beatResponse,
                activeCaptions = active.activeCaptions
            )
        } + activeImages.values.toList()).sortedBy { it.zOrder }
}
