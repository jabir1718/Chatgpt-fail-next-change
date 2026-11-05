package com.creatorstudio.app.audio

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.creatorstudio.app.editor.EditorProject
import com.creatorstudio.app.editor.MediaKind
import com.creatorstudio.app.editor.TimelinePlaybackPlan
import com.creatorstudio.app.editor.pitchRatio

/**
 * Plays every audible audio-bearing clip that overlaps the timeline playhead.
 *
 * Each clip has its own Media3 player, allowing Android's audio output mixer to
 * combine concurrent sources while this controller keeps their source positions
 * aligned to one monotonic timeline clock.
 */
class TimelineAudioPreviewController(context: Context) {
    private data class ActiveSource(
        val player: ExoPlayer,
        val uri: String
    )

    private val appContext = context.applicationContext
    private val activePlayers = linkedMapOf<String, ActiveSource>()
    private var anchorTimelineMs = 0L
    private var anchorRealtimeMs = 0L
    var isPlaying: Boolean = false
        private set

    fun play(project: EditorProject, fromTimelineMs: Long) {
        anchorTimelineMs = fromTimelineMs.coerceIn(0L, project.durationMs)
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        isPlaying = true
        reconcile(project, anchorTimelineMs, forceSeek = true)
        if (project.durationMs <= anchorTimelineMs) stop()
    }

    fun pause(): Long {
        val position = currentPosition()
        isPlaying = false
        anchorTimelineMs = position
        activePlayers.values.forEach { it.player.pause() }
        return position
    }

    fun seek(project: EditorProject, timelineMs: Long) {
        anchorTimelineMs = timelineMs.coerceIn(0L, project.durationMs)
        anchorRealtimeMs = SystemClock.elapsedRealtime()
        if (isPlaying) reconcile(project, anchorTimelineMs, forceSeek = true)
    }

    fun update(project: EditorProject): Long {
        if (!isPlaying) return anchorTimelineMs
        val position = currentPosition()
        if (position >= project.durationMs) {
            anchorTimelineMs = project.durationMs
            stop()
            return anchorTimelineMs
        }
        reconcile(project, position, forceSeek = false)
        return position
    }

    fun stop() {
        isPlaying = false
        activePlayers.values.forEach { source ->
            source.player.playWhenReady = false
            source.player.release()
        }
        activePlayers.clear()
        anchorRealtimeMs = 0L
    }

    fun release() = stop()

    private fun currentPosition(): Long {
        if (!isPlaying) return anchorTimelineMs
        return anchorTimelineMs + (SystemClock.elapsedRealtime() - anchorRealtimeMs).coerceAtLeast(0L)
    }

    private fun reconcile(project: EditorProject, timelineMs: Long, forceSeek: Boolean) {
        val plan = TimelinePlaybackPlan.at(project, timelineMs)
        val desired = plan.audioClips.associateBy { it.clip.id }

        val removedIds = activePlayers.keys.filterNot { it in desired }
        removedIds.forEach { id ->
            activePlayers.remove(id)?.player?.release()
        }

        desired.values.forEach { scheduled ->
            val clip = scheduled.clip
            val asset = project.assets.firstOrNull { it.id == clip.mediaId } ?: return@forEach
            if (asset.kind != MediaKind.AUDIO && asset.kind != MediaKind.VIDEO) return@forEach

            val existing = activePlayers[clip.id]
            val isNew = existing == null || existing.uri != asset.uri
            val activeSource = if (isNew) {
                existing?.player?.release()
                val player = ExoPlayer.Builder(appContext).build()
                val created = ActiveSource(player, asset.uri)
                activePlayers[clip.id] = created
                player.setMediaItem(MediaItemFactory.create(Uri.parse(asset.uri), asset.mimeType))
                player.prepare()
                player.seekTo(scheduled.sourcePositionMs)
                created
            } else {
                val current = requireNotNull(existing)
                val drift = kotlin.math.abs(current.player.currentPosition - scheduled.sourcePositionMs)
                if (forceSeek || (current.player.playbackState == Player.STATE_READY && drift > 280L)) {
                    current.player.seekTo(scheduled.sourcePositionMs)
                }
                current
            }

            val player = activeSource.player
            player.volume = scheduled.effectiveVolume.coerceIn(0f, 1f)
            player.playbackParameters = PlaybackParameters(
                clip.audio.playbackRate,
                clip.audio.pitchRatio()
            )
            player.playWhenReady = true
        }
    }
}

/** Small helper keeps MIME metadata attached to imported content URIs. */
private object MediaItemFactory {
    fun create(uri: Uri, mimeType: String?): androidx.media3.common.MediaItem {
        val builder = androidx.media3.common.MediaItem.Builder().setUri(uri)
        if (!mimeType.isNullOrBlank()) builder.setMimeType(mimeType)
        return builder.build()
    }
}
