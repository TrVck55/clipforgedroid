package dev.clipforge.app.data.transcribe

/**
 * Thin JNI boundary over whisper.cpp. Safe to reference even when the
 * native library isn't built — [isAvailable] just comes back false and
 * every other call throws, so callers must check it first.
 *
 * Building the native side requires the whisper.cpp submodule; see
 * README -> "Enabling on-device transcription".
 */
object WhisperLib {

    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("clipforge_whisper")
            true
        } catch (t: Throwable) {
            false
        }
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)

    /** Runs full transcription; audio must be 16kHz mono float32 PCM in [-1, 1]. Returns 0 on success. */
    external fun fullTranscribe(contextPtr: Long, numThreads: Int, audio: FloatArray): Int

    external fun getSegmentCount(contextPtr: Long): Int
    external fun getSegmentText(contextPtr: Long, index: Int): String
    external fun getSegmentT0(contextPtr: Long, index: Int): Long // centiseconds (x10ms), whisper.cpp convention
    external fun getSegmentT1(contextPtr: Long, index: Int): Long
}
