package com.smusic.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.SmusicViewModel
import com.smusic.app.data.settings.AppSettings
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.domain.storage.StorageUsage
import com.smusic.app.domain.util.Formatters
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.PanelHover

/**
 * Product settings. Every row controls real persisted behavior via
 * [AppSettings]; nothing here is decorative. Storage rows act on the actual
 * download directory, and About shows real build metadata and licenses.
 */
@Composable
fun SettingsScreen(viewModel: SmusicViewModel, onConnectSpotify: () -> Unit = {}) {
    val defaultCategory by viewModel.defaultCategory.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val maxConcurrent by viewModel.maxConcurrent.collectAsState()
    val autoRetry by viewModel.autoRetry.collectAsState()
    val autoplayNext by viewModel.autoplayNext.collectAsState()
    val resumePlayback by viewModel.resumePlayback.collectAsState()
    val shuffleDefault by viewModel.shuffleDefault.collectAsState()
    val repeatDefault by viewModel.repeatDefault.collectAsState()
    val spotifyConnected by viewModel.spotifyConnected.collectAsState()
    val storageUsage by viewModel.storageUsage.collectAsState()
    val queue by viewModel.queue.collectAsState()

    val context = LocalContext.current
    val versionName = remember { readVersionName(context) }

    LaunchedEffect(Unit) { viewModel.refreshStorageUsage() }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "PREFERENCES",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    text = "Every switch here changes real behavior.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        // --- Downloads ---
        item {
            SectionHeader("DOWNLOADS")
            SettingsCard {
                SelectRow(
                    title = "Default destination",
                    subtitle = "New downloads start in this folder",
                    options = listOf(
                        StorageCategory.MUSIC to "Music",
                        StorageCategory.VIDEOS to "Video",
                        StorageCategory.OTHER to "Other"
                    ),
                    selected = defaultCategory,
                    onSelect = viewModel::setDefaultCategory
                )
                Divider()
                ToggleRow(
                    title = "Wi-Fi only",
                    subtitle = "Queue downloads until on an unmetered network",
                    checked = wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly
                )
                Divider()
                SelectRow(
                    title = "Concurrent downloads",
                    subtitle = "How many files stream at once",
                    options = listOf(1 to "1", 2 to "2", 3 to "3"),
                    selected = maxConcurrent,
                    onSelect = viewModel::setMaxConcurrent
                )
                Divider()
                ToggleRow(
                    title = "Auto-retry failures",
                    subtitle = "Retry connection errors automatically (max 3 attempts)",
                    checked = autoRetry,
                    onCheckedChange = viewModel::setAutoRetry
                )
            }
        }

        // --- Playback ---
        item {
            SectionHeader("PLAYBACK")
            SettingsCard {
                ToggleRow(
                    title = "Autoplay next",
                    subtitle = "Keep playing through the queue",
                    checked = autoplayNext,
                    onCheckedChange = viewModel::setAutoplayNext
                )
                Divider()
                ToggleRow(
                    title = "Resume playback",
                    subtitle = "Continue the last track where you left off",
                    checked = resumePlayback,
                    onCheckedChange = viewModel::setResumePlayback
                )
                Divider()
                ToggleRow(
                    title = "Shuffle by default",
                    subtitle = "Start new queues shuffled",
                    checked = shuffleDefault,
                    onCheckedChange = viewModel::setShuffleDefault
                )
                Divider()
                SelectRow(
                    title = "Repeat by default",
                    subtitle = "Applied when a new queue starts",
                    options = listOf(
                        AppSettings.RepeatDefault.OFF to "Off",
                        AppSettings.RepeatDefault.ALL to "All",
                        AppSettings.RepeatDefault.ONE to "One"
                    ),
                    selected = repeatDefault,
                    onSelect = viewModel::setRepeatDefault
                )
            }
        }

        // --- Source connections ---
        item {
            SectionHeader("SOURCE CONNECTIONS")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Spotify catalog", color = Ink, fontWeight = FontWeight.Medium)
                        Text(
                            if (spotifyConnected) "Connected for playlist metadata" else "Connect for official playlist and catalog metadata",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onConnectSpotify,
                        enabled = !spotifyConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentDark)
                    ) { Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // --- Storage ---
        item {
            SectionHeader("STORAGE")
            SettingsCard {
                StorageUsageRow(usage = storageUsage)
                Divider()
                ActionRow(
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Accent) },
                    title = "Clear temporary files",
                    subtitle = storageUsage?.let {
                        if (it.temporaryBytes > 0) "${Formatters.formatBytes(it.temporaryBytes)} of partial downloads"
                        else "No partial downloads"
                    } ?: "Calculating…",
                    enabled = (storageUsage?.temporaryBytes ?: 0L) > 0L,
                    onClick = viewModel::clearTemporaryFiles
                )
                Divider()
                ActionRow(
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Accent) },
                    title = "Remove download history",
                    subtitle = queue.count { it.state == com.smusic.app.domain.model.JobState.COMPLETED }
                        .let { count ->
                            when {
                                count == 0 -> "No history to remove · finished files are kept"
                                else -> "Clears $count finished queue entries · files are kept"
                            }
                        },
                    enabled = queue.any { it.state == com.smusic.app.domain.model.JobState.COMPLETED },
                    onClick = viewModel::clearDownloadHistory
                )
            }
        }

        // --- About ---
        item {
            SectionHeader("ABOUT")
            SettingsCard {
                InfoRow(
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Accent) },
                    title = "Smusic",
                    subtitle = "Version $versionName · Apache-2.0"
                )
                Divider()
                InfoRow(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = Accent) },
                    title = "Open source",
                    subtitle = "AndroidX Media3 · WorkManager · Jetpack Compose · Material 3 — all Apache-2.0"
                )
                Divider()
                InfoRow(
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = Accent) },
                    title = "Privacy",
                    subtitle = "No analytics or tracking. Files go straight from the URL you provide to this device."
                )
            }
        }

        item {
            Text(
                text = "Smusic only downloads media you're authorized to access. " +
                    "Protected or authenticated sources are reported as unsupported.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// --- Rows ---------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = PanelBorder, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentDark,
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = PanelHover,
                uncheckedBorderColor = PanelBorder
            )
        )
    }
}

@Composable
private fun <T> SelectRow(
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Accent else PanelHover)
                        .border(1.dp, if (isSelected) Accent else PanelBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AccentDark else Ink,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Ink else Muted
            )
            Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun StorageUsageRow(usage: StorageUsage?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Storage used", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(
                text = if (usage == null) "Calculating…" else buildString {
                    append(Formatters.formatBytes(usage.totalBytes))
                    append(" total")
                    if (usage.itemCount > 0) append(" · ${usage.itemCount} files")
                },
                fontSize = 12.sp,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (usage != null && usage.temporaryBytes > 0) {
                Text(
                    text = "${Formatters.formatBytes(usage.temporaryBytes)} unfinished downloads",
                    fontSize = 11.sp,
                    color = Accent,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun readVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"
}.getOrDefault("dev")
