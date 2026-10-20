package dev.clipforge.app

import android.app.Application
import dev.clipforge.app.data.jobs.JobRepository
import dev.clipforge.app.data.templates.TemplateRepository
import dev.clipforge.app.data.video.VideoSourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipForgeApp : Application() {

    val appScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // yt-dlp's Python runtime + binary need a one-time init; do it off the main thread
        // so a cold launch isn't blocked on it even if the user never pastes a URL.
        appScope.launch { VideoSourceManager.ensureYoutubeDlReady(this@ClipForgeApp) }
        appScope.launch { TemplateRepository.get(this@ClipForgeApp).load() }
        appScope.launch { JobRepository.get(this@ClipForgeApp).load() }
    }
}
