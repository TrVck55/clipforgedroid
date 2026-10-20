package dev.clipforge.app.data.render

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import dev.clipforge.app.model.ClipSuggestion
import dev.clipforge.app.model.OutputPolish
import dev.clipforge.app.model.RenderedClip
import dev.clipforge.app.model.TranscriptSegment
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * All video/audio manipulation goes through ffmpeg-kit here. Every ffmpeg
 * command is run via FFmpegKit.executeAsync (non-blocking, as recommended
 * for anything beyond trivial clips) wrapped into a suspend function.
 */
class ClipRenderer {

    suspend fun probeDurationSeconds(file: File): Double = suspendCancellableCoroutine { cont ->
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        val durationStr = session.mediaInformation?.duration
        cont.resume(durationStr?.toDoubleOrNull() ?: 0.0)
    }

    /** Downmixes/resamples to 16kHz mono WAV — the format both transcription engines expect. */
    suspend fun extractAudioForTranscription(source: File, outputDir: File): File {
        outputDir.mkdirs()
        val out = File(outputDir, "${source.nameWithoutExtension}_16k.wav")
        val cmd = "-y -i \"${source.absolutePath}\" -vn -ac 1 -ar 16000 -f wav \"${out.absolutePath}\""
        runFfmpeg(cmd)
        return out
    }

    suspend fun renderClip(
        source: File,
        suggestion: ClipSuggestion,
        polish: OutputPolish,
        transcript: List<TranscriptSegment>,
        outputDir: File,
        clipIndex: Int,
    ): RenderedClip {
        outputDir.mkdirs()
        val start = suggestion.startSec
        val duration = (suggestion.endSec - suggestion.startSec).coerceAtLeast(1.0)
        val outFile = File(outputDir, "clip_${clipIndex}_${polish.name.lowercase()}.mp4")

        val cmd = when (polish) {
            OutputPolish.RAW_CUT -> buildRawCutCommand(source, start, duration, outFile)
            OutputPolish.VERTICAL_CROP -> buildVerticalCropCommand(source, start, duration, outFile, srt = null)
            OutputPolish.VERTICAL_CROP_CAPTIONS -> {
                val srt = writeSrtForRange(transcript, start, suggestion.endSec, outputDir, clipIndex)
                buildVerticalCropCommand(source, start, duration, outFile, srt = srt)
            }
        }

        runFfmpeg(cmd)
        check(outFile.exists() && outFile.length() > 0) { "ffmpeg produced no output for clip $clipIndex" }
        return RenderedClip(suggestion = suggestion, filePath = outFile.absolutePath, polish = polish)
    }

    // -- Command builders -----------------------------------------------

    /** Fast trim via stream copy. Seeks snap to the nearest keyframe, so edges can be
     * off by up to ~1-2s on long-GOP sources — trade speed for frame accuracy. */
    private fun buildRawCutCommand(source: File, start: Double, duration: Double, out: File): String =
        "-y -ss ${fmt(start)} -i \"${source.absolutePath}\" -t ${fmt(duration)} " +
            "-c copy -avoid_negative_ts make_zero \"${out.absolutePath}\""

    /** Re-encodes: crops the tallest centered 9:16 window, scales to 1080x1920,
     * optionally burns in an SRT built from the matching transcript range. */
    private fun buildVerticalCropCommand(
        source: File, start: Double, duration: Double, out: File, srt: File?
    ): String {
        val cropScale = "crop=ih*9/16:ih,scale=1080:1920:force_original_aspect_ratio=decrease," +
            "pad=1080:1920:(ow-iw)/2:(oh-ih)/2"
        val vf = if (srt != null) {
            val escaped = srt.absolutePath.replace("'", "\\'").replace(":", "\\:")
            "$cropScale,subtitles='$escaped':force_style='Fontsize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H80000000,BorderStyle=3,Outline=2,Alignment=2,MarginV=80'"
        } else cropScale

        return "-y -ss ${fmt(start)} -i \"${source.absolutePath}\" -t ${fmt(duration)} " +
            "-vf \"$vf\" -c:v h264_mediacodec -b:v 6M -c:a aac -b:a 128k " +
            "\"${out.absolutePath}\""
    }

    private fun writeSrtForRange(
        transcript: List<TranscriptSegment>, clipStart: Double, clipEnd: Double,
        outputDir: File, clipIndex: Int,
    ): File {
        val relevant = transcript.filter { it.endSec > clipStart && it.startSec < clipEnd }
        val srt = File(outputDir, "clip_${clipIndex}.srt")
        srt.bufferedWriter().use { w ->
            relevant.forEachIndexed { i, seg ->
                val relStart = max(0.0, seg.startSec - clipStart)
                val relEnd = min(clipEnd - clipStart, seg.endSec - clipStart)
                if (relEnd <= relStart) return@forEachIndexed
                w.write("${i + 1}\n")
                w.write("${srtTime(relStart)} --> ${srtTime(relEnd)}\n")
                w.write("${seg.text.trim()}\n\n")
            }
        }
        return srt
    }

    private fun srtTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val h = totalMs / 3_600_000
        val m = (totalMs % 3_600_000) / 60_000
        val s = (totalMs % 60_000) / 1000
        val ms = totalMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }

    private fun fmt(seconds: Double) = String.format(Locale.US, "%.3f", seconds)

    private suspend fun runFfmpeg(command: String): Unit = suspendCancellableCoroutine { cont ->
        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                if (cont.isActive) cont.resume(Unit)
            } else {
                val log = session.allLogsAsString
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("ffmpeg failed (${session.returnCode}): ${log.takeLast(800)}")
                    )
                }
            }
        }
    }
}
