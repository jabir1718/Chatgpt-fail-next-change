package com.creatorstudio.app.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.math.ceil

data class OfflineVisualizerVideoExportResult(
    val file: File,
    val durationMs: Long,
    val frameCount: Long,
    val byteCount: Long
)

/**
 * First complete visualizer-video export path:
 * decode timeline audio, mix/encode AAC, analyze that same mix, decode still-image
 * layers, render each visualizer frame, encode H.264, then mux AAC + H.264 into MP4.
 *
 * Source video clips are deliberately rejected until their frame decoder is connected.
 */
class OfflineVisualizerVideoExportPipeline(
    private val audioDecoder: MediaCodecPcmDecoder = MediaCodecPcmDecoder(),
    private val audioEncoder: OfflineAudioAacEncoder = OfflineAudioAacEncoder(),
    private val videoEncoder: OfflineH264VideoEncoder = OfflineH264VideoEncoder(),
    private val muxer: OfflineAudioVideoMuxer = OfflineAudioVideoMuxer()
) {
    fun export(
        context: Context,
        project: EditorProject,
        cacheDirectory: File,
        outputFile: File,
        audioSampleRate: Int = 48_000,
        audioChannels: Int = 2,
        audioBitRate: Int = 192_000,
        videoBitRate: Int = 8_000_000,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): OfflineVisualizerVideoExportResult {
        require(project.durationMs > 0L) { "Project has no timeline duration" }
        require(project.width >= 64 && project.height >= 64 && project.width % 2 == 0 && project.height % 2 == 0) {
            "Video export canvas dimensions must be even and at least 64 pixels"
        }
        require(project.frameRate in 1..60) { "Video export frame rate must be between 1 and 60 FPS" }
        val sourceVideoClips = project.tracks.flatMap { it.clips }.filter { it.kind == MediaKind.VIDEO }
        require(sourceVideoClips.isEmpty()) {
            "This visualizer export stage supports audio + still images/PNG layers; source video clips are not connected yet"
        }

        cacheDirectory.mkdirs()
        val audioClips = project.tracks
            .filter { it.kind == TrackKind.AUDIO || it.kind == TrackKind.VIDEO }
            .flatMap { it.clips }
            .filter { it.kind == MediaKind.AUDIO }
        val audioAssetIds = audioClips.map { it.mediaId }.distinct()
        require(audioAssetIds.isNotEmpty()) { "Add at least one audio clip to drive the visualizer export" }
        val imageAssetIds = buildList {
            project.visualizer.backgroundAssetId?.let(::add)
            project.visualizer.foregroundAssetId?.let(::add)
            project.tracks.flatMap { it.clips }.filter { it.kind == MediaKind.IMAGE }.forEach { add(it.mediaId) }
        }.distinct()
        val imageAssets = imageAssetIds.map { id ->
            project.assets.firstOrNull { it.id == id } ?: error("Image asset is missing: $id")
        }
        require(imageAssets.all { it.kind == MediaKind.IMAGE }) {
            "Visualizer background, foreground and overlay references must point to still images"
        }

        val frameCount = RenderCompositionPlan.frameCount(project)
        require(frameCount > 0L) { "Project has no renderable video frames" }
        val audioEstimate = OfflineAudioStorageEstimator.estimate(
            project, audioAssetIds, audioSampleRate, audioChannels, audioBitRate
        )
        val estimatedVideoBytes = ceil(project.durationMs.toDouble() * videoBitRate / 8_000.0).toLong()
        val requiredFreeBytes = audioEstimate.requiredFreeBytes + estimatedVideoBytes * 2L
        val availableBytes = StatFs(cacheDirectory.absolutePath).availableBytes
        if (availableBytes < requiredFreeBytes) {
            throw InsufficientAudioExportStorageException(requiredFreeBytes, availableBytes)
        }

        val pcmFiles = mutableListOf<File>()
        val sources = linkedMapOf<String, PcmAudioSource>()
        val id = UUID.randomUUID().toString()
        val audioFile = File(cacheDirectory, "visualizer_audio_$id.m4a")
        val videoFile = File(cacheDirectory, "visualizer_video_$id.mp4")
        try {
            audioAssetIds.forEachIndexed { index, assetId ->
                if (shouldCancel()) throw CancellationException("Visualizer export cancelled")
                val asset = project.assets.first { it.id == assetId }
                val pcmFile = File(cacheDirectory, "visualizer_pcm_${assetId.sha256()}.f32")
                pcmFiles += pcmFile
                val decoded = audioDecoder.decodeToFile(
                    context = context,
                    uri = Uri.parse(asset.uri),
                    outputFile = pcmFile,
                    shouldCancel = shouldCancel
                ) { progress ->
                    val fraction = (index + progress.coerceIn(0f, 1f)) / audioAssetIds.size.coerceAtLeast(1)
                    onProgress(fraction * 0.14f)
                }
                sources[assetId] = decoded.source
            }

            if (shouldCancel()) throw CancellationException("Visualizer export cancelled")
            val mixer = OfflineAudioMixEngine(project, sources, audioSampleRate, audioChannels)
            val audioFrames = ceil(project.durationMs.toDouble() * audioSampleRate / 1_000.0).toLong()
            audioEncoder.encode(
                mixer, audioFrames, audioFile, audioBitRate, shouldCancel
            ) { progress -> onProgress(0.14f + progress * 0.22f) }

            val analysis = OfflineVisualizerAnalyzer.analyze(
                project = project,
                sources = sources,
                shouldCancel = shouldCancel
            ) { progress -> onProgress(0.36f + progress * 0.16f) }

            if (shouldCancel()) throw CancellationException("Visualizer export cancelled")
            ImageAssetBitmapLoader(context).use { loader ->
                val bitmaps = linkedMapOf<String, Bitmap>()
                imageAssets.forEachIndexed { index, asset ->
                    bitmaps[asset.id] = loader.load(asset, project.width, project.height)
                    onProgress(0.52f + (index + 1f) / imageAssets.size.coerceAtLeast(1) * 0.03f)
                }
                val renderer = OfflineVisualizerFrameRenderer()
                videoEncoder.encode(
                    width = project.width,
                    height = project.height,
                    frameRate = project.frameRate,
                    totalFrames = frameCount,
                    outputFile = videoFile,
                    bitRate = videoBitRate,
                    recycleFrames = true,
                    shouldCancel = shouldCancel,
                    onProgress = { progress -> onProgress(0.55f + progress * 0.34f) },
                    frameProvider = { frameIndex ->
                        val visualizerFrame = analysis.frames.getOrNull(frameIndex.toInt())
                        val framePlan = RenderCompositionPlan.frameAt(project, frameIndex, visualizerFrame)
                        renderer.render(project, framePlan, bitmaps)
                    }
                )
            }

            val muxed = muxer.mux(
                videoFile = videoFile,
                audioFile = audioFile,
                outputFile = outputFile,
                shouldCancel = shouldCancel
            ) { progress -> onProgress(0.89f + progress * 0.11f) }
            return OfflineVisualizerVideoExportResult(
                file = muxed.file,
                durationMs = project.durationMs,
                frameCount = frameCount,
                byteCount = muxed.byteCount
            )
        } finally {
            sources.values.forEach { runCatching { it.close() } }
            pcmFiles.forEach { it.delete() }
            audioFile.delete()
            videoFile.delete()
        }
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
