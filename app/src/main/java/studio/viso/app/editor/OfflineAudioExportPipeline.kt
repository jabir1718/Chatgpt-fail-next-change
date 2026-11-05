package com.creatorstudio.app.editor

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.math.ceil

data class OfflineAudioMixExportResult(val file: File, val durationMs: Long, val byteCount: Long)

/**
 * Decodes project audio sources to disk-backed PCM, mixes the timeline in blocks,
 * and encodes an AAC-LC M4A audio mix. This does not render video pixels.
 */
class OfflineAudioExportPipeline(
    private val decoder: MediaCodecPcmDecoder = MediaCodecPcmDecoder(),
    private val encoder: OfflineAudioAacEncoder = OfflineAudioAacEncoder()
) {
    fun exportAudioMix(
        context: Context,
        project: EditorProject,
        cacheDirectory: File,
        outputFile: File,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        bitRate: Int = 192_000,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): OfflineAudioMixExportResult {
        require(project.durationMs > 0L) { "Project has no timeline duration" }
        require(sampleRate >= 8_000) { "Output sample rate is too low" }
        require(channels in 1..2) { "Only mono and stereo output are supported" }
        cacheDirectory.mkdirs()

        val audioClips = project.tracks
            .filter { it.kind == TrackKind.AUDIO || it.kind == TrackKind.VIDEO }
            .flatMap { it.clips }
            .filter { it.kind == MediaKind.AUDIO || it.kind == MediaKind.VIDEO }
        val assetIds = audioClips.map { it.mediaId }.distinct()
        require(assetIds.isNotEmpty()) { "Project contains no audio-capable clips" }

        val sources = linkedMapOf<String, PcmAudioSource>()
        val temporaryFiles = mutableListOf<File>()
        try {
            assetIds.forEachIndexed { index, assetId ->
                if (shouldCancel()) throw CancellationException("Audio export cancelled")
                val asset = project.assets.first { it.id == assetId }
                val pcmFile = File(cacheDirectory, "pcm_${assetId.sha256()}.f32")
                temporaryFiles += pcmFile
                try {
                    val decoded = decoder.decodeToFile(
                        context = context,
                        uri = Uri.parse(asset.uri),
                        outputFile = pcmFile,
                        shouldCancel = shouldCancel
                    ) { decodeProgress ->
                        val overall = (index + decodeProgress.coerceIn(0f, 1f)) / assetIds.size
                        onProgress((overall * 0.7f).coerceIn(0f, 0.7f))
                    }
                    sources[assetId] = decoded.source
                } catch (missingAudio: NoAudioTrackException) {
                    val hasAudioOnlyClip = audioClips.any { it.mediaId == assetId && it.kind == MediaKind.AUDIO }
                    if (hasAudioOnlyClip) {
                        throw IllegalStateException("An audio clip has no decodable audio track: ${asset.displayName}", missingAudio)
                    }
                    pcmFile.delete()
                }
            }

            if (sources.isEmpty()) throw IllegalStateException("No audio tracks could be decoded from this project")
            if (shouldCancel()) throw CancellationException("Audio export cancelled")
            val mixer = OfflineAudioMixEngine(project, sources, sampleRate, channels)
            val totalFrames = ceil(project.durationMs.toDouble() * sampleRate / 1_000.0).toLong()
            val encoded = encoder.encode(mixer, totalFrames, outputFile, bitRate, shouldCancel) { encodeProgress ->
                onProgress((0.7f + encodeProgress.coerceIn(0f, 1f) * 0.3f).coerceIn(0f, 1f))
            }
            return OfflineAudioMixExportResult(encoded.file, encoded.durationMs, encoded.byteCount)
        } finally {
            sources.values.forEach { runCatching { it.close() } }
            temporaryFiles.forEach { it.delete() }
        }
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
