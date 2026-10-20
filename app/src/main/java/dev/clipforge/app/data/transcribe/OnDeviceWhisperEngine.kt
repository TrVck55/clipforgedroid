package dev.clipforge.app.data.transcribe

import dev.clipforge.app.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fully offline transcription via whisper.cpp (JNI). Requires:
 *  1. The native lib to have been built (whisper.cpp submodule present at build time), and
 *  2. A ggml model file imported by the user into filesDir/whisper/model.bin
 *     (Settings -> Transcription -> "Import on-device model").
 *
 * Use [WhisperLib.isAvailable] + [modelFile] existence to decide whether to
 * offer this engine at all — see SettingsScreen.
 */
class OnDeviceWhisperEngine(private val modelFile: File) : TranscriptionEngine {

    override suspend fun transcribe(
        audioFile: File,
        onProgress: (String) -> Unit,
    ): List<TranscriptSegment> = withContext(Dispatchers.Default) {
        check(WhisperLib.isAvailable) { "On-device Whisper native library not built into this APK." }
        check(modelFile.exists()) { "No on-device model imported yet (Settings -> Transcription)." }

        onProgress("Loading model…")
        val ctx = WhisperLib.initContext(modelFile.absolutePath)
        check(ctx != 0L) { "Failed to load Whisper model at ${modelFile.absolutePath}" }

        try {
            onProgress("Reading audio…")
            val samples = readWavAsFloatMono16k(audioFile)

            onProgress("Transcribing on-device (this can take a while on older phones)…")
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val rc = WhisperLib.fullTranscribe(ctx, threads, samples)
            check(rc == 0) { "whisper_full failed (code $rc)" }

            val count = WhisperLib.getSegmentCount(ctx)
            (0 until count).map { i ->
                TranscriptSegment(
                    startSec = WhisperLib.getSegmentT0(ctx, i) / 100.0,
                    endSec = WhisperLib.getSegmentT1(ctx, i) / 100.0,
                    text = WhisperLib.getSegmentText(ctx, i),
                )
            }
        } finally {
            WhisperLib.freeContext(ctx)
        }
    }

    /** Minimal PCM16 mono WAV reader -> normalized Float32 samples (whisper.cpp's expected input). */
    private fun readWavAsFloatMono16k(wav: File): FloatArray {
        RandomAccessFile(wav, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            val dataSize = (raf.length() - 44).toInt()
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val samples = FloatArray(dataSize / 2)
            for (i in samples.indices) {
                samples[i] = buf.short / 32768.0f
            }
            return samples
        }
    }
}
