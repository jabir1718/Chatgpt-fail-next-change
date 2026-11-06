package com.creatorstudio.app.editor

import kotlin.math.PI
import kotlin.math.min

data class OfflineOrbGeometry(
    val radiusPx: Float,
    val beatPulse: Float,
    val averageBandEnergy: Float,
    val rotationRadians: Float
)

/** Pure frame math shared by the offline orb renderer and its regression tests. */
object OfflineOrbFrameMath {
    fun geometry(frame: OfflineVisualizerFrameData, width: Int, height: Int): OfflineOrbGeometry {
        require(width > 0 && height > 0) { "Orb frame dimensions must be positive" }
        val pulse = frame.beatPulse.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val bands = frame.spectrumBands
        val energy = if (bands.isEmpty()) 0f else bands
            .map { it.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f }
            .average().toFloat()
        val radius = min(width, height) * 0.19f * (1f + pulse * 0.13f)
        val rotation = (frame.timestampMs.coerceAtLeast(0L) % 12_000L).toFloat() /
            12_000f * (2f * PI.toFloat())
        return OfflineOrbGeometry(radius, pulse, energy, rotation)
    }
}
