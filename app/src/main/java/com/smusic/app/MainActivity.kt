package com.smusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.domain.model.JobState
import com.smusic.app.domain.model.MediaType
import com.smusic.app.ui.screens.DownloadsScreen
import com.smusic.app.ui.screens.HomeScreen
import com.smusic.app.ui.screens.LibraryScreen
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.Canvas
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.SmusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()

        setContent {
            SmusicTheme {
                SmusicApp(player = player!!)
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun SmusicApp(player: ExoPlayer, vm: SmusicViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var playingItem by remember { mutableStateOf<LibraryItem?>(null) }
    val queue by vm.queue.collectAsState()
    val library by vm.library.collectAsState()

    val activeCount = queue.count {
        it.state == JobState.DOWNLOADING || it.state == JobState.QUEUED || it.state == JobState.PROCESSING
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            if (playingItem == null) {
                BottomBar(tab = tab, activeBadge = activeCount) { tab = it }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = if (playingItem != null) 4 else tab,
            modifier = Modifier.padding(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                0 -> HomeScreen(
                    viewModel = vm,
                    onNavigateToQueue = { tab = 1 },
                    onOpenPlayer = { playingItem = it }
                )
                1 -> DownloadsScreen(
                    viewModel = vm,
                    onPlayCompleted = { path ->
                        val item = library.firstOrNull { it.localPath == path }
                            ?: LibraryItem(
                                id = path,
                                title = File(path).nameWithoutExtension,
                                creator = "Saved Media",
                                localPath = path
                            )
                        playingItem = item
                    }
                )
                2 -> LibraryScreen(
                    viewModel = vm,
                    onOpenPlayer = { playingItem = it }
                )
                3 -> SettingsScreen()
                else -> PlayerScreen(
                    item = playingItem,
                    player = player,
                    onBack = { playingItem = null }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, activeBadge: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple(Icons.Default.Home, "Home", 0),
        Triple(Icons.Default.Download, "Queue", activeBadge),
        Triple(Icons.Default.LibraryMusic, "Library", 0),
        Triple(Icons.Default.Settings, "Settings", 0)
    )

    NavigationBar(
        containerColor = Canvas,
        contentColor = Ink,
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, (icon, label, badgeCount) ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { onTab(index) },
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(badge = {
                            Badge(
                                containerColor = Accent,
                                contentColor = AccentDark
                            ) {
                                Text("$badgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }) {
                            Icon(icon, contentDescription = label)
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentDark,
                    selectedTextColor = Accent,
                    indicatorColor = Accent,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

@Composable
private fun PlayerScreen(
    item: LibraryItem?,
    player: ExoPlayer,
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(item) {
        if (item != null && item.localPath.isNotBlank()) {
            val file = File(item.localPath)
            if (file.exists()) {
                val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(file))
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
        }
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text(
                text = "NOW PLAYING",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Tune, contentDescription = null, tint = Muted)
        }

        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF383C42), Color(0xFF141618))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (item?.mediaType == MediaType.VIDEO) "▶" else "♪",
                color = Accent,
                fontSize = 72.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = item?.title ?: "No track selected",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${item?.creator ?: "Smusic Library"} · ${item?.album ?: ""}",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(28.dp))

        Slider(
            value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                if (duration > 0) {
                    player.seekTo((frac * duration).toLong())
                }
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = PanelBorder
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(position), color = Muted, fontSize = 12.sp)
            Text(formatDuration(duration), color = Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Muted)

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        isPlaying = player.isPlaying
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AccentDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Icon(Icons.Default.Download, contentDescription = null, tint = Muted)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun SettingsScreen() {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Quiet, precise control over your offline library.", color = Muted, fontSize = 14.sp)
        }

        item {
            SettingsCategory(
                title = "DOWNLOAD ARCHITECTURE",
                items = listOf(
                    "Engine" to "Native OkHttp Streaming Engine",
                    "Resume capability" to "HTTP 206 Partial Content",
                    "Duplicate detection" to "SHA-256 + URL Indexing",
                    "Destination" to "Standard Android Media Scoped Folders"
                )
            )
        }

        item {
            SettingsCategory(
                title = "METADATA & TAGGING",
                items = listOf(
                    "ID3 / MP4 Tagging" to "Embedded ID3v2.4 & QuickTime Atoms",
                    "Spotify Integration" to "Metadata Discovery (oEmbed permitted)",
                    "Direct Downloads" to "Direct URL & Content-Disposition"
                )
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Smusic Android v0.2.0", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "Architected cleanly from YTDLnis & spotDL concepts. Native Kotlin + Jetpack Compose + Media3.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategory(title: String, items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                items.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                        Text(value, color = Muted, fontSize = 13.sp)
                    }
                    if (index < items.lastIndex) {
                        Divider(color = PanelBorder, modifier = Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
        }
    }
}
