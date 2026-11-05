package com.creatorstudio.app.editor

/**
 * Deterministic, encoder-independent description of one output frame.
 *
 * This is the composition contract shared by a future preview/offline renderer.
 * It deliberately does not decode media, allocate bitmaps, or encode output.
 */
enum class RenderLayerRole { VIDEO, IMAGE, TEXT, VISUALIZER_BACKGROUND, VISUALIZER_FOREGROUND }

data class RenderLayerPlan(
    val assetId: String,
    val clipId: String?,
    val trackId: String?,
    val role: RenderLayerRole,
    val zOrder: Int,
    val sourcePositionMs: Long?,
    val transform: Transform,
    val contentMode: CanvasContentMode = CanvasContentMode.FIT,
    val activeCaptions: List<CaptionSegment> = emptyList()
)

data class RenderFramePlan(
    val frameIndex: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val layers: List<RenderLayerPlan>,
    val audio: List<ScheduledClip>,
    val audioProcessing: AudioProcessingSettings,
    val visualizerFrame: OfflineVisualizerFrameData? = null
)

/**
 * Builds deterministic frame/audio snapshots from an immutable project.
 * Frame timestamps use integer rational arithmetic to avoid cumulative drift.
 */
object RenderCompositionPlan {
    fun frameCount(project: EditorProject): Long {
        val duration = project.durationMs
        if (duration <= 0L) return 0L
        val wholeSeconds = duration / 1000L
        val remainingMs = duration % 1000L
        return wholeSeconds * project.frameRate +
            (remainingMs * project.frameRate + 999L) / 1000L
    }

    fun frameAt(project: EditorProject, frameIndex: Long, visualizerFrame: OfflineVisualizerFrameData? = null): RenderFramePlan {
        val totalFrames = frameCount(project)
        require(frameIndex in 0 until totalFrames) { "Frame index is outside the project duration" }
        val timestampMs = (frameIndex / project.frameRate) * 1000L +
            ((frameIndex % project.frameRate) * 1000L) / project.frameRate
        val playback = TimelinePlaybackPlan.at(project, timestampMs)
        val assetsById = project.assets.associateBy { it.id }
        val layers = mutableListOf<RenderLayerPlan>()

        project.visualizer.backgroundAssetId?.let { assetId ->
            layers += RenderLayerPlan(
                assetId = assetId,
                clipId = null,
                trackId = null,
                role = RenderLayerRole.VISUALIZER_BACKGROUND,
                zOrder = Int.MIN_VALUE,
                sourcePositionMs = null,
                transform = Transform()
            )
        }

        project.tracks.forEachIndexed { trackIndex, track ->
            track.clips.forEach { clip ->
                if (timestampMs < clip.timelineStartMs || timestampMs >= clip.timelineEndMs) return@forEach
                val asset = assetsById[clip.mediaId] ?: return@forEach
                val elapsedMs = timestampMs - clip.timelineStartMs
                val sourcePosition = if (clip.kind == MediaKind.VIDEO) {
                    (clip.sourceStartMs + (elapsedMs.toDouble() * clip.audio.playbackRate).toLong())
                        .coerceIn(clip.sourceStartMs, clip.sourceEndMs)
                } else clip.sourceStartMs
                val role = when (clip.kind) {
                    MediaKind.VIDEO -> RenderLayerRole.VIDEO
                    MediaKind.IMAGE -> RenderLayerRole.IMAGE
                    MediaKind.TEXT -> RenderLayerRole.TEXT
                    MediaKind.AUDIO -> return@forEach
                }
                val captions = clip.captions.filter { elapsedMs >= it.startMs && elapsedMs < it.endMs }
                layers += RenderLayerPlan(
                    assetId = asset.id,
                    clipId = clip.id,
                    trackId = track.id,
                    role = role,
                    zOrder = trackIndex,
                    sourcePositionMs = sourcePosition,
                    transform = clip.transform,
                    contentMode = clip.contentMode,
                    activeCaptions = captions
                )
            }
        }

        project.visualizer.foregroundAssetId?.let { assetId ->
            layers += RenderLayerPlan(
                assetId = assetId,
                clipId = null,
                trackId = null,
                role = RenderLayerRole.VISUALIZER_FOREGROUND,
                zOrder = Int.MAX_VALUE,
                sourcePositionMs = null,
                transform = Transform(scaleX = project.visualizer.foregroundScale, scaleY = project.visualizer.foregroundScale)
            )
        }

        return RenderFramePlan(
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            width = project.width,
            height = project.height,
            frameRate = project.frameRate,
            layers = layers.sortedBy { it.zOrder },
            audio = playback.audioClips,
            audioProcessing = project.audioProcessing,
            visualizerFrame = visualizerFrame
        )
    }
}
