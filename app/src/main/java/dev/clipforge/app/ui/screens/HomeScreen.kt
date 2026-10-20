package dev.clipforge.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.clipforge.app.data.jobs.JobRepository
import dev.clipforge.app.model.ClipJob
import dev.clipforge.app.model.JobStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNewJob: () -> Unit, onOpenSettings: () -> Unit, onOpenJob: (String) -> Unit) {
    val context = LocalContext.current
    val jobRepo = remember { JobRepository.get(context) }
    val jobs by jobRepo.jobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClipForge AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewJob, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New clip job") })
        },
    ) { padding ->
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No clip jobs yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Upload a video or paste a link, pick a style, and let the AI find the clips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(jobs) { job -> JobRow(job, onClick = { onOpenJob(job.id) }) }
            }
        }
    }
}

@Composable
private fun JobRow(job: ClipJob, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(job.templateName, fontWeight = FontWeight.SemiBold)
                StatusChip(job.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(job.sourceLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                job.progressMessage.ifBlank { DateFormat.getDateTimeInstance().format(Date(job.createdAt)) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (job.status == JobStatus.DONE) {
                Spacer(Modifier.height(4.dp))
                Text("${job.clips.size} clip(s) ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StatusChip(status: JobStatus) {
    val (label, color) = when (status) {
        JobStatus.DONE -> "Done" to MaterialTheme.colorScheme.secondary
        JobStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        JobStatus.QUEUED -> "Queued" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> "Working…" to MaterialTheme.colorScheme.primary
    }
    AssistChip(onClick = {}, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(labelColor = color))
}
