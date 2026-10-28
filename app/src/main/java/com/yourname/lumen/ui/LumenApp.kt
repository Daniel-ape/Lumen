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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.yourname.lumen.core.designsystem.AppBackground
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.core.util.backdropRequest
import com.yourname.lumen.data.cache.LibraryCache
import com.yourname.lumen.data.epg.EpgCache
import com.yourname.lumen.data.epg.GuideIndex
import com.yourname.lumen.data.search.ProgrammeHit
import com.yourname.lumen.data.search.SearchIndex
import com.yourname.lumen.data.home.loadHomeState
import com.yourname.lumen.data.live.loadLiveState
import com.yourname.lumen.data.m3u.M3uClient
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.data.source.clientFor
import com.yourname.lumen.data.storage.SourceStore
import com.yourname.lumen.data.xtream.buildSelfHostedUrl
import com.yourname.lumen.data.xtream.hostName
import com.yourname.lumen.data.xtream.normalizeServerUrl
import com.yourname.lumen.data.xtream.validatePlaylistLink
import com.yourname.lumen.data.xtream.validateServerUrl
import com.yourname.lumen.domain.model.ConnectionResult
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.LibraryContent
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType
import com.yourname.lumen.domain.model.favoriteKey
import com.yourname.lumen.ui.boot.BootScreen
import com.yourname.lumen.ui.common.PlaceholderScreen
import com.yourname.lumen.ui.details.DetailsScreen
import com.yourname.lumen.ui.home.HomeScreen
import com.yourname.lumen.ui.live.LiveTvScreen
import com.yourname.lumen.ui.navigation.DockSlot
import com.yourname.lumen.ui.navigation.LumenDock
import com.yourname.lumen.ui.navigation.Section
import com.yourname.lumen.ui.player.FullscreenControls
import com.yourname.lumen.ui.player.PlaybackController
import com.yourname.lumen.ui.player.VideoLayer
import com.yourname.lumen.ui.search.ProgrammeDetailsScreen
import com.yourname.lumen.ui.search.SearchScreen
import com.yourname.lumen.ui.search.SearchStatus
import com.yourname.lumen.ui.settings.ConnectionFormScreen
import com.yourname.lumen.ui.settings.SettingsActions
import com.yourname.lumen.ui.settings.SettingsInfo
import com.yourname.lumen.ui.settings.SettingsScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LumenApp(settings: AppSettings) {
    val context = LocalContext.current
    val store = remember { SourceStore(context.applicationContext) }
    val libraryCache = remember { LibraryCache(File(context.filesDir, "library")) }
    val scope = rememberCoroutineScope()

    // Animation lengths (all zero when the user chose Reduced Motion).
    val msSection = motionMs(220)
    val msDock = motionMs(260)
    val msBoot = motionMs(400)

    // ----- connections
    var sources by remember { mutableStateOf(store.load()) }
    val activeSource = sources.firstOrNull { it.id == settings.activeSourceId } ?: sources.firstOrNull()
    // Keyed on the connection details only, so testing a connection never restarts playback.
    val loadKey = activeSource?.copy(lastCheckedAt = 0L, lastError = null)
    val client = remember(loadKey) { activeSource?.let { clientFor(it) } }
    var testingId by remember { mutableStateOf<String?>(null) }

    // ----- content
    var homeState by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var homeFor by remember { mutableStateOf<String?>(null) }
    var liveState by remember { mutableStateOf<LiveState>(LiveState.Loading) }
    var liveKey by remember { mutableStateOf<Pair<Source?, Int>?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var lastRefreshAt by remember { mutableLongStateOf(0L) }
    var imageCacheBytes by remember { mutableLongStateOf(0L) }
    var libraryCacheBytes by remember { mutableLongStateOf(0L) }
    var cacheVersion by remember { mutableIntStateOf(0) }

    // Search: the whole movie and series list, the programme guide, and the index built from them.
    var searchQuery by remember { mutableStateOf("") }
    var library by remember { mutableStateOf<LibraryContent?>(null) }
    var libraryFor by remember { mutableStateOf<String?>(null) }
    var libraryLoading by remember { mutableStateOf(false) }
    var searchIndex by remember { mutableStateOf<SearchIndex?>(null) }
    var programmeDetails by remember { mutableStateOf<ProgrammeHit?>(null) }

    // Home's position is kept here, so leaving Home (details, other sections) and coming back
    // returns to the same scroll position, hero slide and row positions.
    val homeListState = rememberLazyListState()
    val rowStates = remember { List(2) { LazyListState() } }
    var heroIndex by remember { mutableIntStateOf(0) }
    var restoreItemId by remember { mutableStateOf<String?>(null) }

    // Programme information shared by the list, the preview, the fullscreen bar and the guide.
    var epgVersion by remember { mutableIntStateOf(0) }
    val epg = remember(client, settings.epgOffsetMinutes, epgVersion) {
        client?.let { EpgCache(it, settings.epgOffsetMinutes * 60L) }
    }

    // ----- playback: one player for the preview and fullscreen
    val controller = remember { PlaybackController(context.applicationContext) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    var fullscreen by remember { mutableStateOf(false) }
    var fullscreenChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var focusedChannelId by remember {
        mutableStateOf(if (settings.rememberLastChannel) settings.lastChannelId else null)
    }

    // ----- screens
    var section by remember { mutableStateOf(if (settings.startSection == 1) Section.LiveTv else Section.Home) }
    var formType by remember { mutableStateOf<SourceType?>(null) }
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

    // Home: shows the saved copy at once if there is one, then fresh content as it arrives.
    // The screen is only blanked when the source itself changed.
    LaunchedEffect(loadKey, retryKey) {
        if (homeState !is HomeState.Ready || homeFor != activeSource?.id) homeState = HomeState.Loading
        val result = loadHomeState(activeSource, client, libraryCache, retryKey > 0) { homeState = it }
        homeState = result
        homeFor = activeSource?.id
        if (result is HomeState.Ready) lastRefreshAt = System.currentTimeMillis()
    }

    // Start loading the hero pictures the moment their addresses are known, before they are on screen.
    LaunchedEffect(homeState) {
        val ready = homeState as? HomeState.Ready ?: return@LaunchedEffect
        ready.content.hero.take(3).forEach { item ->
            (item.backdropUrl ?: item.posterUrl)?.let { context.imageLoader.enqueue(backdropRequest(context, it)) }
        }
    }

    // The channel list: first open uses the saved copy if it is fresh enough; a refresh asks again.
    LaunchedEffect(section, loadKey, retryKey, homeFor) {
        // Search needs the channel list too. Once Home is on screen the list is also fetched quietly in the
        // background, so Live TV opens with its channels already there.
        val wanted = section == Section.LiveTv || section == Section.Search
        val background = !wanted && homeFor != null && homeFor == activeSource?.id
        if ((wanted || background) && liveKey != (loadKey to retryKey)) {
            if (background) delay(600)
            val force = liveKey != null && liveKey?.second != retryKey
            if (liveState !is LiveState.Ready || liveKey?.first?.id != activeSource?.id) liveState = LiveState.Loading
            liveState = loadLiveState(activeSource, client, libraryCache, force) { liveState = it }
            liveKey = loadKey to retryKey
        }
    }

    // Search data, loaded only once Search is opened: the whole library (saved copy first, then a fresh one
    // if it is missing or over a day old), then the programme guide once the channels are known.
    LaunchedEffect(section, loadKey) {
        val id = activeSource?.id
        val current = client
        if (section != Section.Search || id == null || current == null) return@LaunchedEffect
        if (libraryFor != id) {
            library = null
            libraryFor = id
        }
        if (library != null) return@LaunchedEffect
        libraryLoading = true
        val saved = withContext(Dispatchers.IO) { libraryCache.loadLibrary(id) }
        if (saved != null) library = saved.first
        if (saved == null || saved.second > 24 * 60) {
            try {
                val fresh = current.loadLibrary()
                library = fresh
                withContext(Dispatchers.IO) { runCatching { libraryCache.saveLibrary(id, fresh) } }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.add("Could not load the full library for search")
            }
        }
        libraryLoading = false
    }

    val guideIndex = remember(client, settings.epgOffsetMinutes) { GuideIndex() }
    val liveChannelsForSearch = (liveState as? LiveState.Ready)?.content?.channels
    LaunchedEffect(section, liveChannelsForSearch, guideIndex) {
        val current = client
        val channels = liveChannelsForSearch
        if (section == Section.Search && current != null && !channels.isNullOrEmpty()) {
            guideIndex.ensureLoaded(current, channels, settings.epgOffsetMinutes * 60L)
        }
    }

    // The index is rebuilt off the main thread whenever more data has arrived. Until the full library
    // is in, the newest movies and series from Home are searchable straight away.
    val homeFallback = (homeState as? HomeState.Ready)?.content?.let { LibraryContent(it.movies, it.series) }
    LaunchedEffect(section, library, homeFallback, liveChannelsForSearch, guideIndex.entries) {
        if (section != Section.Search) return@LaunchedEffect
        val lib = library ?: homeFallback
        val channels = liveChannelsForSearch.orEmpty()
        val guide = guideIndex.entries
        searchIndex = withContext(Dispatchers.Default) {
            SearchIndex(lib?.movies.orEmpty(), lib?.series.orEmpty(), channels, guide)
        }
    }

    // Optional automatic refresh while the app stays open.
    LaunchedEffect(settings.autoRefreshHours) {
        val hours = settings.autoRefreshHours
        if (hours > 0) {
            while (true) {
                delay(hours * 3_600_000L)
                epgVersion++
                retryKey++
            }
        }
    }

    // Storage figures for Settings.
    LaunchedEffect(section, cacheVersion) {
        if (section == Section.Settings) {
            imageCacheBytes = context.imageLoader.diskCache?.size ?: 0L
            libraryCacheBytes = withContext(Dispatchers.IO) { libraryCache.sizeBytes() }
        }
    }

    // ----- player housekeeping
    LaunchedEffect(client) {
        // A different connection means different channels: stop whatever was playing.
        controller.stop()
        fullscreen = false
    }
    LaunchedEffect(section, fullscreen) {
        if (section != Section.LiveTv && !fullscreen) controller.stop()
    }
    LaunchedEffect(settings.previewSound, fullscreen) {
        controller.setMuted(!fullscreen && !settings.previewSound)
    }

    // Put focus on the page once it has something to focus, without stealing it from the dock or the player.
    // (A pending "return to this poster" is left alone: Home puts focus there itself.)
    LaunchedEffect(formType, detailsItem, programmeDetails, homeState, liveState, section, fullscreen) {
        val ready = if (section == Section.LiveTv) liveState !is LiveState.Loading else homeState !is HomeState.Loading
        if (formType == null && detailsItem == null && programmeDetails == null && !fullscreen && ready && !dockHasFocus && restoreItemId == null) {
            // If the page has nothing to focus yet, fall back to the dock so the remote is never dead.
            val ok = runCatching { contentFocus.requestFocus() }.isSuccess
            if (!ok) runCatching { dockFocus.requestFocus() }
        }
    }

    // The dock hides while Home is scrolled down and returns at the very top.
    LaunchedEffect(section) {
        if (section != Section.Home) homeAtTop = true
    }
    val dockVisible = !fullscreen && (section != Section.Home || homeAtTop || dockHasFocus || dockForced)

    // Back from content brings the dock back and moves focus to it; Back on the dock leaves the app.
    BackHandler(enabled = !dockHasFocus && formType == null && detailsItem == null && programmeDetails == null && !fullscreen) {
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
    fun playChannel(channel: LiveChannel) {
        val current = client ?: return
        controller.play(channel, settings.preferTs, current.supportsStreamFormats) { ch, ext ->
            current.streamUrl(ch, ext)
        }
        focusedChannelId = channel.id
        if (settings.rememberLastChannel) settings.updateLastChannel(channel.id)
    }

    /** Opens a channel from search: plays it fullscreen with the whole channel list for Up/Down. */
    fun openChannelFromSearch(channel: LiveChannel) {
        val all = (liveState as? LiveState.Ready)?.content?.channels ?: return
        playChannel(channel)
        fullscreenChannels = all
        fullscreen = true
    }

    fun testSource(source: Source) {
        scope.launch {
            testingId = source.id
            val result = clientFor(source).checkConnection()
            val problem = (result as? ConnectionResult.Failed)?.message
            store.update(source.copy(lastCheckedAt = System.currentTimeMillis(), lastError = problem))
            sources = store.load()
            AppLog.add(
                if (problem == null) "Connection to ${source.name} works" else "Connection to ${source.name} failed: $problem",
            )
            testingId = null
            if (problem == null && source.id == activeSource?.id) {
                epgVersion++
                retryKey++
            }
        }
    }

    /** Tests, and when [save] is true also saves, a connection. Returns a message for the user, or null on success. */
    suspend fun submitConnection(type: SourceType, values: Map<String, String>, save: Boolean): String? {
        val existing = editingSource
        val id = existing?.id ?: UUID.randomUUID().toString()
        val name = values["name"].orEmpty().trim()
        val user = values["user"].orEmpty().trim()
        val pass = values["pass"].orEmpty()

        val candidate: Source = when (type) {
            SourceType.XTREAM -> {
                validateServerUrl(values["url"].orEmpty())?.let { return it }
                if (user.isEmpty() || pass.isEmpty()) return "Enter the username and password."
                val url = normalizeServerUrl(values["url"].orEmpty())
                Source(id, name.ifEmpty { hostName(url) }, url, user, pass, type = type)
            }

            SourceType.SELF_HOSTED -> {
                val address = values["url"].orEmpty().trim()
                if (address.isEmpty()) return "Enter the server address."
                val url = buildSelfHostedUrl(address, values["port"].orEmpty().trim(), values["https"] == "1")
                    ?: return "That server address or port isn't valid."
                if (user.isEmpty() || pass.isEmpty()) return "Enter the username and password."
                Source(id, name.ifEmpty { hostName(url) }, url, user, pass, type = type)
            }

            SourceType.M3U -> {
                val file = values["file"].orEmpty()
                val link = values["url"].orEmpty().trim()
                when {
                    file.isNotEmpty() -> Source(
                        id, name.ifEmpty { "Playlist" }, M3uClient.FILE_PREFIX + file, "", "", type = type,
                    )

                    link.isNotEmpty() -> {
                        validatePlaylistLink(link)?.let { return it }
                        Source(id, name.ifEmpty { hostName(link) }, link, "", "", type = type)
                    }

                    else -> return "Enter a playlist link or import a file."
                }
            }
        }

        val result = clientFor(candidate).checkConnection()
        if (result is ConnectionResult.Failed) {
            AppLog.add("Connection to ${candidate.name} failed: ${result.message}")
            return result.message
        }
        if (!save) return null

        val saved = candidate.copy(lastCheckedAt = System.currentTimeMillis())
        if (existing != null) store.update(saved) else store.add(saved)
        sources = store.load()
        if (settings.activeSourceId == null || sources.size == 1) settings.updateActiveSource(saved.id)
        if (existing != null && existing.id == activeSource?.id) {
            epgVersion++
            retryKey++
        }
        AppLog.add("Saved connection ${saved.name}")
        return null
    }

    // ----- layout
    val showVideo = controller.channel != null &&
        (fullscreen || (section == Section.LiveTv && previewBounds != null && formType == null && detailsItem == null))
    val guideDpPerMinute = when (settings.guideDensity) {
        0 -> 3.2f
        2 -> 5.6f
        else -> 4.2f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTheme.colors.background)
            // Ignore the remote while the boot screen is showing.
            .onPreviewKeyEvent { !booted },
    ) {
        // The soft charcoal backdrop. The fullscreen video draws over it, so video is never tinted.
        AppBackground()

        when {
            formType != null -> formType?.let { type ->
                ConnectionFormScreen(
                    type = type,
                    initial = editingSource,
                    onClose = {
                        formType = null
                        editingSource = null
                    },
                    onSubmit = { values, save -> submitConnection(type, values, save) },
                )
            }

            programmeDetails != null -> programmeDetails?.let { hit ->
                ProgrammeDetailsScreen(
                    hit = hit,
                    onOpenChannel = {
                        programmeDetails = null
                        openChannelFromSearch(hit.channel)
                    },
                    onBack = { programmeDetails = null },
                )
            }

            detailsItem != null -> DetailsScreen(
                item = detailsItem!!,
                loadDetails = { item -> client?.loadDetails(item) },
                onBack = { detailsItem = null },
            )

            else -> {
                Crossfade(
                    targetState = section,
                    animationSpec = tween(msSection),
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
                            onOpenDetails = {
                                restoreItemId = null
                                detailsItem = it
                            },
                            onRowOpen = {
                                restoreItemId = it.id
                                detailsItem = it
                            },
                            onAtTopChange = { homeAtTop = it },
                            onAddSource = {
                                editingSource = null
                                formType = SourceType.XTREAM
                            },
                            onRetry = { retryKey++ },
                            listState = homeListState,
                            rowStates = rowStates,
                            heroIndex = heroIndex,
                            onHeroIndex = { heroIndex = it },
                            restoreItemId = restoreItemId,
                            onRestored = { restoreItemId = null },
                        )

                        Section.LiveTv -> LiveTvScreen(
                            state = liveState,
                            sourceId = activeSource?.id ?: "",
                            favorites = settings.favoriteChannels,
                            focusedChannelId = focusedChannelId,
                            playingChannelId = controller.channel?.id,
                            videoHeight = controller.videoHeight,
                            showQuality = settings.showQuality,
                            showLogos = settings.showLogos,
                            initialGroup = settings.liveStartGroup,
                            epg = epg,
                            onFocusChannel = { focusedChannelId = it.id },
                            onToggleFavorite = { channel ->
                                activeSource?.let { settings.toggleFavoriteChannel(favoriteKey(it.id, channel)) }
                            },
                            onSelect = { playChannel(it) },
                            onFullscreen = { channels, index ->
                                val channel = channels[index]
                                if (controller.channel?.id != channel.id) playChannel(channel)
                                fullscreenChannels = channels
                                fullscreen = true
                            },
                            onPreviewBounds = { previewBounds = it },
                            dockFocus = dockFocus,
                            contentFocus = contentFocus,
                            onAddSource = {
                                editingSource = null
                                formType = SourceType.XTREAM
                            },
                            onRetry = { retryKey++ },
                        )

                        Section.Settings -> SettingsScreen(
                            sources = sources,
                            info = SettingsInfo(
                                activeSourceId = activeSource?.id,
                                testingId = testingId,
                                lastRefreshAt = lastRefreshAt,
                                imageCacheBytes = imageCacheBytes,
                                libraryCacheBytes = libraryCacheBytes,
                            ),
                            settings = settings,
                            actions = SettingsActions(
                                onAddSource = { type ->
                                    editingSource = null
                                    formType = type
                                },
                                onEditSource = {
                                    editingSource = it
                                    formType = it.type
                                },
                                onRemoveSource = { removed ->
                                    if (removed.baseUrl.startsWith(M3uClient.FILE_PREFIX)) {
                                        runCatching { File(removed.baseUrl.removePrefix(M3uClient.FILE_PREFIX)).delete() }
                                    }
                                    store.remove(removed.id)
                                    sources = store.load()
                                    if (settings.activeSourceId == removed.id) {
                                        settings.updateActiveSource(sources.firstOrNull()?.id)
                                    }
                                    AppLog.add("Deleted ${removed.name}")
                                },
                                onSetActive = { settings.updateActiveSource(it.id) },
                                onTestSource = { testSource(it) },
                                onRefreshData = {
                                    epgVersion++
                                    retryKey++
                                },
                                onClearImageCache = {
                                    context.imageLoader.memoryCache?.clear()
                                    context.imageLoader.diskCache?.clear()
                                    cacheVersion++
                                    AppLog.add("Image cache cleared")
                                },
                                onClearGuideCache = {
                                    epgVersion++
                                    AppLog.add("Guide data will be loaded again")
                                },
                                onClearLibraryCache = {
                                    libraryCache.clear()
                                    cacheVersion++
                                    AppLog.add("Saved library data cleared")
                                },
                                onRestart = { restartApp(context) },
                            ),
                            dockFocus = dockFocus,
                            contentFocus = contentFocus,
                        )

                        Section.Search -> SearchScreen(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            index = searchIndex,
                            status = SearchStatus(
                                loadingLibrary = libraryLoading,
                                loadingChannels = liveState is LiveState.Loading && activeSource != null,
                                guidePhase = guideIndex.phase,
                                guideMessage = guideIndex.message,
                            ),
                            epg = epg,
                            showRatings = settings.showRatings,
                            onOpenItem = {
                                restoreItemId = null
                                detailsItem = it
                            },
                            onPlayChannel = { openChannelFromSearch(it) },
                            onOpenProgramme = { hit ->
                                if (hit.isNow) openChannelFromSearch(hit.channel) else programmeDetails = hit
                            },
                            dockFocus = dockFocus,
                            contentFocus = contentFocus,
                        )

                        else -> PlaceholderScreen(current)
                    }
                }

                AnimatedVisibility(
                    visible = dockVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(msDock)) + slideInVertically(tween(msDock)) { -it },
                    exit = fadeOut(tween(msDock)) + slideOutVertically(tween(msDock)) { -it },
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

        // The one video surface: a small preview in Live TV, or the whole screen.
        if (showVideo) {
            VideoLayer(
                controller = controller,
                fullscreen = fullscreen,
                previewBounds = previewBounds,
                scaleMode = settings.videoScale,
            )
        }

        // Fullscreen controls: the info bar and the programme guide are two states of one top panel.
        if (fullscreen && controller.channel != null) {
            FullscreenControls(
                controller = controller,
                channels = fullscreenChannels,
                epg = epg,
                showQuality = settings.showQuality,
                showLogos = settings.showLogos,
                overlaySeconds = settings.overlaySeconds,
                guideDpPerMinute = guideDpPerMinute,
                onZap = { playChannel(it) },
                onClose = { fullscreen = false },
            )
        }

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(animationSpec = tween(msBoot)),
        ) {
            BootScreen()
        }
    }
}
