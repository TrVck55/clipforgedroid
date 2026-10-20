package dev.clipforge.app.data.ai

import dev.clipforge.app.model.ClipSuggestion
import dev.clipforge.app.model.ClipTemplate
import dev.clipforge.app.model.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject

interface AIProvider {
    val id: String // "claude" | "openai"

    suspend fun suggestClips(
        transcript: List<TranscriptSegment>,
        template: ClipTemplate,
        sourceDurationSec: Double,
        model: String,
        apiKey: String,
    ): List<ClipSuggestion>
}

/**
 * Shared prompt construction + response parsing so Claude and OpenAI stay
 * in lockstep on the exact schema they're asked to return.
 */
object PromptBuilder {

    const val JSON_INSTRUCTIONS = """
Return ONLY valid JSON (no markdown fences, no commentary) matching exactly this shape:
{
  "clips": [
    {
      "start": <seconds, float>,
      "end": <seconds, float>,
      "hook_title": <short punchy title string>,
      "virality_score": <integer 0-100>,
      "reasoning": <one sentence: why this moment, specifically>,
      "caption": <a ready-to-post social caption, 1-2 sentences>,
      "hashtags": [<3-6 short hashtag strings, no # symbol>]
    }
  ]
}
Pick between 3 and 8 non-overlapping clips. "start" and "end" MUST be real timestamps
that appear in (or between) the transcript lines provided — never invent timestamps
outside the transcript's range. Order clips by virality_score descending.
"""

    fun userPrompt(
        transcript: List<TranscriptSegment>,
        template: ClipTemplate,
        sourceDurationSec: Double,
    ): String {
        val sb = StringBuilder()
        sb.append("Video duration: ${"%.1f".format(sourceDurationSec)}s\n")
        sb.append("Template: ${template.name} — ${template.description}\n\n")
        sb.append("Timestamped transcript:\n")
        for (seg in transcript) {
            sb.append("[${"%.1f".format(seg.startSec)}-${"%.1f".format(seg.endSec)}] ${seg.text.trim()}\n")
        }
        sb.append("\n").append(JSON_INSTRUCTIONS)
        return sb.toString()
    }

    /** Parses the shared {"clips":[...]} schema out of a raw model response string. */
    fun parseClips(rawResponseText: String, providerId: String): List<ClipSuggestion> {
        val cleaned = rawResponseText.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = JSONObject(cleaned)
        val arr: JSONArray = root.getJSONArray("clips")
        val out = mutableListOf<ClipSuggestion>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = mutableListOf<String>()
            o.optJSONArray("hashtags")?.let { ta ->
                for (j in 0 until ta.length()) tags.add(ta.getString(j).removePrefix("#"))
            }
            out.add(
                ClipSuggestion(
                    startSec = o.getDouble("start"),
                    endSec = o.getDouble("end"),
                    hookTitle = o.optString("hook_title", "Untitled clip"),
                    viralityScore = o.optInt("virality_score", 50),
                    reasoning = o.optString("reasoning", ""),
                    suggestedCaption = o.optString("caption", ""),
                    suggestedHashtags = tags,
                    sourceProvider = providerId,
                )
            )
        }
        return out
    }
}

/**
 * Merges results from multiple providers (AiProviderChoice.BOTH): drops
 * suggestions that heavily overlap an already-kept, higher-scoring one from
 * another provider, so you don't get near-duplicate clips in the output.
 */
fun mergeAndDedupe(all: List<ClipSuggestion>): List<ClipSuggestion> {
    val sorted = all.sortedByDescending { it.viralityScore }
    val kept = mutableListOf<ClipSuggestion>()
    outer@ for (candidate in sorted) {
        for (existing in kept) {
            val overlapStart = maxOf(candidate.startSec, existing.startSec)
            val overlapEnd = minOf(candidate.endSec, existing.endSec)
            val overlap = (overlapEnd - overlapStart).coerceAtLeast(0.0)
            val shorter = minOf(candidate.endSec - candidate.startSec, existing.endSec - existing.startSec)
            if (shorter > 0 && overlap / shorter > 0.5) continue@outer // >50% overlap with a better clip: skip
        }
        kept.add(candidate)
    }
    return kept
}
