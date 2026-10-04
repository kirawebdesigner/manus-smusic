package com.smusic.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smusic.app.domain.model.JobState
import com.smusic.app.ui.components.MiniPlayer
import com.smusic.app.ui.screens.DownloadsScreen
import com.smusic.app.ui.screens.HomeScreen
import com.smusic.app.ui.screens.LibraryScreen
import com.smusic.app.ui.screens.PlayerScreen
import com.smusic.app.ui.screens.SettingsScreen
import com.smusic.app.ui.theme.Accent
import com.smusic.app.ui.theme.AccentDark
import com.smusic.app.ui.theme.Canvas
import com.smusic.app.ui.theme.Ink
import com.smusic.app.ui.theme.Muted
import com.smusic.app.ui.theme.SmusicTheme

private const val PLAYER_TAB = 4

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Best-effort: downloads and playback work with or without it.
            if (!granted) {
                android.util.Log.i("MainActivity", "POST_NOTIFICATIONS declined; progress notifications hidden")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            SmusicTheme {
                SmusicApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun SmusicApp(vm: SmusicViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var playerOpen by remember { mutableStateOf(false) }
    val queue by vm.queue.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeCount = queue.count {
        it.state == JobState.DOWNLOADING || it.state == JobState.QUEUED || it.state == JobState.PROCESSING
    }

    // Surface playback errors (missing/corrupt file) even from other tabs.
    val playerError = playerState.errorMessage
    LaunchedEffect(playerError) {
        if (playerError != null) {
            snackbarHostState.showSnackbar(playerError)
            vm.dismissPlayerError()
        }
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!playerOpen) {
                Column {
                    AnimatedVisibility(
                        visible = playerState.isActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayer(
                            state = playerState,
                            onOpen = { playerOpen = true },
                            onTogglePlayPause = vm::togglePlayPause
                        )
                    }
                    BottomBar(tab = tab, activeBadge = activeCount) { tab = it }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = if (playerOpen) PLAYER_TAB else tab,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                when {
                    targetState == PLAYER_TAB ->
                        slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() togetherWith
                            slideOutVertically(targetOffsetY = { -it / 4 }) + fadeOut()
                    initialState == PLAYER_TAB ->
                        fadeIn() togetherWith
                            slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                    else ->
                        fadeIn() togetherWith fadeOut()
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                0 -> HomeScreen(
                    viewModel = vm,
                    onNavigateToQueue = { tab = 1 },
                    onOpenPlayer = { item ->
                        vm.playItem(item)
                        playerOpen = true
                    }
                )

                1 -> DownloadsScreen(
                    viewModel = vm,
                    onPlayCompleted = { path ->
                        vm.playItemByPath(path)
                        playerOpen = true
                    }
                )

                2 -> LibraryScreen(
                    viewModel = vm,
                    onOpenPlayer = { item ->
                        vm.playItem(item)
                        playerOpen = true
                    }
                )

                3 -> SettingsScreen(viewModel = vm)

                else -> PlayerScreen(
                    viewModel = vm,
                    onBack = { playerOpen = false }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, activeBadge: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple(Icons.Default.Home, "Home", 0),
        Triple(Icons.Default.Download, "Downloads", activeBadge),
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
