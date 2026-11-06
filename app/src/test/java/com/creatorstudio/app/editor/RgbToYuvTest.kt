package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test
import android.graphics.Color

class RgbToYuvTest {
    @Test
    fun convertsPrimaryAndNeutralColorsToLimitedRangeYuv() {
        assertEquals(YuvPixel(16, 128, 128), rgbToYuv(Color.BLACK))
        assertEquals(YuvPixel(235, 128, 128), rgbToYuv(Color.WHITE))
        assertEquals(YuvPixel(82, 90, 240), rgbToYuv(Color.RED))
        assertEquals(YuvPixel(144, 54, 34), rgbToYuv(Color.GREEN))
        assertEquals(YuvPixel(41, 240, 110), rgbToYuv(Color.BLUE))
    }
}
