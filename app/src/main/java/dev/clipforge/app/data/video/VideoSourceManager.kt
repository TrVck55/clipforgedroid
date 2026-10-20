package dev.clipforge.app.data.video

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Gets a local, playable source video file onto disk regardless of whether
 * the user picked a file from their device or pasted a URL.
 *
 * IMPORTANT: only download content you own or have the rights to clip.
 * Downloading from some platforms may violate their Terms of Service —
 * that's on the person running this app, not on the library or this code.
 */
class VideoSourceManager(private val context: Context) {

    private val workDir: File
        get() = File(context.cacheDir, "sources").apply { mkdirs() }

    suspend fun importLocalFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val out = File(workDir, "${UUID.randomUUID()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open the selected file.")
        out
    }

    suspend fun downloadFromUrl(
        url: String,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val outTemplate = File(workDir, "${UUID.randomUUID()}.%(ext)s")
        val request = YoutubeDLRequest(url).apply {
            addOption("-f", "bv*[height<=1080]+ba/b[height<=1080]")
            addOption("--merge-output-format", "mp4")
            addOption("-o", outTemplate.absolutePath)
            addOption("--no-playlist")
        }
        YoutubeDL.getInstance().execute(request, null) { progress, etaSeconds ->
            onProgress(progress, "ETA ${etaSeconds}s")
        }
        // yt-dlp resolves %(ext)s itself; find whatever file actually landed.
        val produced = workDir.listFiles { f ->
            f.nameWithoutExtension == outTemplate.nameWithoutExtension
        }?.firstOrNull()
        produced ?: error("Download finished but no output file was found for: $url")
    }

    companion object {
        /** Call once, early (e.g. Application.onCreate on a background thread). */
        suspend fun ensureYoutubeDlReady(context: Context) = withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
        }
    }
}
