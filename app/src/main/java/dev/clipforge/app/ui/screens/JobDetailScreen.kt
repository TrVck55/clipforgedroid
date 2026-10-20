package dev.clipforge.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.clipforge.app.data.jobs.JobRepository
import dev.clipforge.app.model.ClipJob
import dev.clipforge.app.model.JobStatus
import dev.clipforge.app.model.RenderedClip
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(jobId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val jobRepo = remember { JobRepository.get(context) }
    val jobs by jobRepo.jobs.collectAsState()
    val job = jobs.firstOrNull { it.id == jobId }

    Scaffold(topBar = {
        TopAppBar(title = { Text(job?.templateName ?: "Clip job") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        if (job == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Job not found.") }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ProgressHeader(job)
            Spacer(Modifier.height(16.dp))
            if (job.status == JobStatus.DONE) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(job.clips) { clip -> ClipCard(clip) }
                }
            } else if (job.status == JobStatus.FAILED) {
                Text(
                    "Something went wrong: ${job.errorMessage ?: "unknown error"}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(job: ClipJob) {
    Column {
        Text(job.sourceLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        if (job.status != JobStatus.DONE && job.status != JobStatus.FAILED) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Text(job.progressMessage.ifBlank { job.status.name }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ClipCard(clip: RenderedClip) {
    val context = LocalContext.current
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(clip.suggestion.hookTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("${clip.suggestion.viralityScore}") })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${fmt(clip.suggestion.startSec)} – ${fmt(clip.suggestion.endSec)} · from ${clip.suggestion.sourceProvider}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (clip.suggestion.reasoning.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(clip.suggestion.reasoning, style = MaterialTheme.typography.bodySmall)
            }
            if (clip.suggestion.suggestedCaption.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("\u201C${clip.suggestion.suggestedCaption}\u201D", style = MaterialTheme.typography.bodySmall)
            }
            if (clip.suggestion.suggestedHashtags.isNotEmpty()) {
                Text(
                    clip.suggestion.suggestedHashtags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openClip(context, clip.filePath) }) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play")
                }
                OutlinedButton(onClick = { shareClip(context, clip.filePath) }) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(4.dp)); Text("Share")
                }
            }
        }
    }
}

private fun fmt(seconds: Double): String {
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "%d:%02d".format(m, s)
}

private fun uriFor(context: android.content.Context, filePath: String) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))

private fun openClip(context: android.content.Context, filePath: String) {
    val uri = uriFor(context, filePath)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun shareClip(context: android.content.Context, filePath: String) {
    val uri = uriFor(context, filePath)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
