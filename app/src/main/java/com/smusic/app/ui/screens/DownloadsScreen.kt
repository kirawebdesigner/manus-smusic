package com.smusic.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.smusic.app.SmusicViewModel
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.MediaType
import com.smusic.app.ui.DownloadUiModel
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.ErrorRed
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.PanelHover
import com.smusic.app.ui.theme.SuccessGreen
import com.smusic.app.domain.util.Formatters
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Queue overview grouped into Active / Queued / Failed / Completed sections.
 * Every action shown matches the job's state, progress figures are real bytes
 * from the worker, and speed/ETA come from the engine's smoothed measurements.
 */
@Composable
fun DownloadsScreen(
    viewModel: SmusicViewModel,
    onPlayCompleted: (String) -> Unit
) {
    val queue by viewModel.queue.collectAsState()
    val sections = DownloadUiModel.sections(queue)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Screen Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOWNLOAD ENGINE",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Downloads",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                }
                if (sections.completed.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearCompletedJobs) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = "Clear completed entries",
                            tint = Muted
                        )
                    }
                }
            }
        }

        // Section: Active
        if (sections.active.isNotEmpty()) {
            item { SectionTitle("DOWNLOADING (${sections.active.size})") }
            items(sections.active, key = { "active_${it.id}" }) { job ->
                ActiveDownloadCard(
                    job = job,
                    onCancel = { viewModel.cancelJob(job.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Section: Queued
        if (sections.queued.isNotEmpty()) {
            item { SectionTitle("QUEUED (${sections.queued.size})") }
            items(sections.queued, key = { "queued_${it.id}" }) { job ->
                QueuedJobCard(
                    job = job,
                    onRemove = { viewModel.removeJob(job.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Section: Failed / Cancelled
        if (sections.failed.isNotEmpty()) {
            item { SectionTitle("NEEDS ATTENTION (${sections.failed.size})") }
            items(sections.failed, key = { "failed_${it.id}" }) { job ->
                FailedJobCard(
                    job = job,
                    onRetry = { viewModel.retryJob(job.id) },
                    onDelete = { viewModel.removeJob(job.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Section: Completed
        if (sections.completed.isNotEmpty()) {
            item { SectionTitle("COMPLETED (${sections.completed.size})") }
            items(sections.completed, key = { "done_${it.id}" }) { job ->
                CompletedJobCard(
                    job = job,
                    onPlay = { job.localPath?.let(onPlayCompleted) },
                    onDelete = { viewModel.removeJob(job.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Empty state
        if (sections.isEmpty) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Queue is empty", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
                        Text(
                            "Downloads you start appear here with live byte progress, speed, " +
                                "and background execution — even if you close the app.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Muted,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp
    )
}

@Composable
private fun TypeBadge(mediaType: MediaType, size: Int = 42) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelHover),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (mediaType == MediaType.AUDIO) "♪" else "▶",
            color = Accent,
            fontSize = (size / 2).sp
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    job: DownloadJob,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = job.progress.coerceIn(0f, 1f),
        label = "download_progress"
    )
    val eta = Formatters.formatEta(
        remainingBytes = (job.totalBytes - job.downloadedBytes).coerceAtLeast(0L),
        speedBytesPerSecond = job.speedBytesPerSecond
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(job.mediaInfo.mediaType)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = job.mediaInfo.title,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${job.selectedFormat.container} · ${job.mediaInfo.uploader}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel download", tint = Muted)
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Accent,
                trackColor = PanelBorder,
                drawStopIndicator = {}
            )

            // Metrics row: bytes, ETA, speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val percent = (job.progress * 100).toInt()
                val downloaded = Formatters.formatBytes(job.downloadedBytes)
                val total = if (job.totalBytes > 0) Formatters.formatBytes(job.totalBytes) else "unknown size"

                Text(
                    text = "$percent% · $downloaded of $total",
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatSpeed(job.speedBytesPerSecond),
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (eta != null) {
                        Text(text = eta, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuedJobCard(
    job: DownloadJob,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Muted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.mediaInfo.title, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = job.errorMessage ?: "Waiting in queue · ${job.selectedFormat.container}"
                Text(status, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove from queue", tint = Muted)
            }
        }
    }
}

@Composable
private fun FailedJobCard(
    job: DownloadJob,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ErrorRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.mediaInfo.title, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = job.errorMessage ?: "Download stopped",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry download", tint = Accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove entry", tint = Muted)
            }
        }
    }
}

@Composable
private fun CompletedJobCard(
    job: DownloadJob,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = job.localPath?.let { File(it) }
    val isPlayable = file?.exists() == true

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.mediaInfo.title, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val completedAt = job.completedAt
                val details = buildString {
                    append(job.selectedFormat.container)
                    append(" · ")
                    append(
                        if (job.totalBytes > 0) Formatters.formatBytes(job.totalBytes)
                        else job.selectedFormat.fileSize
                    )
                    if (completedAt != null) {
                        append(" · ")
                        append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(completedAt)))
                    }
                }
                Text(details, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlay, enabled = isPlayable) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = if (isPlayable) Accent else Muted)
            }
            IconButton(onClick = {
                file?.let { target ->
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            target
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = job.selectedFormat.mimeType ?: "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${job.mediaInfo.title}"))
                    }
                }
            }, enabled = isPlayable) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share file",
                    tint = if (isPlayable) Muted else PanelBorder
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove entry", tint = Muted)
            }
        }
    }
}
