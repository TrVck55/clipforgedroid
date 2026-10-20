package dev.clipforge.app.data.ai

import dev.clipforge.app.model.ClipSuggestion
import dev.clipforge.app.model.ClipTemplate
import dev.clipforge.app.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAIProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val id = "openai"

    override suspend fun suggestClips(
        transcript: List<TranscriptSegment>,
        template: ClipTemplate,
        sourceDurationSec: Double,
        model: String,
        apiKey: String,
    ): List<ClipSuggestion> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OpenAI API key is not set (Settings -> AI Providers)." }

        val userPrompt = PromptBuilder.userPrompt(transcript, template, sourceDurationSec)
        val systemPrompt = "${template.systemPrompt}\n\nYou always answer with the exact JSON schema you are given, nothing else. Respond with a single JSON object."

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OpenAI API error ${resp.code}: $raw")
            }
            val json = JSONObject(raw)
            val text = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            PromptBuilder.parseClips(text, id)
        }
    }
}
