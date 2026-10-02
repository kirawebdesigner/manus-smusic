package com.smusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import com.smusic.app.data.AnalysisResult
import com.smusic.app.data.DownloadState
import com.smusic.app.data.MediaFormat
import com.smusic.app.data.MediaItem
import com.smusic.app.data.MediaKind

private val Ink = Color(0xFFF4F1EA)
private val Canvas = Color(0xFF0A0A0C)
private val Panel = Color(0xFF151518)
private val Muted = Color(0xFF9A9894)
private val Accent = Color(0xFFD7F26A)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent { SmusicTheme { SmusicApp(player) } }
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
private fun SmusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(background = Canvas, surface = Panel, primary = Accent, onPrimary = Color(0xFF1A1D0D), onBackground = Ink, onSurface = Ink, outline = Color(0xFF343438)), content = content)
}

@Composable
private fun SmusicApp(player: ExoPlayer, vm: SmusicViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var playingMedia by remember { mutableStateOf<MediaItem?>(null) }
    val library by vm.library.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val download by vm.download.collectAsState()
    Scaffold(containerColor = Canvas, bottomBar = { BottomBar(tab) { tab = it } }) { padding ->
        AnimatedContent(targetState = if (playingMedia != null) 4 else tab, modifier = Modifier.padding(padding), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
            when (screen) {
                0 -> HomeScreen(vm, library, analysis, download, onOpenPlayer = { playingMedia = it })
                1 -> LibraryScreen(library, onOpenPlayer = { playingMedia = it })
                2 -> SettingsScreen()
                else -> PlayerScreen(playingMedia, player, onBack = { playingMedia = null })
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = Canvas, modifier = Modifier.navigationBarsPadding()) {
        listOf(Icons.Default.Home to "Home", Icons.Default.LibraryMusic to "Library", Icons.Default.Settings to "Settings").forEachIndexed { index, item ->
            NavigationBarItem(selected = tab == index, onClick = { onTab(index) }, icon = { Icon(item.first, null) }, label = { Text(item.second, fontSize = 11.sp) })
        }
    }
}

@Composable
private fun HomeScreen(vm: SmusicViewModel, library: List<MediaItem>, analysis: AnalysisResult?, download: com.smusic.app.data.DownloadTask?, onOpenPlayer: (MediaItem) -> Unit) {
    val url by vm.url.collectAsState()
    val busy by vm.busy.collectAsState()
    val clipboard = LocalClipboardManager.current
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) { Text("SMUSIC", color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 12.sp); Spacer(Modifier.height(8.dp)); Text("Make it yours.", fontSize = 32.sp, fontWeight = FontWeight.SemiBold); Text("Save the moments worth keeping.", color = Muted, fontSize = 15.sp) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Color(0xFF6B7D32)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = Color(0xFF18200B), modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ADD MEDIA", color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                TextField(value = url, onValueChange = vm::setUrl, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste a media URL", color = Muted) }, leadingIcon = { Icon(Icons.Default.Link, null, tint = Muted) }, trailingIcon = { IconButton(onClick = { clipboard.getText()?.let { vm.setUrl(it.text) } }) { Icon(Icons.Default.ContentPaste, "Paste", tint = Accent) } }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Accent))
                Button(onClick = vm::analyze, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF18200B))) { if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF18200B)) else { Icon(Icons.Default.Speed, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Analyze link", fontWeight = FontWeight.Bold) } }
            }
        }
        if (analysis != null) item { AnalysisCard(analysis, vm, download) }
        if (library.isNotEmpty()) item { SectionHeader("RECENTLY SAVED", "See library") }
        if (library.isEmpty()) item { EmptyState() }
        items(library.take(3), key = { it.id }) { media -> MediaRow(media, onClick = { onOpenPlayer(media) }) }
    }
}

@Composable
private fun AnalysisCard(result: AnalysisResult, vm: SmusicViewModel, download: com.smusic.app.data.DownloadTask?) {
    when (result) {
        is AnalysisResult.Invalid, is AnalysisResult.Unsupported -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF201A1A)), shape = RoundedCornerShape(22.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color(0xFFF0A6A6)); Spacer(Modifier.width(12.dp)); Text((result as? AnalysisResult.Invalid)?.message ?: (result as AnalysisResult.Unsupported).message, color = Color(0xFFF0C4C4), fontSize = 14.sp) } }
        is AnalysisResult.Success -> { val selected by vm.selectedFormat.collectAsState(); Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF3D4749), Color(0xFF15191A)))), contentAlignment = Alignment.Center) { Text(if (result.media.kind == MediaKind.AUDIO) "♪" else "▶", color = Accent, fontSize = 27.sp) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(result.media.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${result.media.creator} · ${result.media.source}", color = Muted, fontSize = 12.sp) } }
                Text("CHOOSE FORMAT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                result.formats.forEach { format -> FormatRow(format, selected == format) { vm.selectFormat(format) } }
                if (download?.state == DownloadState.DOWNLOADING) { LinearProgress(download.progress); Text("Downloading ${(download.progress * 100).toInt()}%", color = Muted, fontSize = 12.sp) } else if (download?.state == DownloadState.COMPLETE) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Saved to your library", color = Accent, fontWeight = FontWeight.Medium) } } else Button(onClick = vm::download, enabled = selected != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Canvas)) { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Download", fontWeight = FontWeight.Bold) }
            } } }
    }
}

