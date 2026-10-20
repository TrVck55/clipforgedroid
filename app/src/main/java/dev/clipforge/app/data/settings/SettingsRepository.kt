package dev.clipforge.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.clipforge.app.model.AiProviderChoice
import dev.clipforge.app.model.OutputPolish
import dev.clipforge.app.model.TranscriptionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val claudeApiKey: String = "",
    val openAiApiKey: String = "",
    val claudeModel: String = "claude-sonnet-5-5",
    val openAiModel: String = "gpt-4o-mini",
    val openAiTranscribeModel: String = "whisper-1",
    val defaultProvider: AiProviderChoice = AiProviderChoice.CLAUDE,
    val defaultTranscription: TranscriptionMode = TranscriptionMode.CLOUD,
    val defaultPolish: OutputPolish = OutputPolish.VERTICAL_CROP,
)

/**
 * API keys and user settings, stored in an EncryptedSharedPreferences file
 * (AES256-GCM, key in the Android Keystore). Keys never leave the device
 * except in the direct HTTPS request to the chosen provider.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "clipforge_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load() = AppSettings(
        claudeApiKey = prefs.getString(KEY_CLAUDE_API_KEY, "") ?: "",
        openAiApiKey = prefs.getString(KEY_OPENAI_API_KEY, "") ?: "",
        claudeModel = prefs.getString(KEY_CLAUDE_MODEL, "claude-sonnet-5-5") ?: "claude-sonnet-5-5",
        openAiModel = prefs.getString(KEY_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini",
        openAiTranscribeModel = prefs.getString(KEY_OPENAI_TRANSCRIBE_MODEL, "whisper-1") ?: "whisper-1",
        defaultProvider = AiProviderChoice.valueOf(
            prefs.getString(KEY_DEFAULT_PROVIDER, AiProviderChoice.CLAUDE.name) ?: AiProviderChoice.CLAUDE.name
        ),
        defaultTranscription = TranscriptionMode.valueOf(
            prefs.getString(KEY_DEFAULT_TRANSCRIPTION, TranscriptionMode.CLOUD.name) ?: TranscriptionMode.CLOUD.name
        ),
        defaultPolish = OutputPolish.valueOf(
            prefs.getString(KEY_DEFAULT_POLISH, OutputPolish.VERTICAL_CROP.name) ?: OutputPolish.VERTICAL_CROP.name
        ),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_settings.value)
        prefs.edit()
            .putString(KEY_CLAUDE_API_KEY, next.claudeApiKey)
            .putString(KEY_OPENAI_API_KEY, next.openAiApiKey)
            .putString(KEY_CLAUDE_MODEL, next.claudeModel)
            .putString(KEY_OPENAI_MODEL, next.openAiModel)
            .putString(KEY_OPENAI_TRANSCRIBE_MODEL, next.openAiTranscribeModel)
            .putString(KEY_DEFAULT_PROVIDER, next.defaultProvider.name)
            .putString(KEY_DEFAULT_TRANSCRIPTION, next.defaultTranscription.name)
            .putString(KEY_DEFAULT_POLISH, next.defaultPolish.name)
            .apply()
        _settings.value = next
    }

    companion object {
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_CLAUDE_MODEL = "claude_model"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_OPENAI_TRANSCRIBE_MODEL = "openai_transcribe_model"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DEFAULT_TRANSCRIPTION = "default_transcription"
        private const val KEY_DEFAULT_POLISH = "default_polish"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
