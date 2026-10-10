package com.yourname.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.home.loadHomeState
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.data.storage.SourceStore
import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.data.xtream.hostName
import com.yourname.lumen.data.xtream.normalizeServerUrl
import com.yourname.lumen.data.xtream.validateServerUrl
import com.yourname.lumen.domain.model.ConnectionResult
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.ui.boot.BootScreen
import com.yourname.lumen.ui.common.PlaceholderScreen
import com.yourname.lumen.ui.details.DetailsScreen
import com.yourname.lumen.ui.home.HomeScreen
import com.yourname.lumen.ui.navigation.DockSlot
import com.yourname.lumen.ui.navigation.LumenDock
import com.yourname.lumen.ui.navigation.Section
import com.yourname.lumen.ui.settings.AddSourceScreen
import com.yourname.lumen.ui.settings.SettingsActions
import com.yourname.lumen.ui.settings.SettingsInfo
import com.yourname.lumen.ui.settings.SettingsScreen
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LumenApp(settings: AppSettings) {
    val context = LocalContext.current
    val store = remember { SourceStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // ----- data
    var sources by remember { mutableStateOf(store.load()) }
    val activeSource = sources.firstOrNull { it.id == settings.activeSourceId } ?: sources.firstOrNull()
    var homeState by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }
    var lastRefreshAt by remember { mutableLongStateOf(0L) }
    var reconnectingId by remember { mutableStateOf<String?>(null) }
    var imageCacheBytes by remember { mutableLongStateOf(0L) }
    var cacheVersion by remember { mutableIntStateOf(0) }

    // ----- screens
    var section by remember { mutableStateOf(Section.Home) }
    var showAddSource by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<Source?>(null) }
    var detailsItem by remember { mutableStateOf<MediaItem?>(null) }

    // ----- dock and focus
    var dockHasFocus by remember { mutableStateOf(false) }
    var homeAtTop by remember { mutableStateOf(true) }
    var dockForced by remember { mutableStateOf(false) }
    val dockFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val dockSlots = remember { mutableStateMapOf<Section, DockSlot>() }

    // ----- boot screen: at least a moment, until Home has its data, but never longer than 12s
    var minElapsed by remember { mutableStateOf(false) }
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        minElapsed = true
        delay(11_200)
        booted = true
    }
    LaunchedEffect(minElapsed, homeState) {
        if (minElapsed && homeState !is HomeState.Loading) booted = true
    }

    // Reload Home when the active source changes (not when only its status text changes) or on refresh.
    val loadKey = activeSource?.copy(lastCheckedAt = 0L, lastError = null)
    LaunchedEffect(loadKey, retryKey) {
        homeState = HomeState.Loading
        homeState = loadHomeState(activeSource)
        if (homeState is HomeState.Ready) lastRefreshAt = System.currentTimeMillis()
    }

    // Optional automatic refresh while the app stays open.
    LaunchedEffect(settings.autoRefreshHours) {
        val hours = settings.autoRefreshHours
        if (hours > 0) {
            while (true) {
                delay(hours * 3_600_000L)
                retryKey++
            }
        }
    }

    // Image cache size for the Settings screen.
    LaunchedEffect(section, cacheVersion) {
        if (section == Section.Settings) {
            imageCacheBytes = context.imageLoader.diskCache?.size ?: 0L
        }
    }

    // Put focus on the page once it has something to focus, without stealing it from the dock.
    LaunchedEffect(showAddSource, detailsItem, homeState, section) {
        if (!showAddSource && detailsItem == null && homeState !is HomeState.Loading && !dockHasFocus) {
            runCatching { contentFocus.requestFocus() }
        }
    }

    // The dock hides while Home is scrolled down and returns at the very top.
    LaunchedEffect(section) {
        if (section != Section.Home) homeAtTop = true
    }
    val dockVisible = section != Section.Home || homeAtTop || dockHasFocus || dockForced

    // Back from content brings the dock back and moves focus to it; Back on the dock leaves the app.
    BackHandler(enabled = !dockHasFocus && !showAddSource && detailsItem == null) {
        dockForced = true
    }
    LaunchedEffect(dockForced) {
        if (dockForced) {
            delay(150)
            runCatching { dockFocus.requestFocus() }
        }
    }
    LaunchedEffect(dockHasFocus) {
        if (dockHasFocus) dockForced = false
    }

    // ----- actions
    fun reconnect(source: Source) {
        scope.launch {
            reconnectingId = source.id
            val result = XtreamClient(source).checkConnection()
            val problem = (result as? ConnectionResult.Failed)?.message
            store.update(source.copy(lastCheckedAt = System.currentTimeMillis(), lastError = problem))
            sources = store.load()
            AppLog.add(
                if (problem == null) "Connected to ${source.name}" else "Connection to ${source.name} failed: $problem",
            )
            reconnectingId = null
            if (problem == null && source.id == activeSource?.id) retryKey++
        }
    }

    /** Checks the login, saves it, and returns a message for the user, or null on success. */
    suspend fun submitSource(server: String, user: String, pass: String): String? {
        validateServerUrl(server)?.let { return it }
        val url = normalizeServerUrl(server)
        val existing = editingSource
        val candidate = Source(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = hostName(url),
            baseUrl = url,
            username = user.trim(),
            password = pass,
        )
        return when (val result = XtreamClient(candidate).checkConnection()) {
            is ConnectionResult.Failed -> {
                AppLog.add("Connection to ${candidate.name} failed: ${result.message}")
                result.message
            }

            ConnectionResult.Connected -> {
                val saved = candidate.copy(lastCheckedAt = System.currentTimeMillis())
                if (existing != null) store.update(saved) else store.add(saved)
                sources = store.load()
                if (settings.activeSourceId == null || sources.size == 1) settings.updateActiveSource(saved.id)
                if (existing != null && existing.id == activeSource?.id) retryKey++
                AppLog.add("Connected to ${saved.name}")
                null
            }
        }
    }

    // ----- layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTheme.colors.background)
            // Ignore the remote while the boot screen is showing.
            .onPreviewKeyEvent { !booted },
    ) {
        when {
            showAddSource -> AddSourceScreen(
                initial = editingSource,
                onClose = {
                    showAddSource = false
                    editingSource = null
                },
                onSubmit = { server, user, pass -> submitSource(server, user, pass) },
            )

            detailsItem != null -> DetailsScreen(
                item = detailsItem!!,
                loadDetails = { item -> activeSource?.let { XtreamClient(it).loadDetails(item) } },
                onBack = { detailsItem = null },
            )

            else -> {
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
                            showRatings = settings.showRatings,
                            autoRotate = settings.heroAutoRotate,
                            onOpenDetails = { detailsItem = it },
                            onAtTopChange = { homeAtTop = it },
                            onAddSource = {
                                editingSource = null
                                showAddSource = true
                            },
                            onRetry = { retryKey++ },
                        )

                        Section.Settings -> SettingsScreen(
                            sources = sources,
                            info = SettingsInfo(
                                activeSourceId = activeSource?.id,
                                reconnectingId = reconnectingId,
                                lastRefreshAt = lastRefreshAt,
                                imageCacheBytes = imageCacheBytes,
                            ),
                            settings = settings,
                            actions = SettingsActions(
                                onAddSource = {
                                    editingSource = null
                                    showAddSource = true
                                },
                                onEditSource = {
                                    editingSource = it
                                    showAddSource = true
                                },
                                onRemoveSource = { removed ->
                                    store.remove(removed.id)
                                    sources = store.load()
                                    if (settings.activeSourceId == removed.id) {
                                        settings.updateActiveSource(sources.firstOrNull()?.id)
                                    }
                                    AppLog.add("Removed ${removed.name}")
                                },
                                onSetActive = { settings.updateActiveSource(it.id) },
                                onReconnect = { reconnect(it) },
                                onRefreshLibrary = { retryKey++ },
                                onClearImageCache = {
                                    context.imageLoader.memoryCache?.clear()
                                    context.imageLoader.diskCache?.clear()
                                    cacheVersion++
                                    AppLog.add("Image cache cleared")
                                },
                            ),
                            dockFocus = dockFocus,
                            contentFocus = contentFocus,
                        )

                        else -> PlaceholderScreen(current)
                    }
                }

                AnimatedVisibility(
                    visible = dockVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { -it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { -it },
                ) {
                    LumenDock(
                        selected = section,
                        slots = dockSlots,
                        onSelect = { section = it },
                        selectedFocusRequester = dockFocus,
                        downTarget = contentFocus,
                        onDockFocusChange = { dockHasFocus = it },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(animationSpec = tween(400)),
        ) {
            BootScreen()
        }
    }
}