@Composable private fun LinearProgress(progress: Float) { Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF303034))) { Box(Modifier.fillMaxWidth(progress).height(6.dp).background(Accent)) } }
@Composable private fun FormatRow(format: MediaFormat, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0xFF293016) else Color(0xFF1D1D20)).clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) Accent else Color(0xFF55555A))); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(format.label, fontWeight = FontWeight.Medium); Text(format.detail, color = Muted, fontSize = 12.sp) }; Text(format.size, color = Muted, fontSize = 12.sp) } }
@Composable private fun SectionHeader(title: String, action: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, color = Muted, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.7.sp, modifier = Modifier.weight(1f)); Text(action, color = Accent, fontSize = 12.sp) } }
@Composable private fun EmptyState() { Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LibraryMusic, null, tint = Color(0xFF6D6D73), modifier = Modifier.size(32.dp)); Spacer(Modifier.height(10.dp)); Text("Your library is quiet", fontWeight = FontWeight.SemiBold); Text("Analyze a link above and your saved media will appear here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)) } } }
@Composable private fun MediaRow(media: MediaItem, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF292B2D)), contentAlignment = Alignment.Center) { Text(if (media.kind == MediaKind.AUDIO) "♪" else "▶", color = Accent, fontSize = 22.sp) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(media.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${media.creator} · ${media.fileSize}", color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.MoreHoriz, null, tint = Muted) } }

@Composable private fun LibraryScreen(library: List<MediaItem>, onOpenPlayer: (MediaItem) -> Unit) { LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { item { Text("Library", fontSize = 32.sp, fontWeight = FontWeight.SemiBold); Text("Everything you chose to keep.", color = Muted) }; item { TextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth(), enabled = false, placeholder = { Text("Search your library", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(disabledContainerColor = Panel, disabledIndicatorColor = Color.Transparent)) }; if (library.isEmpty()) item { EmptyState() }; items(library, key = { it.id }) { media -> MediaRow(media) { onOpenPlayer(media) } } } }

@Composable private fun PlayerScreen(media: MediaItem?, player: ExoPlayer, onBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    LaunchedEffect(media) {
        if (media?.localPath != null) {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(media.localPath))
            player.prepare()
            player.playWhenReady = false
        }
        while (isActive) { position = player.currentPosition.coerceAtLeast(0L); duration = player.duration.coerceAtLeast(0L); isPlaying = player.isPlaying; delay(250) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("NOW PLAYING", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(Icons.Default.MoreHoriz, null, tint = Muted) }
        Spacer(Modifier.height(48.dp)); Box(Modifier.size(280.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B4548), Color(0xFF171A1B)))), contentAlignment = Alignment.Center) { Text(if (media?.kind == MediaKind.AUDIO) "♪" else "▶", color = Accent, fontSize = 80.sp) }
        Spacer(Modifier.height(28.dp)); Text(media?.title ?: "Nothing playing", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(media?.creator ?: "Choose something from your library", color = Muted)
        Spacer(Modifier.height(24.dp)); Slider(value = if (duration > 0) position.toFloat() / duration else 0f, onValueChange = { if (duration > 0) player.seekTo((it * duration).toLong()) }, valueRange = 0f..1f, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatMs(position), color = Muted, fontSize = 12.sp); Text(formatMs(duration), color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(25.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) { Icon(Icons.Default.Tune, null, tint = Muted); Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(52.dp).clip(CircleShape).background(Accent).clickable { if (player.isPlaying) player.pause() else player.play(); isPlaying = player.isPlaying }.padding(14.dp)); Icon(Icons.Default.LibraryMusic, null, tint = Muted) }
    }
}
private fun formatMs(value: Long): String { val seconds = value / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }

@Composable private fun SettingsScreen() { LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { item { Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.SemiBold); Text("Quiet control over your listening space.", color = Muted) }; item { SettingSection("DOWNLOADS", listOf("Default audio quality" to "Original", "Default video quality" to "1080p", "Wi-Fi only" to "On", "Download location" to "Smusic folder")) }; item { SettingSection("APP", listOf("Theme" to "Dark", "Notifications" to "Enabled", "Open-source licenses" to "View")) }; item { Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Accent); Spacer(Modifier.width(14.dp)); Column { Text("About Smusic", fontWeight = FontWeight.SemiBold); Text("Version 0.1.0 · Built for offline listening", color = Muted, fontSize = 12.sp) } } } } } }
@Composable private fun SettingSection(title: String, values: List<Pair<String, String>>) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp); Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) { Column { values.forEachIndexed { index, (label, value) -> Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 14.sp); Text(value, color = Muted, fontSize = 13.sp) }; if (index < values.lastIndex) Divider(color = Color(0xFF27272B), modifier = Modifier.padding(horizontal = 18.dp)) } } } } }
