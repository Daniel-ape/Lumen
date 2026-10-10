package com.yourname.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.data.home.loadHomeState
import com.yourname.lumen.data.storage.SourceStore
import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.data.xtream.hostName
import com.yourname.lumen.data.xtream.normalizeServerUrl
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.ui.boot.BootScreen
import com.yourname.lumen.ui.common.PlaceholderScreen
import com.yourname.lumen.ui.home.HomeScreen
import com.yourname.lumen.ui.navigation.LumenDock
import com.yourname.lumen.ui.navigation.Section
import com.yourname.lumen.ui.settings.AddSourceScreen
import com.yourname.lumen.ui.settings.SettingsScreen
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun LumenApp() {
    val context = LocalContext.current
    val store = remember { SourceStore(context.applicationContext) }

    var sources by remember { mutableStateOf(store.load()) }
    var homeState by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }
    var showAddSource by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(Section.Home) }
    var dockHasFocus by remember { mutableStateOf(false) }
    val dockFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }

    // Boot screen: show for at least a moment, until Home has its data, but never longer than 12s.
    var minElapsed by remember { mutableStateOf(false) }
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_800)
        minElapsed = true
        delay(10_200)
        booted = true
    }
    LaunchedEffect(minElapsed, homeState) {
        if (minElapsed && homeState !is HomeState.Loading) booted = true
    }

    // Reload Home whenever the sources change or the user presses Retry.
    LaunchedEffect(sources, retryKey) {
        homeState = HomeState.Loading
        homeState = loadHomeState(sources.firstOrNull())
    }

    // Put focus on the page once it has something to focus, without stealing it from the dock.
    LaunchedEffect(showAddSource, homeState, section) {
        if (!showAddSource && homeState !is HomeState.Loading && !dockHasFocus) {
            runCatching { contentFocus.requestFocus() }
        }
    }

    // Back from content returns to the dock; Back on the dock leaves the app.
    BackHandler(enabled = !dockHasFocus && !showAddSource) {
        runCatching { dockFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTheme.colors.background)
            // Ignore the remote while the boot screen is showing.
            .onPreviewKeyEvent { !booted },
    ) {
        if (showAddSource) {
            AddSourceScreen(
                onClose = { showAddSource = false },
                onSubmit = { server, user, pass ->
                    val url = normalizeServerUrl(server)
                    val candidate = Source(
                        id = UUID.randomUUID().toString(),
                        name = hostName(url),
                        baseUrl = url,
                        username = user.trim(),
                        password = pass,
                    )
                    val ok = try {
                        XtreamClient(candidate).authenticate()
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) {
                        store.add(candidate)
                        sources = store.load()
                    }
                    ok
                },
            )
        } else {
            Crossfade(
                targetState = section,
                animationSpec = tween(220),
                label = "section",
            ) { current ->
                when (current) {
                    Section.Home -> HomeScreen(
                        state = homeState,
                        dockFocus = dockFocus,
                        contentFocus = contentFocus,
                        dockHasFocus = dockHasFocus,
                        onAddSource = { showAddSource = true },
                        onRetry = { retryKey++ },
                    )

                    Section.Settings -> SettingsScreen(
                        sources = sources,
                        dockFocus = dockFocus,
                        contentFocus = contentFocus,
                        onAddSource = { showAddSource = true },
                        onRemove = {
                            store.remove(it.id)
                            sources = store.load()
                        },
                    )

                    else -> PlaceholderScreen(current)
                }
            }

            LumenDock(
                selected = section,
                onSelect = { section = it },
                selectedFocusRequester = dockFocus,
                downTarget = contentFocus,
                onDockFocusChange = { dockHasFocus = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            )
        }

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(animationSpec = tween(500)),
        ) {
            BootScreen()
        }
    }
}
