package com.smusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.player.PlayerState
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.PanelBorder

/**
 * Compact now-playing surface shown above the navigation bar on every tab.
 * Tapping the body opens the full-screen player; the trailing button toggles
 * play/pause. A thin progress line mirrors real playback position.
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isActive) return

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(com.smusic.app.ui.theme.Panel)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2C3236), Color(0xFF14171A)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.mediaType == MediaType.VIDEO) "▶" else "♪",
                    color = Accent,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title ?: "",
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist ?: state.album ?: "",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Accent
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Accent,
            trackColor = PanelBorder,
            drawStopIndicator = {}
        )
    }
}
