package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAssetFrameDecoderTest {
    @Test
    fun convertsSourceMillisecondsToMicrosecondsWithoutFrameRateRounding() {
        assertEquals(0L, videoSourceTimeUs(0L))
        assertEquals(1_234_000L, videoSourceTimeUs(1_234L))
    }

    @Test
    fun scalesWideVideoToFitCanvasWithoutChangingAspectRatio() {
        assertEquals(960 to 540, scaledVideoFrameSize(1920, 1080, 1080, 1920))
    }

    @Test
    fun scalesPortraitVideoToFitCanvasWithoutChangingAspectRatio() {
        assertEquals(540 to 960, scaledVideoFrameSize(1080, 1920, 1920, 1080))
    }

    @Test
    fun doesNotUpscaleSmallSourceFrames() {
        assertEquals(320 to 180, scaledVideoFrameSize(320, 180, 1080, 1920))
    }
}
