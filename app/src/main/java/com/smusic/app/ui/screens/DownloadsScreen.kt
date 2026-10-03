package com.smusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.SmusicViewModel
import com.smusic.app.domain.model.DownloadJob
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaType
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.ErrorRed
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.PanelHover
import com.smusic.app.ui.theme.SuccessGreen

@Composable
fun DownloadsScreen(
    viewModel: SmusicViewModel,
    onPlayCompleted: (String) -> Unit
) {
    val queue by viewModel.queue.collectAsState()

    val downloadingJobs = queue.filter {
        it.state == JobState.DOWNLOADING || it.state == JobState.PROCESSING || it.state == JobState.WAITING
    }
    val queuedJobs = queue.filter { it.state == JobState.QUEUED || it.state == JobState.ANALYZING }
    val completedJobs = queue.filter { it.state == JobState.COMPLETED }
    val failedJobs = queue.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
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
                        text = "Active Queue",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                }
                if (completedJobs.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearCompletedJobs) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Completed", tint = Muted)
                    }
                }
            }
        }

        // Section: Active Downloading
        if (downloadingJobs.isNotEmpty()) {
            item {
                SectionTitle("DOWNLOADING (${downloadingJobs.size})")
            }
            items(downloadingJobs, key = { it.id }) { job ->
                ActiveDownloadCard(
                    job = job,
                    onCancel = { viewModel.cancelJob(job.id) }
                )
            }
        }

        // Section: Queued
        if (queuedJobs.isNotEmpty()) {
            item {
                SectionTitle("QUEUED (${queuedJobs.size})")
            }
            items(queuedJobs, key = { it.id }) { job ->
                QueuedJobCard(
                    job = job,
                    onRemove = { viewModel.removeJob(job.id) }
                )
            }
        }

        // Section: Failed / Cancelled
        if (failedJobs.isNotEmpty()) {
            item {
                SectionTitle("FAILED / CANCELLED (${failedJobs.size})")
            }
            items(failedJobs, key = { it.id }) { job ->
                FailedJobCard(
                    job = job,
                    onRetry = { viewModel.retryJob(job.id) },
                    onDelete = { viewModel.removeJob(job.id) }
                )
            }
        }

        // Section: Completed
        if (completedJobs.isNotEmpty()) {
            item {
                SectionTitle("COMPLETED (${completedJobs.size})")
            }
            items(completedJobs, key = { it.id }) { job ->
                CompletedJobCard(
                    job = job,
                    onPlay = { job.localPath?.let(onPlayCompleted) },
                    onDelete = { viewModel.removeJob(job.id) }
                )
            }
        }

        // Empty state
        if (queue.isEmpty()) {
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
                            "Any media link you download will show live byte progress, download speed, and background execution here.",
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
private fun ActiveDownloadCard(
    job: DownloadJob,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelHover),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (job.mediaInfo.mediaType == MediaType.AUDIO) "♪" else "▶",
                        color = Accent,
                        fontSize = 20.sp
                    )
                }
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
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Muted)
                }
            }

            // Real progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(PanelBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(job.progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(Accent)
                )
            }

            // Metrics row: Speed, Percent, Bytes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val percent = (job.progress * 100).toInt()
                val speedMb = "%.1f MB/s".format(job.speedBytesPerSecond / 1_048_576.0)
                val downloadedMb = "%.1f MB".format(job.downloadedBytes / 1_048_576.0)
                val totalMb = if (job.totalBytes > 0) "%.1f MB".format(job.totalBytes / 1_048_576.0) else "..."

                Text(
                    text = "$percent% · $downloadedMb of $totalMb",
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = speedMb,
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QueuedJobCard(
    job: DownloadJob,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
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
                Text("Waiting in queue · ${job.selectedFormat.container}", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Muted)
            }
        }
    }
}

@Composable
private fun FailedJobCard(
    job: DownloadJob,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.mediaInfo.title, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(job.errorMessage ?: "Download stopped", color = ErrorRed, fontSize = 12.sp)
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted)
            }
        }
    }
}

@Composable
private fun CompletedJobCard(
    job: DownloadJob,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
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
                Text("Saved to storage · ${job.selectedFormat.container}", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted)
            }
        }
    }
}
