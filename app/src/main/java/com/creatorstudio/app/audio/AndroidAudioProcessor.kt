package com.creatorstudio.app.audio

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import com.creatorstudio.app.editor.AudioProcessingSettings

/**
 * Attaches Android's native DynamicsProcessing engine to a Media3 audio session.
 * The platform engine performs EQ and multi-band compression in the playback path.
 * API 26-27 and devices without a working effect engine safely fall back.
 */
class AndroidAudioProcessor {
    private val effects = mutableMapOf<Int, DynamicsProcessing>()

    fun apply(audioSessionId: Int, settings: AudioProcessingSettings): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioSessionId <= 0) return false
        return runCatching {
            val effect = effects[audioSessionId] ?: createEffect(audioSessionId).also { effects[audioSessionId] = it }
            applySettings(effect, settings)
            effect.enabled = true
            true
        }.getOrDefault(false)
    }

    fun release(audioSessionId: Int) {
        effects.remove(audioSessionId)?.let { runCatching { it.release() } }
    }

    private fun createEffect(audioSessionId: Int): DynamicsProcessing {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2,
            true, 5, true, 1, false, 0, false
        )
        repeat(2) { channelIndex ->
            val channel = DynamicsProcessing.Channel(0f, true, 5, true, 1, false, 0, false)
            val eq = DynamicsProcessing.Eq(true, true, 5)
            EQ_FREQUENCIES.forEachIndexed { index, frequency ->
                eq.setBand(index, DynamicsProcessing.EqBand(true, frequency, 0f))
            }
            channel.setPreEq(eq)
            val mbc = DynamicsProcessing.Mbc(true, true, 1)
            mbc.setBand(0, DynamicsProcessing.MbcBand(true, 20000f, 10f, 120f, 1f, 0f, 6f, -90f, 1f, 0f, 0f))
            channel.setMbc(mbc)
            builder.setChannelTo(channelIndex, channel)
        }
        return DynamicsProcessing(0, audioSessionId, builder.build()).apply { enabled = true }
    }

    private fun applySettings(effect: DynamicsProcessing, settings: AudioProcessingSettings) {
        repeat(effect.channelCount) { channel ->
            settings.eqGainsDb.forEachIndexed { bandIndex, baseGain ->
                val gain = (baseGain + if (bandIndex == 0) settings.bassBoostDb else 0f).coerceIn(-12f, 12f)
                val band = DynamicsProcessing.EqBand(effect.getPreEqBandByChannelIndex(channel, bandIndex))
                band.setGain(gain)
                effect.setPreEqBandByChannelIndex(channel, bandIndex, band)
            }
            val amount = settings.compressorAmount
            val band = DynamicsProcessing.MbcBand(effect.getMbcBandByChannelIndex(channel, 0))
            band.setAttackTime(10f)
            band.setReleaseTime(120f)
            band.setRatio(1f + amount * 7f)
            band.setThreshold(-36f * amount)
            effect.setMbcBandByChannelIndex(channel, 0, band)
        }
    }

    private companion object {
        val EQ_FREQUENCIES = floatArrayOf(80f, 250f, 1000f, 4000f, 12000f)
    }
}
