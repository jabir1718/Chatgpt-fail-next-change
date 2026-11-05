package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSampleSizeTest {
    @Test
    fun downsamplesLargeImagesByPowerOfTwo() {
        assertEquals(2, calculateImageSampleSize(4_000, 4_000, 1_080, 1_920))
        assertEquals(4, calculateImageSampleSize(8_000, 6_000, 1_080, 1_920))
    }

    @Test
    fun leavesSmallImagesAtFullResolution() {
        assertEquals(1, calculateImageSampleSize(800, 600, 1_080, 1_920))
    }
}
