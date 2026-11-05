package com.creatorstudio.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Decodes imported audio to PCM peak buckets and caches the real source waveform. */
class WaveformRepository(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "waveforms").apply { mkdirs() }

    suspend fun getOrCreate(uriString: String, durationMs: Long): WaveformData? = withContext(Dispatchers.IO) {
        val file = cacheFile(uriString, durationMs)
        readCache(file, durationMs)?.let { return@withContext it }
        val result = runCatching { decode(Uri.parse(uriString), durationMs) }.getOrNull()
            ?: return@withContext null
        writeCache(file, result)
        result
    }

    private suspend fun decode(uri: Uri, fallbackDuration: Long): WaveformData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(app, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    track = i; format = candidate; break
                }
            }
            if (track < 0 || format == null) throw IOException("No audio stream in imported media")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Audio MIME type missing")
            val rate = format.intValue(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            val duration = (format.longValue(MediaFormat.KEY_DURATION, fallbackDuration * 1_000L) / 1_000L).coerceAtLeast(1L)
            val frames = (duration * rate / 1_000L).coerceAtLeast(1L)
            val reducer = WaveformAccumulator(frames, (duration / 35L).toInt().coerceIn(128, 2_048))
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputRate = rate
            var channels = format.intValue(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var encoding = MediaFormat.ENCODING_PCM_16BIT
            val started = System.currentTimeMillis()
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (System.currentTimeMillis() - started > 120_000L) throw IOException("Waveform decode timeout")
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: throw IOException("Decoder input unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        outputRate = out.intValue(MediaFormat.KEY_SAMPLE_RATE, rate)
                        channels = out.intValue(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        encoding = out.intValue(MediaFormat.KEY_PCM_ENCODING, MediaFormat.ENCODING_PCM_16BIT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (index >= 0) {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)?.duplicate() ?: throw IOException("Decoder output unavailable")
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.LITTLE_ENDIAN)
                            appendPcm(buffer, channels, encoding, reducer)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            return reducer.finish(outputRate, duration)
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; extractor.release()
        }
    }

    private fun appendPcm(buffer: java.nio.ByteBuffer, channels: Int, encoding: Int, reducer: WaveformAccumulator) {
        val bytes = when (encoding) { MediaFormat.ENCODING_PCM_FLOAT -> 4; MediaFormat.ENCODING_PCM_8BIT -> 1; else -> 2 }
        val channelCount = channels.coerceAtLeast(1)
        val frames = buffer.remaining() / (bytes * channelCount)
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var peak = 0f
            repeat(channelCount) {
                val sample = when (encoding) {
                    MediaFormat.ENCODING_PCM_FLOAT -> buffer.float
                    MediaFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128f
                    else -> buffer.short / 32768f
                }
                peak = maxOf(peak, kotlin.math.abs(sample))
            }
            mono[frame] = peak.coerceIn(0f, 1f)
        }
        reducer.addMonoSamples(mono)
    }

    private fun cacheFile(uri: String, duration: Long): File {
        val key = MessageDigest.getInstance("SHA-256").digest("$uri|$duration|v1".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.bin")
    }

    private fun readCache(file: File, duration: Long): WaveformData? = runCatching {
        if (!file.isFile || file.length() !in 24L..20_000L) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != 1) return null
            val savedDuration = input.readLong()
            val rate = input.readInt()
            val count = input.readInt()
            if (savedDuration <= 0L || rate <= 0 || count !in 1..2_048) return null
            val samples = List(count) { input.readFloat().coerceIn(0f, 1f) }
            if (input.available() != 0) return null
            WaveformData(samples, rate, savedDuration)
        }
    }.getOrNull()

    private fun writeCache(file: File, data: WaveformData) {
        val temp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC); out.writeInt(1); out.writeLong(data.durationMs)
                out.writeInt(data.sampleRate); out.writeInt(data.samples.size)
                data.samples.forEach(out::writeFloat)
            }
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { temp.delete() }
    }

    private fun MediaFormat.intValue(key: String, fallback: Int) = if (containsKey(key)) getInteger(key) else fallback
    private fun MediaFormat.longValue(key: String, fallback: Long) = if (containsKey(key)) getLong(key) else fallback

    companion object { private const val MAGIC = 0x57415645 }
}
