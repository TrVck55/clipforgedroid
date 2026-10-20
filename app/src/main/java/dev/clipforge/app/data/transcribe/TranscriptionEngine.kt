package dev.clipforge.app.data.transcribe

import dev.clipforge.app.model.TranscriptSegment
import java.io.File

interface TranscriptionEngine {
    /** audioFile should already be 16kHz mono WAV (see ClipRenderer.extractAudioForTranscription). */
    suspend fun transcribe(audioFile: File, onProgress: (String) -> Unit = {}): List<TranscriptSegment>
}
