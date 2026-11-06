package com.creatorstudio.app.editor

import android.content.Context
import java.util.concurrent.CancellationException
import kotlin.math.min

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
 * into a FloatArray. Current draft caption timing is window-level (25 seconds).
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
                val samples = FloatArray(frameCount) { index ->
                    source.sampleAt(
                        framePosition = (startFrame + processedFrames + index).toDouble(),
                        outputChannel = 0,
                        outputChannelCount = 1
                    )
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
                        chunks += OfflineTranscriptionChunk(
                            text = text,
                            startMs = chunkStartMs,
                            endMs = chunkEndMs,
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
