package dev.clipforge.app.data.transcribe

import dev.clipforge.app.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uses OpenAI's /v1/audio/transcriptions endpoint (Whisper) with
 * response_format=verbose_json to get per-segment timestamps directly —
 * no local ASR model or NDK build required. Costs ~$0.006/min on OpenAI's
 * pricing as of this writing; check current pricing in Settings before a
 * big batch job.
 */
class CloudWhisperEngine(
    private val apiKey: String,
    private val model: String = "whisper-1",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // long audio files take a while to upload+process
        .build(),
) : TranscriptionEngine {

    override suspend fun transcribe(
        audioFile: File,
        onProgress: (String) -> Unit,
    ): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "OpenAI API key is not set (Settings -> AI Providers)." }
        onProgress("Uploading audio to OpenAI Whisper…")

        val fileBody = audioFile.asRequestBody("audio/wav".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, fileBody)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Whisper API error ${resp.code}: $raw")
            }
            onProgress("Parsing transcript…")
            val json = JSONObject(raw)
            val segments = json.optJSONArray("segments")
            val out = mutableListOf<TranscriptSegment>()
            if (segments != null) {
                for (i in 0 until segments.length()) {
                    val s = segments.getJSONObject(i)
                    out.add(
                        TranscriptSegment(
                            startSec = s.getDouble("start"),
                            endSec = s.getDouble("end"),
                            text = s.getString("text"),
                        )
                    )
                }
            } else {
                // Fallback: no segment granularity returned, treat whole thing as one block.
                out.add(TranscriptSegment(0.0, 0.0, json.optString("text", "")))
            }
            out
        }
    }
}
