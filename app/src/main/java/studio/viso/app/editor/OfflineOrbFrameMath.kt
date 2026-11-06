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
        // Match the live fragment shader's 0.22 radians/second rotation rate.
        val rotation = ((frame.timestampMs.coerceAtLeast(0L) / 1_000f) * 0.22f) % (2f * PI.toFloat())
        return OfflineOrbGeometry(radius, pulse, energy, rotation)
    }

    /** Nearest FFT band lookup using the same wrapped angular mapping as the live GLSL shader. */
    fun bandAtAngle(frame: OfflineVisualizerFrameData, angleRadians: Float): Float {
        val bands = frame.spectrumBands
        if (bands.isEmpty() || !angleRadians.isFinite()) return 0f
        val fullTurn = 2f * PI.toFloat()
        val wrapped = ((angleRadians + PI.toFloat()) % fullTurn + fullTurn) % fullTurn
        val index = (wrapped / fullTurn * bands.size).toInt().coerceIn(0, bands.lastIndex)
        return bands[index].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    }
}
