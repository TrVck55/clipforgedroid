package dev.clipforge.app.model

/** Which LLM(s) should analyze the transcript for clip-worthy moments. */
enum class AiProviderChoice { CLAUDE, OPENAI, BOTH }

enum class TranscriptionMode { ON_DEVICE, CLOUD }

/** How much post-processing a rendered clip gets. */
enum class OutputPolish {
    RAW_CUT,                 // fast stream-copy trim, no re-encode
    VERTICAL_CROP,           // re-encoded, cropped/scaled to 9:16
    VERTICAL_CROP_CAPTIONS   // vertical crop + burned-in captions
}

enum class JobStatus {
    QUEUED, FETCHING_SOURCE, EXTRACTING_AUDIO, TRANSCRIBING,
    ANALYZING, RENDERING, DONE, FAILED
}

data class TranscriptSegment(
    val startSec: Double,
    val endSec: Double,
    val text: String,
)

data class ClipTemplate(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = true,
)

/** One clip-worthy moment an AI provider found in the transcript. */
data class ClipSuggestion(
    val startSec: Double,
    val endSec: Double,
    val hookTitle: String,
    val viralityScore: Int,       // 0-100, model's own estimate
    val reasoning: String,
    val suggestedCaption: String,
    val suggestedHashtags: List<String>,
    val sourceProvider: String,   // "claude" | "openai" — useful when AiProviderChoice.BOTH is used
)

data class RenderedClip(
    val suggestion: ClipSuggestion,
    val filePath: String,
    val polish: OutputPolish,
)

data class ClipJob(
    val id: String,
    val createdAt: Long,
    val sourceLabel: String,
    val templateId: String,
    val templateName: String,
    val providerChoice: AiProviderChoice,
    val transcriptionMode: TranscriptionMode,
    val polish: OutputPolish,
    val status: JobStatus = JobStatus.QUEUED,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val clips: List<RenderedClip> = emptyList(),
)

/** Where the source video comes from for a given job. */
sealed class VideoSourceInput {
    data class LocalFile(val uriString: String) : VideoSourceInput()
    data class RemoteUrl(val url: String) : VideoSourceInput()
}
