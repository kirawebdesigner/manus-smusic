package com.smusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.domain.discovery.DiscoveryResult
import com.smusic.app.domain.model.MediaType
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder
import com.smusic.app.ui.theme.PanelHover

@Composable
fun DiscoveryResultCard(result: DiscoveryResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (result) {
                is DiscoveryResult.Success -> {
                    Text(result.page.title ?: "DISCOVERY RESULTS", color = Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    val rows = result.page.items.ifEmpty { result.page.entries.mapNotNull { it.item } }
                    rows.take(25).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PanelHover), contentAlignment = Alignment.Center) {
                                Text(if (item.mediaType == MediaType.AUDIO) "♪" else "▶", color = Accent)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(item.creator, item.album).joinToString(" · ").ifBlank { item.providerId }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text("Metadata found. Protected platform audio is not downloaded by the official APIs.", color = Muted, fontSize = 12.sp)
                }
                is DiscoveryResult.Unsupported -> Text(result.message, color = Muted, fontSize = 13.sp)
                is DiscoveryResult.Failed -> Text(result.message, color = Muted, fontSize = 13.sp)
            }
        }
    }
}
