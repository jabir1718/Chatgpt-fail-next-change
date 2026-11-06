package com.creatorstudio.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionSubtitleCodecTest {
    @Test
    fun parsesSrtCueIdsAndCommaTimestamps() {
        val parsed = CaptionSubtitleCodec.parse(
            """1
00:00:01,250 --> 00:00:02,500
Hello world!

2
00:00:03,000 --> 00:00:04,125
Second line
continues"""
        )
        assertEquals(2, parsed.size)
        assertEquals("Hello world!", parsed[0].text)
        assertEquals(1_250L, parsed[0].startMs)
        assertEquals(2_500L, parsed[0].endMs)
        assertEquals("Second line\ncontinues", parsed[1].text)
        assertEquals(4_125L, parsed[1].endMs)
    }

    @Test
    fun parsesWebVttHeaderCueSettingsAndDotTimestamps() {
        val parsed = CaptionSubtitleCodec.parse(
            """WEBVTT

intro
00:00:00.500 --> 00:00:01.750 align:start position:10%
<c.green>Welcome</c> back"""
        )
        assertEquals(1, parsed.size)
        assertEquals("Welcome back", parsed.single().text)
        assertEquals(500L, parsed.single().startMs)
        assertEquals(1_750L, parsed.single().endMs)
    }

    @Test
    fun parsesWebVttShortMinuteSecondTimestamp() {
        val parsed = CaptionSubtitleCodec.parse(
            """WEBVTT

00:12.345 --> 00:14.500
Short WebVTT timestamp"""
        )
        assertEquals(1, parsed.size)
        assertEquals(12_345L, parsed.single().startMs)
        assertEquals(14_500L, parsed.single().endMs)
    }

    @Test
    fun exportsSortedSrtAndWebVttWithCorrectTimestampSeparators() {
        val captions = listOf(
            CaptionSegment(id = "later", text = "Later", startMs = 65_250L, endMs = 66_500L),
            CaptionSegment(id = "first", text = "First", startMs = 1_000L, endMs = 2_000L)
        )
        val srt = CaptionSubtitleCodec.format(captions, CaptionSubtitleCodec.Format.SRT)
        assertTrue(srt.startsWith("1\n00:00:01,000 --> 00:00:02,000\nFirst"))
        assertTrue(srt.contains("2\n00:01:05,250 --> 00:01:06,500\nLater"))
        val vtt = CaptionSubtitleCodec.format(captions, CaptionSubtitleCodec.Format.WEBVTT)
        assertTrue(vtt.startsWith("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nFirst"))
        assertTrue(vtt.contains("00:01:05.250 --> 00:01:06.500"))
    }

    @Test
    fun preservesEmojiAndUrduTextInSubtitleRoundTrip() {
        val source = listOf(
            CaptionSegment(text = "🎵 سلام! ✨", startMs = 250L, endMs = 1_250L)
        )
        val encoded = CaptionSubtitleCodec.format(source, CaptionSubtitleCodec.Format.WEBVTT)
        val decoded = CaptionSubtitleCodec.parse(encoded)
        assertEquals("🎵 سلام! ✨", decoded.single().text)
        assertEquals(250L, decoded.single().startMs)
        assertEquals(1_250L, decoded.single().endMs)
    }

    @Test
    fun roundTripsPhraseCuesWithoutChangingTheirTiming() {
        val source = listOf(
            CaptionSegment(text = "One", startMs = 125L, endMs = 900L),
            CaptionSegment(text = "Two", startMs = 1_500L, endMs = 2_250L)
        )
        val parsed = CaptionSubtitleCodec.parse(CaptionSubtitleCodec.format(source, CaptionSubtitleCodec.Format.WEBVTT))
        assertEquals(source.map { it.text to (it.startMs to it.endMs) }, parsed.map { it.text to (it.startMs to it.endMs) })
    }
}
