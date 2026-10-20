package dev.clipforge.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.clipforge.app.data.settings.SettingsRepository
import dev.clipforge.app.data.transcribe.WhisperLib
import dev.clipforge.app.model.AiProviderChoice
import dev.clipforge.app.model.OutputPolish
import dev.clipforge.app.model.TranscriptionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.get(context) }
    val settings by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    val modelFile = remember { File(File(context.filesDir, "whisper"), "model.bin") }
    var modelImported by remember { mutableStateOf(modelFile.exists()) }

    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                modelFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    modelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            modelImported = modelFile.exists()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionTitle("AI providers")
            Text(
                "Keys are stored on-device only, encrypted via the Android Keystore. " +
                    "They're sent directly to Anthropic/OpenAI over HTTPS — never anywhere else.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = settings.claudeApiKey,
                onValueChange = { v -> settingsRepo.update { it.copy(claudeApiKey = v) } },
                label = { Text("Claude API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.claudeModel,
                onValueChange = { v -> settingsRepo.update { it.copy(claudeModel = v) } },
                label = { Text("Claude model") },
                supportingText = { Text("Check Anthropic's docs for the current model id if this one is stale.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.openAiApiKey,
                onValueChange = { v -> settingsRepo.update { it.copy(openAiApiKey = v) } },
                label = { Text("OpenAI API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.openAiModel,
                onValueChange = { v -> settingsRepo.update { it.copy(openAiModel = v) } },
                label = { Text("OpenAI model (for clip analysis)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Divider()
            SectionTitle("Default: which AI to use")
            SingleChoiceRow(
                options = AiProviderChoice.entries,
                selected = settings.defaultProvider,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { v -> settingsRepo.update { it.copy(defaultProvider = v) } },
            )

            Divider()
            SectionTitle("Transcription")
            SingleChoiceRow(
                options = TranscriptionMode.entries,
                selected = settings.defaultTranscription,
                label = { if (it == TranscriptionMode.ON_DEVICE) "On-device" else "Cloud (Whisper API)" },
                onSelect = { v -> settingsRepo.update { it.copy(defaultTranscription = v) } },
            )
            OutlinedTextField(
                value = settings.openAiTranscribeModel,
                onValueChange = { v -> settingsRepo.update { it.copy(openAiTranscribeModel = v) } },
                label = { Text("Cloud transcription model") },
                modifier = Modifier.fillMaxWidth(),
            )
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("On-device engine: ${if (WhisperLib.isAvailable) "built into this APK" else "NOT built (see README)"}")
                    Text("Model file: ${if (modelImported) "imported ✓" else "none imported"}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { importModelLauncher.launch(arrayOf("*/*")) }) {
                        Text("Import a ggml Whisper model (.bin)")
                    }
                }
            }

            Divider()
            SectionTitle("Default clip polish")
            SingleChoiceRow(
                options = OutputPolish.entries,
                selected = settings.defaultPolish,
                label = {
                    when (it) {
                        OutputPolish.RAW_CUT -> "Raw cut"
                        OutputPolish.VERTICAL_CROP -> "Vertical crop"
                        OutputPolish.VERTICAL_CROP_CAPTIONS -> "Vertical + captions"
                    }
                },
                onSelect = { v -> settingsRepo.update { it.copy(defaultPolish = v) } },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun <T> SingleChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(label(opt)) })
        }
    }
}
