package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmFileAudioSourceTest {
    @Test
    fun readsAndInterpolatesInterleavedStereoFramesAcrossCacheWindows() {
        val file = File.createTempFile("creator-pcm", ".f32")
        try {
            val samples = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f)
            val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach(bytes::putFloat)
            file.writeBytes(bytes.array())

            PcmFileAudioSource(file, sampleRate = 8_000, channelCount = 2, cacheFrames = 2).use { source ->
                assertEquals(4L, source.frameCount)
                assertEquals(0.1f, source.sampleAt(0.0, 0, 2), 0.0001f)
                assertEquals(0.5f, source.sampleAt(1.5, 1, 2), 0.0001f)
                assertEquals(0.45f, source.sampleAt(1.5, 0, 1), 0.0001f)
                assertEquals(0f, source.sampleAt(4.0, 0, 2), 0.0001f)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun validatesPcmFileFrameAlignment() {
        val file = File.createTempFile("creator-pcm-invalid", ".f32")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val error = runCatching { PcmFileAudioSource(file, 8_000, 1) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        } finally {
            file.delete()
        }
    }
}
