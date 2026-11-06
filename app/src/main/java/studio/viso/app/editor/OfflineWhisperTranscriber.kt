package com.creatorstudio.app.editor

import android.content.Context
import java.util.concurrent.CancellationException
import kotlin.math.min
import kotlin.math.max

data class OfflineTranscriptionChunk(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val languageTag: String?
)

data class OfflineTranscriptionResult(
    val chunks: List<OfflineTranscriptionChunk>,
    val detectedLanguage: String?
)

/**
 * Offline Whisper transcription over a disk-backed decoded media source.
 * Audio is read in short windows so long media files are never copied wholesale
 * into a FloatArray. Whisper token timestamps are grouped into short caption segments;
 * if timing metadata is absent or malformed, the recognizer window is retained as fallback.
 */
object OfflineWhisperTranscriber {
    private const val WINDOW_SECONDS = 25L

    fun transcribe(
        context: Context,
        source: PcmAudioSource,
        sourceStartMs: Long,
        durationMs: Long,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): OfflineTranscriptionResult {
        require(sourceStartMs >= 0L) { "Source start time cannot be negative." }
        require(durationMs > 0L) { "Select a media clip with a valid duration." }
        val sampleRate = source.sampleRate
        val startFrame = sourceStartMs * sampleRate / 1_000L
        val requestedFrames = durationMs * sampleRate / 1_000L
        val availableFrames = (source.frameCount - startFrame).coerceAtLeast(0L)
        val totalFrames = min(requestedFrames, availableFrames)
        require(totalFrames > 0L) { "The selected source range contains no decodable audio." }

        val recognizer = OfflineWhisperModelStore.createRecognizer(context)
        val chunks = mutableListOf<OfflineTranscriptionChunk>()
        try {
            val windowFrames = WINDOW_SECONDS * sampleRate
            var processedFrames = 0L
            while (processedFrames < totalFrames) {
                if (shouldCancel()) throw CancellationException("Transcription cancelled")
                val frameCount = min(windowFrames, totalFrames - processedFrames).toInt()
                val windowStartFrame = startFrame + processedFrames
                val samples = if (source is PcmFileAudioSource) {
                    source.readMonoSamples(windowStartFrame, frameCount)
                } else {
                    FloatArray(frameCount) { index ->
                        source.sampleAt(
                            framePosition = (windowStartFrame + index).toDouble(),
                            outputChannel = 0,
                            outputChannelCount = 1
                        )
                    }
                }
                require(samples.size == frameCount) {
                    "The decoded audio ended before the selected transcription range."
                }
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, sampleRate = sampleRate)
                    recognizer.decode(stream)
                    val recognized = recognizer.getResult(stream)
                    val text = recognized.text.trim()
                    if (text.isNotBlank()) {
                        val chunkStartMs = processedFrames * 1_000L / sampleRate
                        val chunkEndMs = (processedFrames + frameCount) * 1_000L / sampleRate
                        chunks += buildTokenTimedCaptionChunks(
                            tokens = recognized.tokens.toList(),
                            timestampsSeconds = recognized.timestamps.toList(),
                            fallbackText = text,
                            chunkStartMs = chunkStartMs,
                            chunkEndMs = chunkEndMs,
                            languageTag = recognized.lang.takeIf { it.isNotBlank() }
                        )
                    }
                } finally {
                    stream.release()
                }
                processedFrames += frameCount
                onProgress((processedFrames.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f))
            }
        } finally {
            recognizer.release()
        }

        if (chunks.isEmpty()) throw IllegalStateException("No speech was recognized in the selected media.")
        val detectedLanguage = chunks.mapNotNull { it.languageTag }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return OfflineTranscriptionResult(chunks, detectedLanguage)
    }
}


/**
 * Converts Whisper's per-token start timestamps into readable phrase-sized captions.
 * Token times are relative to the current recognizer window. Missing/mismatched timing
 * metadata falls back to one window-level caption rather than inventing precision.
 */
