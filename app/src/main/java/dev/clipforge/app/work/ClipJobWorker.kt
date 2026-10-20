package dev.clipforge.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.clipforge.app.R
import dev.clipforge.app.data.ai.AIProvider
import dev.clipforge.app.data.ai.ClaudeProvider
import dev.clipforge.app.data.ai.OpenAIProvider
import dev.clipforge.app.data.ai.mergeAndDedupe
import dev.clipforge.app.data.jobs.JobRepository
import dev.clipforge.app.data.render.ClipRenderer
import dev.clipforge.app.data.settings.SettingsRepository
import dev.clipforge.app.data.templates.TemplateRepository
import dev.clipforge.app.data.transcribe.CloudWhisperEngine
import dev.clipforge.app.data.transcribe.OnDeviceWhisperEngine
import dev.clipforge.app.data.transcribe.TranscriptionEngine
import dev.clipforge.app.data.video.VideoSourceManager
import dev.clipforge.app.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * End-to-end pipeline for one clip job: fetch source -> extract audio ->
 * transcribe -> ask the chosen AI provider(s) for clip-worthy moments ->
 * render each one with ffmpeg -> persist results. Runs as expedited/foreground
 * work since this routinely takes minutes on real video.
 */
class ClipJobWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val jobRepo = JobRepository.get(appContext)
    private val templateRepo = TemplateRepository.get(appContext)
    private val settingsRepo = SettingsRepository.get(appContext)
    private val renderer = ClipRenderer()
    private val sourceManager = VideoSourceManager(appContext)

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Starting clip job…")

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val sourceType = inputData.getString(KEY_SOURCE_TYPE)
        val sourceValue = inputData.getString(KEY_SOURCE_VALUE) ?: ""
        val templateId = inputData.getString(KEY_TEMPLATE_ID) ?: ""
        val providerChoice = AiProviderChoice.valueOf(inputData.getString(KEY_PROVIDER) ?: AiProviderChoice.CLAUDE.name)
        val transcriptionMode = TranscriptionMode.valueOf(inputData.getString(KEY_TRANSCRIPTION) ?: TranscriptionMode.CLOUD.name)
        val polish = OutputPolish.valueOf(inputData.getString(KEY_POLISH) ?: OutputPolish.VERTICAL_CROP.name)

        setForeground(buildForegroundInfo("Starting…"))

        // Progress callbacks from ffmpeg-kit / youtubedl-android arrive on their own
        // background threads via plain (non-suspend) Java-style callbacks, so this is
        // intentionally a blocking call rather than suspend — it's never called from
        // the main thread, and jobRepo.upsert is just a fast local JSON write.
        fun updateStatus(status: JobStatus, message: String, clips: List<RenderedClip> = emptyList(), error: String? = null) {
            val current = jobRepo.getSync(jobId)
            val updated = (current ?: return).copy(
                status = status, progressMessage = message,
                clips = clips.ifEmpty { current.clips }, errorMessage = error,
            )
            kotlinx.coroutines.runBlocking { jobRepo.upsert(updated) }
        }

        return try {
            templateRepo.load()
            val template = templateRepo.templates.value.firstOrNull { it.id == templateId }
                ?: error("Template '$templateId' not found")

            // 1) Source
            updateStatus(JobStatus.FETCHING_SOURCE, "Getting source video…")
            val sourceFile = when (sourceType) {
                "local" -> sourceManager.importLocalFile(Uri.parse(sourceValue))
                "url" -> sourceManager.downloadFromUrl(sourceValue) { pct, eta ->
                    updateStatus(JobStatus.FETCHING_SOURCE, "Downloading… ${pct.toInt()}% ($eta)")
                }
                else -> error("Unknown source type: $sourceType")
            }
            val durationSec = renderer.probeDurationSeconds(sourceFile)

            // 2) Transcribe
            updateStatus(JobStatus.EXTRACTING_AUDIO, "Extracting audio…")
            val audioDir = File(applicationContext.cacheDir, "audio_$jobId")
            val audioFile = renderer.extractAudioForTranscription(sourceFile, audioDir)

            updateStatus(JobStatus.TRANSCRIBING, "Transcribing (${transcriptionMode.name.lowercase()})…")
            val settings = settingsRepo.settings.value
            val engine: TranscriptionEngine = when (transcriptionMode) {
                TranscriptionMode.CLOUD -> CloudWhisperEngine(settings.openAiApiKey, settings.openAiTranscribeModel)
                TranscriptionMode.ON_DEVICE -> OnDeviceWhisperEngine(
                    File(File(applicationContext.filesDir, "whisper"), "model.bin")
                )
            }
            val transcript = engine.transcribe(audioFile) { msg -> updateStatus(JobStatus.TRANSCRIBING, msg) }
            check(transcript.isNotEmpty()) { "Transcription returned no segments — nothing to analyze." }

            // 3) Ask the AI(s) which moments are clip-worthy
            updateStatus(JobStatus.ANALYZING, "Asking AI for clip-worthy moments…")
            val providers: List<Pair<AIProvider, Pair<String, String>>> = buildList {
                if (providerChoice == AiProviderChoice.CLAUDE || providerChoice == AiProviderChoice.BOTH)
                    add(ClaudeProvider() to (settings.claudeModel to settings.claudeApiKey))
                if (providerChoice == AiProviderChoice.OPENAI || providerChoice == AiProviderChoice.BOTH)
                    add(OpenAIProvider() to (settings.openAiModel to settings.openAiApiKey))
            }
            val allSuggestions = coroutineScope {
                providers.map { (provider, modelAndKey) ->
                    async { provider.suggestClips(transcript, template, durationSec, modelAndKey.first, modelAndKey.second) }
                }.awaitAll()
            }.flatten()
            val suggestions = mergeAndDedupe(allSuggestions)
            check(suggestions.isNotEmpty()) { "The AI didn't return any clip suggestions." }

            // 4) Render
            updateStatus(JobStatus.RENDERING, "Rendering ${suggestions.size} clip(s)…")
            val outputDir = File(applicationContext.getExternalFilesDir("ClipForge"), jobId).apply { mkdirs() }
            val rendered = suggestions.mapIndexed { idx, s ->
                updateStatus(JobStatus.RENDERING, "Rendering clip ${idx + 1}/${suggestions.size}: ${s.hookTitle}")
                renderer.renderClip(sourceFile, s, polish, transcript, outputDir, idx)
            }

            updateStatus(JobStatus.DONE, "Done — ${rendered.size} clip(s) ready.", clips = rendered)
            Result.success()
        } catch (t: Throwable) {
            updateStatus(JobStatus.FAILED, "Failed", error = t.message ?: t.toString())
            Result.failure(workDataOf("error" to (t.message ?: t.toString())))
        }
    }

    private fun buildForegroundInfo(message: String): ForegroundInfo {
        val channelId = "clipforge_jobs"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Clip jobs", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("ClipForge AI")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        val id = 42
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_SOURCE_TYPE = "source_type" // "local" | "url"
        const val KEY_SOURCE_VALUE = "source_value"
        const val KEY_TEMPLATE_ID = "template_id"
        const val KEY_PROVIDER = "provider"
        const val KEY_TRANSCRIPTION = "transcription"
        const val KEY_POLISH = "polish"
    }
}
