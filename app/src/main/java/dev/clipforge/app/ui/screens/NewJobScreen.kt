package dev.clipforge.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import dev.clipforge.app.data.jobs.JobRepository
import dev.clipforge.app.data.settings.SettingsRepository
import dev.clipforge.app.data.templates.TemplateRepository
import dev.clipforge.app.model.*
import dev.clipforge.app.work.ClipJobWorker
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJobScreen(onJobCreated: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val templateRepo = remember { TemplateRepository.get(context) }
    val settingsRepo = remember { SettingsRepository.get(context) }
    val jobRepo = remember { JobRepository.get(context) }
    val templates by templateRepo.templates.collectAsState()
    val settings by settingsRepo.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var sourceTab by remember { mutableStateOf(0) } // 0 = upload, 1 = url
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pastedUrl by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var providerChoice by remember { mutableStateOf(settings.defaultProvider) }
    var transcriptionMode by remember { mutableStateOf(settings.defaultTranscription) }
    var polish by remember { mutableStateOf(settings.defaultPolish) }
    var showCustomTemplateDialog by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedUri = uri
    }

    val canSubmit = (sourceTab == 0 && pickedUri != null || sourceTab == 1 && pastedUrl.isNotBlank()) &&
        selectedTemplateId != null

    Scaffold(topBar = {
        TopAppBar(title = { Text("New clip job") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("1. Source video", style = MaterialTheme.typography.titleMedium)
            TabRow(selectedTabIndex = sourceTab) {
                Tab(selected = sourceTab == 0, onClick = { sourceTab = 0 }, text = { Text("Upload file") })
                Tab(selected = sourceTab == 1, onClick = { sourceTab = 1 }, text = { Text("Paste URL") })
            }
            if (sourceTab == 0) {
                OutlinedButton(onClick = { pickFileLauncher.launch(arrayOf("video/*")) }) {
                    Text(if (pickedUri == null) "Choose a video" else "Change video")
                }
                pickedUri?.let { Text(it.lastPathSegment ?: it.toString(), style = MaterialTheme.typography.bodySmall) }
            } else {
                OutlinedTextField(
                    value = pastedUrl,
                    onValueChange = { pastedUrl = it },
                    label = { Text("Video URL (YouTube, Twitch VOD, TikTok, …)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Only download content you own or have the rights to clip — some platforms' " +
                        "Terms of Service restrict downloading. That call is yours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Divider()
            Text("2. Clip style", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates) { t ->
                    TemplateCard(t, selected = t.id == selectedTemplateId, onClick = { selectedTemplateId = t.id })
                }
                item {
                    OutlinedButton(onClick = { showCustomTemplateDialog = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("+ Custom")
                    }
                }
            }

            Divider()
            Text("3. Which AI should pick the clips?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviderChoice.entries.forEach { p ->
                    FilterChip(
                        selected = providerChoice == p,
                        onClick = { providerChoice = p },
                        label = { Text(if (p == AiProviderChoice.BOTH) "Both (ensemble)" else p.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                    )
                }
            }
            if (providerChoice == AiProviderChoice.BOTH) {
                Text(
                    "Runs Claude and OpenAI on the same transcript and merges the results, " +
                        "dropping near-duplicate picks. Costs roughly 2x the API calls.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Divider()
            Text("4. Transcription", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranscriptionMode.entries.forEach { m ->
                    FilterChip(
                        selected = transcriptionMode == m,
                        onClick = { transcriptionMode = m },
                        label = { Text(if (m == TranscriptionMode.ON_DEVICE) "On-device" else "Cloud") },
                    )
                }
            }

            Divider()
            Text("5. Output polish", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputPolish.entries.forEach { p ->
                    FilterChip(
                        selected = polish == p,
                        onClick = { polish = p },
                        label = {
                            Text(
                                when (p) {
                                    OutputPolish.RAW_CUT -> "Raw cut"
                                    OutputPolish.VERTICAL_CROP -> "Vertical"
                                    OutputPolish.VERTICAL_CROP_CAPTIONS -> "Vertical + captions"
                                }
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val template = templates.first { it.id == selectedTemplateId }
                    val jobId = UUID.randomUUID().toString()
                    val sourceType = if (sourceTab == 0) "local" else "url"
                    val sourceValue = if (sourceTab == 0) pickedUri.toString() else pastedUrl.trim()
                    val sourceLabel = if (sourceTab == 0) (pickedUri?.lastPathSegment ?: "Uploaded video") else pastedUrl.trim()

                    scope.launch {
                        jobRepo.upsert(
                            ClipJob(
                                id = jobId, createdAt = System.currentTimeMillis(), sourceLabel = sourceLabel,
                                templateId = template.id, templateName = template.name, providerChoice = providerChoice,
                                transcriptionMode = transcriptionMode, polish = polish, status = JobStatus.QUEUED,
                            )
                        )
                        val request = OneTimeWorkRequestBuilder<ClipJobWorker>()
                            .setInputData(
                                workDataOf(
                                    ClipJobWorker.KEY_JOB_ID to jobId,
                                    ClipJobWorker.KEY_SOURCE_TYPE to sourceType,
                                    ClipJobWorker.KEY_SOURCE_VALUE to sourceValue,
                                    ClipJobWorker.KEY_TEMPLATE_ID to template.id,
                                    ClipJobWorker.KEY_PROVIDER to providerChoice.name,
                                    ClipJobWorker.KEY_TRANSCRIPTION to transcriptionMode.name,
                                    ClipJobWorker.KEY_POLISH to polish.name,
                                )
                            )
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(jobId, ExistingWorkPolicy.KEEP, request)
                        onJobCreated(jobId)
                    }
                },
            ) { Text("Generate clips") }
        }
    }

    if (showCustomTemplateDialog) {
        CustomTemplateDialog(
            onDismiss = { showCustomTemplateDialog = false },
            onSave = { name, desc, prompt ->
                scope.launch {
                    templateRepo.addCustom(name, desc, prompt)
                    showCustomTemplateDialog = false
                }
            },
        )
    }
}

@Composable
private fun TemplateCard(template: ClipTemplate, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(template.description, style = MaterialTheme.typography.bodySmall, maxLines = 4)
        }
    }
}

@Composable
private fun CustomTemplateDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Short description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Instructions for the AI (what should it look for?)") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && prompt.isNotBlank(), onClick = { onSave(name, desc, prompt) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
