package com.smusic.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.SmusicViewModel
import com.smusic.app.data.database.LibraryItem
import com.smusic.app.domain.discovery.DiscoveryResult
import com.smusic.app.domain.model.AnalysisResult
import com.smusic.app.domain.model.MediaFormat
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.model.StorageCategory
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.ErrorContainer
import com.smusic.app.ui.theme.ErrorRed
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.PanelHover

@Composable
fun HomeScreen(
    viewModel: SmusicViewModel,
    onNavigateToQueue: () -> Unit,
    onOpenPlayer: (LibraryItem) -> Unit
) {
    val url by viewModel.url.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val discoveryResult by viewModel.discoveryResult.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val library by viewModel.library.collectAsState()
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // App Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMUSIC",
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Clean Media Pipeline",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(
                        text = "Offline-first library · Direct streaming · Queue engine",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Color(0xFF88A825))))
                        .clickable { onNavigateToQueue() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads Queue",
                        tint = AccentDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Link Input Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ANALYZE MEDIA SOURCE",
                    color = Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
                TextField(
                    value = url,
                    onValueChange = viewModel::setUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanelBorder, RoundedCornerShape(18.dp)),
                    placeholder = { Text("Paste direct media or Spotify link", color = Muted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Muted) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.let { viewModel.setUrl(it.text) }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Accent)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Panel,
                        unfocusedContainerColor = Panel,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Accent
                    )
                )

                Button(
                    onClick = viewModel::analyzeOrDiscover,
                    enabled = !isAnalyzing && !isDiscovering && url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = AccentDark,
                        disabledContainerColor = PanelHover,
                        disabledContentColor = Muted
                    )
                ) {
                    if (isAnalyzing || isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AccentDark
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(if (isDiscovering) "Searching sources..." else "Probing source...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search / Analyze", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (discoveryResult != null) {
            item { DiscoveryResultCard(discoveryResult!!) }
        }

        // Analysis Result Card
        if (analysisResult != null) {
            item {
                when (val result = analysisResult) {
                    // Unreachable: the enclosing `if (analysisResult != null)` guards this block,
                    // but Kotlin requires exhaustiveness over the nullable subject.
                    null -> Unit
                    is AnalysisResult.Invalid -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ErrorRed)
                                Spacer(Modifier.width(12.dp))
                                Text(result.message, color = Ink, fontSize = 14.sp)
                            }
                        }
                    }

                    is AnalysisResult.Failed -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = ErrorRed)
                                    Spacer(Modifier.width(10.dp))
                                    Text(result.error.title, fontWeight = FontWeight.SemiBold, color = Ink)
                                }
                                Text(result.error.message, color = Muted, fontSize = 13.sp)
                                Button(
                                    onClick = viewModel::analyze,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PanelHover,
                                        contentColor = Accent
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Try again", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    is AnalysisResult.Unsupported -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PanelBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Muted)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Unsupported Source", fontWeight = FontWeight.SemiBold, color = Ink)
                                }
                                Text(result.message, color = Muted, fontSize = 13.sp)
                            }
                        }
                    }

                    is AnalysisResult.MetadataOnly -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(PanelHover),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("♫", color = Accent, fontSize = 24.sp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(result.media.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${result.media.uploader} · ${result.media.source}", color = Muted, fontSize = 12.sp)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PanelHover)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("PERMITTED METADATA ONLY", color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                                        Text(result.message, color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    is AnalysisResult.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Media Header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Brush.linearGradient(listOf(Color(0xFF2C3236), Color(0xFF14171A)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (result.media.mediaType == MediaType.AUDIO) "♪" else "▶",
                                            color = Accent,
                                            fontSize = 26.sp
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(result.media.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${result.media.uploader} · ${result.media.fileSize}", color = Muted, fontSize = 12.sp)
                                        result.media.supportsRangeRequests?.let { resumable ->
                                            Text(
                                                text = if (resumable) "Resumable download · range requests supported"
                                                    else "Single-stream download",
                                                color = Muted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Format selection — only when the source offers a real choice.
                                if (result.formats.size > 1) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "SELECT FORMAT & QUALITY",
                                            color = Muted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.4.sp
                                        )
                                        result.formats.forEach { format ->
                                            FormatRowItem(
                                                format = format,
                                                isSelected = selectedFormat == format,
                                                onClick = { viewModel.selectFormat(format) }
                                            )
                                        }
                                    }
                                } else {
                                    // One format: show what the source provides, no fake picker.
                                    result.formats.firstOrNull()?.let { format ->
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "FORMAT",
                                                color = Muted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.4.sp
                                            )
                                            FormatRowItem(
                                                format = format,
                                                isSelected = true,
                                                onClick = {}
                                            )
                                        }
                                    }
                                }

                                // Destination Picker
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "STORAGE DESTINATION",
                                        color = Muted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.4.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        listOf(StorageCategory.MUSIC, StorageCategory.VIDEOS, StorageCategory.OTHER).forEach { cat ->
                                            val isCatSelected = selectedCategory == cat
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isCatSelected) AccentDark else PanelHover)
                                                    .border(1.dp, if (isCatSelected) Accent else Color.Transparent, RoundedCornerShape(12.dp))
                                                    .clickable { viewModel.selectCategory(cat) }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = cat.name.lowercase().replaceFirstChar { it.uppercase() },
                                                    color = if (isCatSelected) Accent else Muted,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                // Add to Queue Button
                                Button(
                                    onClick = {
                                        viewModel.enqueueDownload()
                                        viewModel.clearAnalysis()
                                        onNavigateToQueue()
                                    },
                                    enabled = selectedFormat != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Accent,
                                        contentColor = AccentDark
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add to Download Queue", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Saved Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVED TO LIBRARY",
                    color = Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${library.size} items",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        if (library.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No media saved yet", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            "Analyze a direct media stream above to download and build your offline library.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(library.take(4).size) { index ->
                val item = library[index]
                LibraryRowItem(item = item, onClick = { onOpenPlayer(item) })
            }
        }
    }
}

@Composable
fun FormatRowItem(
    format: MediaFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF242C12) else PanelHover)
            .border(1.dp, if (isSelected) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (isSelected) Accent else PanelBorder),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AccentDark, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(format.label, fontWeight = FontWeight.Medium, color = Ink, fontSize = 14.sp)
            val technicalDetail = buildString {
                append(format.container)
                format.codec?.let { append(" · $it") }
                format.resolution?.let { append(" · $it") }
                format.bitrate?.let { append(" · $it") }
            }
            Text(technicalDetail, color = Muted, fontSize = 12.sp)
        }
        Text(format.fileSize, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LibraryRowItem(
    item: LibraryItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PanelHover),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.mediaType == MediaType.AUDIO) "♪" else "▶",
                color = Accent,
                fontSize = 20.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.creator} · ${item.readableSize}", color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Accent)
    }
}
