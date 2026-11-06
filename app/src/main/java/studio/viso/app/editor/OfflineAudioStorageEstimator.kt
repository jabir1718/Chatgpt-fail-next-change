package com.creatorstudio.app.editor

import kotlin.math.ceil

data class AudioExportStorageEstimate(
    val decodedPcmBytes: Long,
    val encodedAudioBytes: Long,
    val safetyReserveBytes: Long,
    val requiredFreeBytes: Long
)

class InsufficientAudioExportStorageException(
    val requiredBytes: Long,
    val availableBytes: Long
) : IllegalStateException(
    "Not enough free storage for audio export. Need approximately ${requiredBytes / (1024 * 1024)} MB; available ${availableBytes / (1024 * 1024)} MB."
)

object OfflineAudioStorageEstimator {
    fun estimate(
        project: EditorProject,
        assetIds: List<String>,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        bitRate: Int = 192_000,
        safetyReserveBytes: Long = 64L * 1024L * 1024L
    ): AudioExportStorageEstimate {
        require(sampleRate > 0)
        require(channels in 1..2)
        require(bitRate > 0)
        require(safetyReserveBytes >= 0L)
        val assets = project.assets.associateBy { it.id }
        val decodedDurationMs = assetIds.distinct().sumOf { id ->
            val asset = assets[id] ?: error("Audio source asset is missing: $id")
            (asset.durationMs ?: project.durationMs).coerceAtLeast(0L)
        }
        val decodedBytes = ceil(decodedDurationMs.toDouble() * sampleRate * channels * 4.0 / 1_000.0).toLong()
        val encodedBytes = ceil(project.durationMs.toDouble() * bitRate / 8_000.0).toLong()
        val required = decodedBytes + encodedBytes + safetyReserveBytes
        return AudioExportStorageEstimate(decodedBytes, encodedBytes, safetyReserveBytes, required)
    }
}
