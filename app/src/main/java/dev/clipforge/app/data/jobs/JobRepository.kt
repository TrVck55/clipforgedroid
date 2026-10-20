package dev.clipforge.app.data.jobs

import android.content.Context
import dev.clipforge.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lightweight JSON-file-backed job history (no Room/SQLite dependency needed
 * for a list that will realistically hold dozens-to-hundreds of entries).
 * ClipJobWorker writes progress here; the UI observes [jobs] reactively.
 */
class JobRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "jobs.json")
    private val mutex = Mutex()

    private val _jobs = MutableStateFlow<List<ClipJob>>(emptyList())
    val jobs: StateFlow<List<ClipJob>> = _jobs.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _jobs.value = if (file.exists()) parse(file.readText()) else emptyList()
        }
    }

    suspend fun upsert(job: ClipJob) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _jobs.value.filter { it.id != job.id } + job
            _jobs.value = next.sortedByDescending { it.createdAt }
            file.writeText(serialize(_jobs.value))
        }
    }

    fun getSync(id: String): ClipJob? = _jobs.value.firstOrNull { it.id == id }

    private fun serialize(jobs: List<ClipJob>): String {
        val arr = JSONArray()
        jobs.forEach { j ->
            arr.put(JSONObject().apply {
                put("id", j.id)
                put("createdAt", j.createdAt)
                put("sourceLabel", j.sourceLabel)
                put("templateId", j.templateId)
                put("templateName", j.templateName)
                put("providerChoice", j.providerChoice.name)
                put("transcriptionMode", j.transcriptionMode.name)
                put("polish", j.polish.name)
                put("status", j.status.name)
                put("progressMessage", j.progressMessage)
                put("errorMessage", j.errorMessage)
                put("clips", JSONArray().apply {
                    j.clips.forEach { c ->
                        put(JSONObject().apply {
                            put("filePath", c.filePath)
                            put("polish", c.polish.name)
                            put("startSec", c.suggestion.startSec)
                            put("endSec", c.suggestion.endSec)
                            put("hookTitle", c.suggestion.hookTitle)
                            put("viralityScore", c.suggestion.viralityScore)
                            put("reasoning", c.suggestion.reasoning)
                            put("caption", c.suggestion.suggestedCaption)
                            put("hashtags", JSONArray(c.suggestion.suggestedHashtags))
                            put("sourceProvider", c.suggestion.sourceProvider)
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    private fun parse(text: String): List<ClipJob> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val clipsArr = o.optJSONArray("clips") ?: JSONArray()
            val clips = (0 until clipsArr.length()).map { ci ->
                val c = clipsArr.getJSONObject(ci)
                val tags = c.optJSONArray("hashtags")
                val tagList = (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) }
                RenderedClip(
                    suggestion = ClipSuggestion(
                        startSec = c.getDouble("startSec"),
                        endSec = c.getDouble("endSec"),
                        hookTitle = c.getString("hookTitle"),
                        viralityScore = c.getInt("viralityScore"),
                        reasoning = c.optString("reasoning", ""),
                        suggestedCaption = c.optString("caption", ""),
                        suggestedHashtags = tagList,
                        sourceProvider = c.optString("sourceProvider", ""),
                    ),
                    filePath = c.getString("filePath"),
                    polish = OutputPolish.valueOf(c.getString("polish")),
                )
            }
            ClipJob(
                id = o.getString("id"),
                createdAt = o.getLong("createdAt"),
                sourceLabel = o.getString("sourceLabel"),
                templateId = o.getString("templateId"),
                templateName = o.getString("templateName"),
                providerChoice = AiProviderChoice.valueOf(o.getString("providerChoice")),
                transcriptionMode = TranscriptionMode.valueOf(o.getString("transcriptionMode")),
                polish = OutputPolish.valueOf(o.getString("polish")),
                status = JobStatus.valueOf(o.getString("status")),
                progressMessage = o.optString("progressMessage", ""),
                errorMessage = if (o.isNull("errorMessage")) null else o.optString("errorMessage"),
                clips = clips,
            )
        }
    }

    companion object {
        @Volatile private var instance: JobRepository? = null
        fun get(context: Context): JobRepository =
            instance ?: synchronized(this) {
                instance ?: JobRepository(context.applicationContext).also { instance = it }
            }
    }
}
