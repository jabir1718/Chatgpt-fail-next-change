package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16ConversionTest {
    @Test
    fun convertsNormalizedFloatSamplesToSignedPcm16() {
        assertEquals((-32768).toShort(), floatToPcm16(-1f))
        assertEquals(0.toShort(), floatToPcm16(0f))
        assertEquals(16_383.toShort(), floatToPcm16(0.5f))
        assertEquals(32_767.toShort(), floatToPcm16(1f))
        assertEquals(32_767.toShort(), floatToPcm16(1.5f))
    }
}
