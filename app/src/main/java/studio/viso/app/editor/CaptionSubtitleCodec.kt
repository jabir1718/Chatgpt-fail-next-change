package com.creatorstudio.app.editor

/**
 * Basic interoperable SRT/WebVTT phrase-cue import and export.
 * Word-level karaoke timing remains project metadata and is not represented in SRT.
 */
object CaptionSubtitleCodec {
    enum class Format { SRT, WEBVTT }

    fun parse(content: String): List<CaptionSegment> {
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\\n[ \\t]*\\n+"))
        val captions = mutableListOf<CaptionSegment>()
        val timingLine = Regex("^((?:\\d{2,}:)?\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*((?:\\d{2,}:)?\\d{2}:\\d{2}[,.]\\d{3})(?:\\s+.*)?$")
        blocks.forEach { block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { timingLine.matches(it.trim()) }
            if (timingIndex < 0) return@forEach
            val match = timingLine.matchEntire(lines[timingIndex].trim()) ?: return@forEach
            val start = parseTimestamp(match.groupValues[1])
            val end = parseTimestamp(match.groupValues[2])
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
                .replace(Regex("<[^>]+>"), "")
            if (text.isNotBlank() && end > start) {
                captions += CaptionSegment(text = text, startMs = start, endMs = end)
            }
        }
        return captions.sortedWith(compareBy<CaptionSegment> { it.startMs }.thenBy { it.endMs })
    }

    fun format(captions: List<CaptionSegment>, format: Format): String {
        val ordered = captions.sortedWith(compareBy<CaptionSegment> { it.startMs }.thenBy { it.endMs })
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
        val body = ordered.mapIndexed { index, caption ->
            val separator = if (format == Format.SRT) "," else "."
            val timing = "${formatTimestamp(caption.startMs, separator)} --> ${formatTimestamp(caption.endMs, separator)}"
            if (format == Format.SRT) "${index + 1}\n$timing\n${caption.text.trim()}"
            else "$timing\n${caption.text.trim()}"
        }.joinToString("\n\n")
        return if (format == Format.WEBVTT) "WEBVTT\n\n$body" else body
    }

    private fun parseTimestamp(value: String): Long {
        val normalized = value.replace(',', '.')
        val parts = normalized.split(':')
        require(parts.size in 2..3) { "Invalid subtitle timestamp: $value" }
        val seconds = parts.last().split('.')
        require(seconds.size == 2) { "Invalid subtitle timestamp: $value" }
        val hours = if (parts.size == 3) parts[0].toLong() else 0L
        val minutes = if (parts.size == 3) parts[1].toLong() else parts[0].toLong()
        val wholeSeconds = seconds[0].toLong()
        val millis = seconds[1].padEnd(3, '0').take(3).toLong()
        require(minutes >= 0L && (parts.size == 2 || minutes in 0..59) && wholeSeconds in 0..59) {
            "Invalid subtitle timestamp: $value"
        }
        return (((hours * 60 + minutes) * 60 + wholeSeconds) * 1000) + millis
    }

    private fun formatTimestamp(valueMs: Long, separator: String): String {
        val safe = valueMs.coerceAtLeast(0L)
        val hours = safe / 3_600_000
        val minutes = (safe / 60_000) % 60
        val seconds = (safe / 1_000) % 60
        val millis = safe % 1_000
        return "%02d:%02d:%02d%s%03d".format(java.util.Locale.US, hours, minutes, seconds, separator, millis)
    }
}