internal fun buildTokenTimedCaptionChunks(
    tokens: List<String>,
    timestampsSeconds: List<Float>,
    fallbackText: String,
    chunkStartMs: Long,
    chunkEndMs: Long,
    languageTag: String?,
    maxCaptionMs: Long = 4_500L,
    maxCaptionChars: Int = 42
): List<OfflineTranscriptionChunk> {
    require(chunkStartMs >= 0L && chunkEndMs >= chunkStartMs)
    require(maxCaptionMs > 0L && maxCaptionChars > 0)
    fun fallback(): List<OfflineTranscriptionChunk> =
        if (fallbackText.isBlank() || chunkEndMs <= chunkStartMs) emptyList()
        else listOf(OfflineTranscriptionChunk(fallbackText.trim(), chunkStartMs, chunkEndMs, languageTag))

    if (tokens.isEmpty() || tokens.size != timestampsSeconds.size) return fallback()
    data class TimedToken(val text: String, val startMs: Long)
    val duration = chunkEndMs - chunkStartMs
    val clean = tokens.indices.mapNotNull { index ->
        val raw = tokens[index]
        if (raw.startsWith("<|") && raw.endsWith("|>")) return@mapNotNull null
        val value = raw.replace("▁", " ").replace("Ġ", " ").trim()
        if (value.isBlank()) return@mapNotNull null
        val seconds = timestampsSeconds[index]
        if (!seconds.isFinite() || seconds < 0f) return@mapNotNull null
        TimedToken(value, (seconds * 1_000f).toLong().coerceIn(0L, (duration - 1L).coerceAtLeast(0L)))
    }.sortedBy { it.startMs }
    if (clean.isEmpty()) return fallback()

    fun join(parts: List<String>): String {
        val out = StringBuilder()
        parts.forEach { part ->
            val punctuation = part.firstOrNull()?.let { it in ".,!?;:%)]}»”’" } ?: false
            if (out.isNotEmpty() && !punctuation) out.append(' ')
            out.append(part)
        }
        return out.toString().trim()
    }

    val groups = mutableListOf<MutableList<TimedToken>>()
    var current = mutableListOf<TimedToken>()
    for (token in clean) {
        if (current.isNotEmpty()) {
            val currentText = join(current.map { it.text })
            val gap = token.startMs - current.last().startMs
            val elapsed = token.startMs - current.first().startMs
            val endsSentence = current.last().text.lastOrNull()?.let { it in ".!?;:" } ?: false
            val wouldOverflow = join(current.map { it.text } + token.text).length > maxCaptionChars
            if (endsSentence || gap > 800L || elapsed >= maxCaptionMs || wouldOverflow) {
                groups += current
                current = mutableListOf()
            }
        }
        current += token
    }
    if (current.isNotEmpty()) groups += current

    return groups.mapIndexedNotNull { index, group ->
        val start = (chunkStartMs + group.first().startMs).coerceIn(chunkStartMs, chunkEndMs)
        val nextStart = groups.getOrNull(index + 1)?.first()?.startMs
        val lastToken = group.last().startMs
        val estimatedEnd = if (nextStart != null) chunkStartMs + nextStart
            else chunkStartMs + lastToken + 400L
        val end = min(chunkEndMs, max(start + 1L, estimatedEnd))
        if (end <= start) null else OfflineTranscriptionChunk(
            text = join(group.map { it.text }),
            startMs = start,
            endMs = end,
            languageTag = languageTag
        )
    }.ifEmpty { fallback() }
}


/**
 * Best-effort Urdu-script to Roman Urdu transliteration.
 * Roman Urdu has no single standard spelling; output remains editable and
 * should be reviewed. Non-Urdu text and punctuation are preserved.
 */
internal fun transliterateUrduToRoman(text: String): String {
    val commonWords = mapOf(
        "پاکستان" to "Pakistan", "سلام" to "salaam", "آپ" to "aap", "کیسے" to "kaise",
        "میں" to "mein", "ہے" to "hai", "ہیں" to "hain", "اور" to "aur",
        "کیا" to "kya", "نہیں" to "nahin", "بہت" to "bohat", "شکریہ" to "shukriya"
    )
    val letters = mapOf(
        'ا' to "a", 'آ' to "aa", 'ب' to "b", 'پ' to "p", 'ت' to "t",
        'ٹ' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h",
        'خ' to "kh", 'د' to "d", 'ڈ' to "d", 'ذ' to "z", 'ر' to "r",
        'ڑ' to "r", 'ز' to "z", 'ژ' to "zh", 'س' to "s", 'ش' to "sh",
        'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z", 'ع' to "",
        'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ک' to "k", 'گ' to "g",
        'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n", 'و' to "o",
        'ہ' to "h", 'ھ' to "h", 'ء' to "", 'ی' to "i", 'ے' to "e",
        'ؤ' to "o", 'ئ' to "i", 'ۃ' to "a"
    )
    fun isWordCharacter(ch: Char): Boolean {
        val type = Character.getType(ch)
        return ch.isLetter() || type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }
    return Regex("\\S+|\\s+").findAll(text).joinToString("") { match ->
        val token = match.value
        if (token.isBlank()) token
        else {
            var wordStart = 0
            while (wordStart < token.length && !isWordCharacter(token[wordStart])) wordStart++
            var wordEnd = token.length
            while (wordEnd > wordStart && !isWordCharacter(token[wordEnd - 1])) wordEnd--
            if (wordStart == wordEnd) token
            else {
                val prefix = token.substring(0, wordStart)
                val word = token.substring(wordStart, wordEnd)
                val suffix = token.substring(wordEnd)
                val roman = commonWords[word] ?: buildString {
                    word.forEach { ch ->
                        append(letters[ch] ?: if (ch.code in 0x064B..0x065F || ch.code == 0x0670) "" else ch.toString())
                    }
                }
                prefix + roman + suffix
            }
        }
    }
}

/**
 * Maps source-clip-relative transcription windows onto the selected visual clip.
 * The source clip's timeline offset and playback rate are respected, then captions
 * are clipped to the target clip's valid local time range.
 */
fun alignTranscriptionChunks(
    chunks: List<OfflineTranscriptionChunk>,
    sourceTimelineStartMs: Long,
    targetTimelineStartMs: Long,
    targetDurationMs: Long,
    sourcePlaybackRate: Float = 1f
): List<CaptionSegment> {
    require(targetDurationMs > 0L) { "Target clip duration must be positive." }
    require(sourcePlaybackRate in 0.25f..4f) { "Source playback rate is out of range." }
    val offsetMs = sourceTimelineStartMs - targetTimelineStartMs
    return chunks.mapNotNull { chunk ->
        if (chunk.endMs <= chunk.startMs || chunk.text.isBlank()) return@mapNotNull null
        val localStart = offsetMs + (chunk.startMs / sourcePlaybackRate).toLong()
        val localEnd = offsetMs + (chunk.endMs / sourcePlaybackRate).toLong()
        val start = localStart.coerceIn(0L, targetDurationMs)
        val end = localEnd.coerceIn(0L, targetDurationMs)
        if (end <= start) null else CaptionSegment(
            text = chunk.text,
            startMs = start,
            endMs = end,
            languageTag = chunk.languageTag
        )
    }
}
