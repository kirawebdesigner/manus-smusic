package com.smusic.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smusic.app.SmusicViewModel
import com.smusic.app.domain.model.MediaType
import com.smusic.app.domain.player.PlayerState
import com.smusic.app.domain.player.RepeatMode
import com.smusic.app.domain.util.Formatters
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.ErrorContainer
import com.smusic.app.ui.theme.ErrorRed
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.Panel
import com.smusic.app.ui.theme.PanelBorder

/**
 * Full-screen player driven entirely by [PlayerState] from the PlayerController.
 * The Media3 service keeps playing underneath; this screen is a pure view.
 */
@Composable
fun PlayerScreen(
    viewModel: SmusicViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.playerState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink
                )
            }
            Text(
                text = "NOW PLAYING",
                color = Muted,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Accent
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (!state.isActive) {
            EmptyPlayerState()
            return
        }

        // Artwork
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
                text = if (state.mediaType == MediaType.VIDEO) "▶" else "♪",
                color = Accent,
                fontSize = 72.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = ErrorRed,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErrorContainer)
                    .padding(12.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = state.title ?: "",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Ink,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOfNotNull(state.artist, state.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Smusic Library" },
            color = Muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        SeekBar(state = state, onSeek = viewModel::seekTo)

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                    tint = if (state.shuffleEnabled) Accent else Muted
                )
            }

            // Previous
            IconButton(onClick = viewModel::seekPrevious, enabled = state.hasPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (state.hasPrevious) Ink else PanelBorder,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play / pause
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable(onClick = viewModel::togglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = AccentDark,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Next
            IconButton(onClick = viewModel::seekNext, enabled = state.hasNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (state.hasNext) Ink else PanelBorder,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat
            IconButton(onClick = viewModel::cycleRepeatMode) {
                Icon(
                    if (state.repeatMode == RepeatMode.OFF) Icons.Default.Repeat else Icons.Default.RepeatOn,
                    contentDescription = when (state.repeatMode) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    tint = if (state.repeatMode == RepeatMode.OFF) Muted else Accent
                )
            }
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val progress = dragging
        ?: if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { fraction ->
                    if (state.durationMs > 0) {
                        onSeek((fraction * state.durationMs).toLong())
                    }
                }
                dragging = null
            },
            valueRange = 0f..1f,
            enabled = state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = PanelBorder,
                disabledThumbColor = PanelBorder,
                disabledActiveTrackColor = PanelBorder,
                disabledInactiveTrackColor = Panel
            ),
            modifier = Modifier.semantics { contentDescription = "Playback position" }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Formatters.formatTime(state.positionMs),
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                text = if (state.durationMs > 0) {
                    "-${Formatters.formatTime(state.durationMs - state.positionMs)}"
                } else {
                    "--:--"
                },
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyPlayerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Panel),
            contentAlignment = Alignment.Center
        ) {
            Text("♪", color = Muted, fontSize = 44.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Nothing playing", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 18.sp)
        Text(
            text = "Pick something from your Library to start listening.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
