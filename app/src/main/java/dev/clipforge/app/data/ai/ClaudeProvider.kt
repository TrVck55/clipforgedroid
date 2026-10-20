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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val id = "claude"

    override suspend fun suggestClips(
        transcript: List<TranscriptSegment>,
        template: ClipTemplate,
        sourceDurationSec: Double,
        model: String,
        apiKey: String,
    ): List<ClipSuggestion> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Claude API key is not set (Settings -> AI Providers)." }

        val userPrompt = PromptBuilder.userPrompt(transcript, template, sourceDurationSec)
        val systemPrompt = "${template.systemPrompt}\n\nYou always answer with the exact JSON schema you are given, nothing else."

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("system", systemPrompt)
            put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", userPrompt)
            ))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Claude API error ${resp.code}: $raw")
            }
            val json = JSONObject(raw)
            val contentArr = json.getJSONArray("content")
            // Find the first text block (Claude can interleave block types).
            var text = ""
            for (i in 0 until contentArr.length()) {
                val block = contentArr.getJSONObject(i)
                if (block.optString("type") == "text") {
                    text = block.getString("text")
                    break
                }
            }
            PromptBuilder.parseClips(text, id)
        }
    }
}
