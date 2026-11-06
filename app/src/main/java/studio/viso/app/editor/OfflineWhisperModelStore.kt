package com.creatorstudio.app.editor

import android.content.Context
import android.os.StatFs
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * Downloads the multilingual Whisper-small ONNX pack only after the user asks for
 * transcription. Files remain in app-private storage; imported media is never uploaded.
 *
 * The selected model is the "swift" quantized export, approximately 360 MB total.
 * It is based on OpenAI Whisper-small and published under Apache-2.0 in the model card.
 */
object OfflineWhisperModelStore {
    private const val MODEL_REVISION = "9721eb7"
    private const val MODEL_BASE =
        "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/$MODEL_REVISION/whisper-multilingual-swift"
    private const val TOKENS_URL =
        "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/$MODEL_REVISION/whisper-multilingual-swift/tokens.txt"
    private const val MIN_MODEL_BYTES = 10L * 1024L * 1024L
    private const val MIN_TOKENS_BYTES = 1_024L

    data class Fileset(
        val encoder: File,
        val decoder: File,
        val tokens: File
    ) {
        val isComplete: Boolean
            get() = encoder.isFile && encoder.length() >= MIN_MODEL_BYTES &&
                decoder.isFile && decoder.length() >= MIN_MODEL_BYTES &&
                tokens.isFile && tokens.length() >= MIN_TOKENS_BYTES
    }

    fun files(context: Context): Fileset {
        val directory = File(context.filesDir, "speech-models/whisper-multilingual-swift")
        return Fileset(
            encoder = File(directory, "encoder.int8.onnx"),
            decoder = File(directory, "decoder.int8.onnx"),
            tokens = File(directory, "tokens.txt")
        )
    }

    fun isInstalled(context: Context): Boolean = files(context).isComplete

    /**
     * Downloads three model files with per-file progress. Cancellation removes only
     * the incomplete .part file; already completed files are retained for the next try.
     */
    fun download(
        context: Context,
        shouldCancel: () -> Boolean = { false },
        onProgress: (fileIndex: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): Fileset {
        val files = files(context)
        files.encoder.parentFile?.mkdirs()
        val specs = listOf(
            Triple("$MODEL_BASE/encoder.int8.onnx", files.encoder, MIN_MODEL_BYTES),
            Triple("$MODEL_BASE/decoder.int8.onnx", files.decoder, MIN_MODEL_BYTES),
            Triple(TOKENS_URL, files.tokens, MIN_TOKENS_BYTES)
        )
        val missingEstimate = specs.filterNot { (_, destination, minimum) ->
            destination.isFile && destination.length() >= minimum
        }.sumOf { (_, _, minimum) -> if (minimum == MIN_MODEL_BYTES) 180L * 1024L * 1024L else 0L }
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        if (available < missingEstimate + 32L * 1024L * 1024L) {
            throw IOException("Not enough free app storage for the speech model. Free at least 400 MB and try again.")
        }

        specs.forEachIndexed { index, (url, destination, minimumBytes) ->
            if (shouldCancel()) throw CancellationException("Model download cancelled")
            if (destination.isFile && destination.length() >= minimumBytes) {
                onProgress(index, destination.length(), destination.length())
            } else {
                downloadFile(url, destination, minimumBytes, shouldCancel) { downloaded, total ->
                    onProgress(index, downloaded, total)
                }
            }
        }
        if (!files.isComplete) throw IOException("Speech model download is incomplete. Please retry.")
        return files
    }

    fun createRecognizer(context: Context): OfflineRecognizer {
        val files = files(context)
        check(files.isComplete) { "Download the speech model before starting transcription." }
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = files.encoder.absolutePath,
                decoder = files.decoder.absolutePath,
                language = "", // Empty enables Whisper's automatic language detection.
                task = "transcribe",
                tailPaddings = 300,
                enableTokenTimestamps = true
            ),
            tokens = files.tokens.absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        return OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model))
    }

    private fun downloadFile(
        sourceUrl: String,
        destination: File,
        minimumBytes: Long,
        shouldCancel: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        if (temporary.exists()) temporary.delete()
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CreatorStudio-Android")
        }
        try {
            connection.connect()
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Model server returned HTTP $status")
            val total = connection.contentLengthLong
            var downloaded = 0L
            BufferedInputStream(connection.inputStream, 64 * 1024).use { input ->
                BufferedOutputStream(FileOutputStream(temporary), 64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (shouldCancel()) throw CancellationException("Model download cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }
            if (total > 0L && downloaded != total) {
                throw IOException("Model download ended early ($downloaded of $total bytes). Please retry.")
            }
            if (downloaded < minimumBytes) throw IOException("Downloaded model file is too small to be valid.")
            if (destination.exists() && !destination.delete()) {
                throw IOException("Could not replace the previous model file.")
            }
            if (!temporary.renameTo(destination)) throw IOException("Could not save the downloaded model file.")
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
