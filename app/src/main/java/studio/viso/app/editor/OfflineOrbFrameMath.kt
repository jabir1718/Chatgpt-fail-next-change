package com.creatorstudio.app.editor

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.floor

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
        // Match the live fragment shader's 0.22 radians/second rotation rate.
        val rotation = ((frame.timestampMs.coerceAtLeast(0L) / 1_000f) * 0.22f) % (2f * PI.toFloat())
        return OfflineOrbGeometry(radius, pulse, energy, rotation)
    }

    /** Nearest FFT band lookup using the same wrapped angular mapping as the live GLSL shader. */
    fun bandAtAngle(frame: OfflineVisualizerFrameData, angleRadians: Float): Float {
        val bands = frame.spectrumBands
        if (bands.isEmpty() || !angleRadians.isFinite()) return 0f
        // Use Double precision and floor-based wrapping to avoid Float rounding
        // turning an exact half-turn (angle 0) into band 15 instead of band 16.
        val fullTurn = 2.0 * PI
        val normalized = angleRadians.toDouble() / fullTurn + 0.5
        val rawWrapped = ((normalized % 1.0) + 1.0) % 1.0
        // Float PI inputs can land a few ulps below the -PI seam; snap that seam to band 0.
        val wrapped = if (rawWrapped > 1.0 - 1e-6) 0.0 else rawWrapped
        val index = floor(wrapped * bands.size).toInt().coerceIn(0, bands.lastIndex)
        return bands[index].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    }
}
