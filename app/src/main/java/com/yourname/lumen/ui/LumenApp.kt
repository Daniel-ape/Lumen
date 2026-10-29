package com.yourname.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.yourname.lumen.core.designsystem.AppBackground
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.motionMs
import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.core.diagnostics.Perf
import com.yourname.lumen.core.progress.LoadTracker
import com.yourname.lumen.ui.common.LoadProgressPopup
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
import com.yourname.lumen.data.settings.RecentChannelsStore
import com.yourname.lumen.data.settings.ResumeStore
import com.yourname.lumen.domain.model.Episode
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
import com.yourname.lumen.domain.model.LiveCategory
import com.yourname.lumen.domain.model.MediaType
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.myListKey
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType
import com.yourname.lumen.domain.model.favoriteKey
import com.yourname.lumen.ui.boot.BootScreen
import com.yourname.lumen.ui.common.PlaceholderScreen
import com.yourname.lumen.ui.details.DetailsScreen
import com.yourname.lumen.ui.home.HomeScreen
import com.yourname.lumen.ui.library.LibraryScreen
import com.yourname.lumen.ui.live.LiveTvScreen
import com.yourname.lumen.ui.navigation.DockSlot
import com.yourname.lumen.ui.navigation.LumenDock
import com.yourname.lumen.ui.navigation.Section
import com.yourname.lumen.ui.player.FullscreenControls
import com.yourname.lumen.ui.player.PlaybackController
import com.yourname.lumen.ui.player.VodControls
import com.yourname.lumen.ui.player.VodSession
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
    // What is loading after a source is added or refreshed, for the progress pop-up in the corner.
    val tracker = remember { LoadTracker() }
    // The pop-up hides itself a few seconds after everything has finished, and never stays longer than two minutes.
    LaunchedEffect(tracker.session, tracker.pending) {
        if (tracker.visible && !tracker.pending) {
            delay(5_000)
            tracker.hide()
        }
    }
    LaunchedEffect(tracker.session) {
        delay(120_000)
        tracker.hide()
    }
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

    // Movies and Series screens: their categories, and where you were (category, sort, scroll).
    var movieCategories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }
    var seriesCategories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }
    var categoriesFor by remember { mutableStateOf<String?>(null) }
    val movieGrid = rememberLazyGridState()
    val seriesGrid = rememberLazyGridState()
    var movieCategory by remember { mutableIntStateOf(0) }
    var seriesCategory by remember { mutableIntStateOf(0) }
    var movieSort by remember { mutableIntStateOf(0) }
    var seriesSort by remember { mutableIntStateOf(0) }

    // Home's position is kept here, so leaving Home (details, other sections) and coming back
    // returns to the same scroll position, hero slide and row positions.
    val homeListState = rememberLazyListState()
    val rowStates = remember { List(3) { LazyListState() } }
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

    // Live TV keeps its place: the category and list position survive leaving the screen, and a counter
    // tells the list when fullscreen has just ended so it can scroll back to the channel that was playing.
    // Channels the user has really watched (after five seconds on a channel), newest first, saved per connection.
    val recentStore = remember { RecentChannelsStore(context.applicationContext) }
    // Where you stopped in each movie and episode, so Play can continue from there.
    val resumeStore = remember { ResumeStore(context.applicationContext) }
    var recents by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var lastRecentId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeSource?.id) {
        recents = activeSource?.id?.let { recentStore.load(it) }.orEmpty()
    }
    LaunchedEffect(controller.channel?.id) {
        val watching = controller.channel ?: return@LaunchedEffect
        delay(5_000)
        if (controller.channel?.id == watching.id) {
            activeSource?.id?.let { recents = recentStore.record(it, watching) }
        }
    }

    var liveGroup by remember { mutableIntStateOf(settings.liveStartGroup) }
    var liveFirstIndex by remember { mutableIntStateOf(0) }
    var fullscreenExits by remember { mutableIntStateOf(0) }
    var wasFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) {
        if (wasFullscreen && !fullscreen) fullscreenExits++
        wasFullscreen = fullscreen
    }
    var formType by remember { mutableStateOf<SourceType?>(null) }
    var editingSource by remember { mutableStateOf<Source?>(null) }
    var detailsItem by remember { mutableStateOf<MediaItem?>(null) }
    // Titles you came through by opening "similar" ones, so Back goes to the previous page, not out of details.
    val detailsHistory = remember { mutableStateListOf<MediaItem>() }

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

    // While a movie or episode plays, where you are is saved every few seconds, and again when it stops.
    LaunchedEffect(controller.vod) {
        val session = controller.vod ?: return@LaunchedEffect
        try {
            while (true) {
                delay(5_000)
                resumeStore.save(session.resumeKey, controller.exo.currentPosition, controller.exo.duration)
            }
        } finally {
            resumeStore.save(session.resumeKey, controller.exo.currentPosition, controller.exo.duration)
        }
    }
    // When it ends: forget the resume point, and carry on with the next episode if there is one.
    LaunchedEffect(controller.vodEnded) {
        if (controller.vodEnded) {
            val finished = controller.vod
            if (finished != null) {
                resumeStore.clear(finished.resumeKey)
                val next = finished.next?.invoke()
                if (next != null) controller.playVod(next) else controller.stop()
            }
        }
    }

    // Moments measured from opening the app.
    LaunchedEffect(booted) { if (booted) Perf.milestone("Boot screen finished") }
    LaunchedEffect(homeState) {
        if (homeState is HomeState.Ready) Perf.milestone("Home content on screen")
    }
    LaunchedEffect(liveState) {
        if (liveState is LiveState.Ready) Perf.milestone("Channel list ready")
    }

    // Home: shows the saved copy at once if there is one, then fresh content as it arrives.
    // The screen is only blanked when the source itself changed.
    LaunchedEffect(loadKey, retryKey) {
        val sourceId = activeSource?.id
        if (homeState !is HomeState.Ready || homeFor != sourceId) homeState = HomeState.Loading
        // A source loaded for the very first time shows the progress pop-up too (adding one starts it already).
        if (sourceId != null && !tracker.visible &&
            withContext(Dispatchers.IO) { libraryCache.homeAgeMinutes(sourceId) } == null
        ) {
            tracker.begin()
            tracker.connection.done(message = "Connected")
        }
        if (sourceId != null) tracker.home.running()
        val result = Perf.time("Home: saved copy and source") { loadHomeState(activeSource, client, libraryCache, retryKey > 0) { update ->
            homeState = update
            (update as? HomeState.Ready)?.let { tracker.home.progress(it.content.movies.size + it.content.series.size) }
        } }
        homeState = result
        homeFor = sourceId
        when (result) {
            is HomeState.Ready -> {
                lastRefreshAt = System.currentTimeMillis()
                tracker.home.done(result.content.movies.size + result.content.series.size)
            }
            is HomeState.Failed -> tracker.home.fail(result.message)
            else -> Unit
        }
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
            tracker.channels.running()
            liveState = Perf.time("Channel list", { (it as? LiveState.Ready)?.let { r -> "%,d channels".format(r.content.channels.size) } }) {
                loadLiveState(activeSource, client, libraryCache, force, { tracker.channels.progress(it) }) { liveState = it }
            }
            when (val result = liveState) {
                is LiveState.Ready -> tracker.channels.done(result.content.channels.size)
                is LiveState.Failed -> tracker.channels.fail(result.message)
                else -> Unit
            }
            liveKey = loadKey to retryKey
        }
    }

    // The full movie and series list. It is big, so it loads in the background after Home is already on
    // screen (or at once if Movies, Series or Search is opened first), and the download is never cancelled by
    // moving between screens. A saved copy is shown first; a fresh one is fetched if it is missing or old.
    fun ensureLibrary() {
        val id = activeSource?.id ?: return
        val current = client ?: return
        if (libraryFor == id && (libraryLoading || library != null)) return
        libraryFor = id
        library = null
        libraryLoading = true
        scope.launch {
            try {
                val saved = Perf.time("Saved library read", { it?.let { s -> "%,d movies, %,d series".format(s.first.movies.size, s.first.series.size) } }) {
                    withContext(Dispatchers.IO) { libraryCache.loadLibrary(id) }
                }
                if (saved != null && libraryFor == id) library = saved.first
                saved?.let {
                    tracker.movies.done(it.first.movies.size)
                    tracker.series.done(it.first.series.size)
                }
                // A copy saved before categories existed has none; it is replaced with a fresh one.
                val hasCategories = saved != null &&
                    (saved.first.movies.any { it.categoryId != null } || saved.first.series.any { it.categoryId != null })
                if (saved == null || saved.second > 24 * 60 || !hasCategories) {
                    try {
                        tracker.movies.running()
                        tracker.series.running()
                        val fresh = Perf.time("Full library download", { "%,d movies, %,d series".format(it.movies.size, it.series.size) }) {
                            current.loadLibrary({ tracker.movies.progress(it) }, { tracker.series.progress(it) })
                        }
                        tracker.movies.done(fresh.movies.size, if (fresh.movies.isEmpty()) "None found" else null)
                        tracker.series.done(fresh.series.size, if (fresh.series.isEmpty()) "None found" else null)
                        if (libraryFor == id) library = fresh
                        withContext(Dispatchers.IO) { runCatching { libraryCache.saveLibrary(id, fresh) } }
                        // Now that the true newest titles are known, Home's rows are refreshed from them: saved
                        // for next time, and shown now unless you are looking at Home (rows must not move under you).
                        val ready = homeState as? HomeState.Ready
                        val newestMovies = fresh.movies.filter { it.posterUrl != null }.take(20)
                        val newestSeries = fresh.series.filter { it.posterUrl != null }.take(20)
                        if (ready != null && libraryFor == id && (newestMovies.isNotEmpty() || newestSeries.isNotEmpty())) {
                            val updated = ready.content.copy(
                                movies = newestMovies.ifEmpty { ready.content.movies },
                                series = newestSeries.ifEmpty { ready.content.series },
                            )
                            withContext(Dispatchers.IO) { runCatching { libraryCache.saveHome(id, updated) } }
                            if (section != Section.Home) homeState = HomeState.Ready(updated)
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        tracker.movies.fail(e.message ?: "error")
                        tracker.series.fail(e.message ?: "error")
                        AppLog.add("Could not load the full library: ${e.message ?: "unknown error"}")
                    }
                }
            } finally {
                if (libraryFor == id) libraryLoading = false
            }
        }
    }

    LaunchedEffect(section, loadKey, homeFor) {
        val wanted = section == Section.Search || section == Section.Movies || section == Section.Series
        val background = homeFor != null && homeFor == activeSource?.id
        if (wanted) {
            ensureLibrary()
        } else if (background) {
            delay(2_500)
            ensureLibrary()
        }
    }

    LaunchedEffect(section, loadKey) {
        val id = activeSource?.id
        val current = client
        if ((section == Section.Movies || section == Section.Series) && id != null && current != null && categoriesFor != id) {
            try {
                movieCategories = current.loadCategories(MediaType.Movie)
                seriesCategories = current.loadCategories(MediaType.Series)
                categoriesFor = id
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.add("Could not load movie and series categories")
            }
        }
    }

    // If a "return to this poster" never finds its poster (for example the list changed), give up after a
    // moment and put focus back on the page, so the remote never ends up with nothing selected.
    LaunchedEffect(restoreItemId) {
        if (restoreItemId != null) {
            delay(1_500)
            if (restoreItemId != null) {
                restoreItemId = null
                runCatching { contentFocus.requestFocus() }
            }
        }
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
        searchIndex = Perf.time("Search index build") {
            withContext(Dispatchers.Default) {
                SearchIndex(lib?.movies.orEmpty(), lib?.series.orEmpty(), channels, guide)
            }
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
        if (section != Section.LiveTv && !fullscreen && controller.vod == null) controller.stop()
    }
    LaunchedEffect(settings.previewSound, fullscreen, controller.vod) {
        controller.setMuted(controller.vod == null && !fullscreen && !settings.previewSound)
    }

    // Put focus on the page once it has something to focus, without stealing it from the dock or the player.
    // (A pending "return to this poster" is left alone: Home puts focus there itself.)
    LaunchedEffect(formType, detailsItem, programmeDetails, homeState, liveState, section, fullscreen) {
        val ready = if (section == Section.LiveTv) liveState !is LiveState.Loading else homeState !is HomeState.Loading
        if (formType == null && detailsItem == null && programmeDetails == null && !fullscreen && controller.vod == null && ready && !dockHasFocus && restoreItemId == null) {
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

    // Moving across the dock opens each page after a short pause, so sweeping past items does not load them.
    // The pause is cancelled the moment focus leaves the dock.
    var dockFocusTarget by remember { mutableStateOf<Section?>(null) }
    LaunchedEffect(dockFocusTarget, dockHasFocus) {
        val target = dockFocusTarget
        if (dockHasFocus && target != null && target != section && settings.dockSwitchOnFocus) {
            delay(320)
            section = target
        }
    }
    LaunchedEffect(dockHasFocus) {
        if (!dockHasFocus) dockFocusTarget = null
    }

    // Back on the menu of any other page goes to Home first; Back on Home's menu leaves the app.
    BackHandler(enabled = dockHasFocus && section != Section.Home && formType == null && detailsItem == null && programmeDetails == null && !fullscreen) {
        section = Section.Home
    }

    // Back from content brings the dock back and moves focus to it; Back on the dock leaves the app.
    BackHandler(enabled = !dockHasFocus && formType == null && detailsItem == null && programmeDetails == null && !fullscreen && controller.vod == null) {
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

    fun startVod(session: VodSession) {
        if (session.url.isEmpty()) return
        // Live preview (if any) gives way to the movie or episode.
        fullscreen = false
        controller.playVod(session)
    }

    /** One episode as something playable; when it ends, the next episode of the list plays by itself. */
    fun episodeSession(item: MediaItem, ep: Episode, all: List<Episode>, startMs: Long): VodSession? {
        val current = client ?: return null
        val sourceKey = activeSource?.id.orEmpty()
        val index = all.indexOfFirst { it.id == ep.id }
        return VodSession(
            title = item.title,
            subtitle = "S${ep.season} E${ep.number} · ${ep.title}",
            url = current.episodeUrl(ep.id, ep.extension),
            resumeKey = "$sourceKey|ep:${ep.id}",
            startMs = startMs,
            next = { all.getOrNull(index + 1)?.let { episodeSession(item, it, all, 0L) } },
        )
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
        // The pop-up in the corner follows everything that loads from this connection.
        tracker.begin()
        tracker.connection.done(message = "Connected")
        if (settings.activeSourceId == null || sources.size == 1) settings.updateActiveSource(saved.id)
        if (existing != null && existing.id == activeSource?.id) {
            epgVersion++
            retryKey++
        }
        AppLog.add("Saved connection ${saved.name}")
        return null
    }

    // ----- layout
    // A movie or episode always plays fullscreen; a live channel is either the preview or fullscreen.
    val vodActive = controller.vod != null
    val showVideo = vodActive || (controller.channel != null &&
        (fullscreen || (section == Section.LiveTv && previewBounds != null && formType == null && detailsItem == null)))
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

            detailsItem != null -> detailsItem?.let { item ->
                val similar = remember(item, library, homeFallback) {
                    val pool = (library ?: homeFallback)?.let { if (item.type == MediaType.Movie) it.movies else it.series }.orEmpty()
                    val category = item.categoryId
                    val genres = item.genre?.lowercase()?.split(',', '/', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    val year = item.year?.toIntOrNull()
                    // Points: same category, each shared genre, and being released close in time. Needs at least a
                    // category or genre match, so nothing unrelated is offered.
                    pool.asSequence()
                        .filter { it.id != item.id }
                        .mapNotNull { other ->
                            var score = 0
                            if (category != null && other.categoryId == category) score += 3
                            if (genres.isNotEmpty()) {
                                val theirs = other.genre?.lowercase()?.split(',', '/', '|')?.map { it.trim() }.orEmpty()
                                score += 2 * genres.count { it in theirs }
                            }
                            if (score == 0) return@mapNotNull null
                            val otherYear = other.year?.toIntOrNull()
                            if (year != null && otherYear != null) score += (2 - minOf(2, kotlin.math.abs(year - otherYear) / 4))
                            other to score
                        }
                        .sortedByDescending { it.second }
                        .take(24)
                        .map { it.first }
                        .toList()
                }
                DetailsScreen(
                    item = item,
                    loadDetails = { client?.loadDetails(it) },
                    inMyList = activeSource?.let { myListKey(it.id, item) in settings.myList } ?: false,
                    onToggleMyList = { activeSource?.let { settings.toggleMyList(myListKey(it.id, item)) } },
                    similar = similar,
                    resumeMs = { key -> resumeStore.position("${activeSource?.id.orEmpty()}|$key") },
                    onPlayMovie = { details, startMs ->
                        client?.let {
                            startVod(
                                VodSession(
                                    title = item.title,
                                    subtitle = null,
                                    url = it.vodUrl(item.id, details.streamExtension ?: "mp4"),
                                    resumeKey = "${activeSource?.id.orEmpty()}|movie:${item.id}",
                                    startMs = startMs,
                                ),
                            )
                        }
                    },
                    onPlayEpisode = { ep, all, startMs ->
                        episodeSession(item, ep, all, startMs)?.let { startVod(it) }
                    },
                    onOpenSimilar = { next ->
                        detailsHistory.add(item)
                        detailsItem = next
                    },
                    onBack = { detailsItem = detailsHistory.removeLastOrNull() },
                )
            }

            else -> {
                // Only one screen exists at a time, and it fades in. (A cross-fade kept the old screen alive
                // for a moment too, and the remote could land on that invisible old screen.)
                val fade = remember(section) { Animatable(0f) }
                LaunchedEffect(section) { fade.animateTo(1f, tween(msSection)) }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
                    val current = section
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
                            recentChannels = recents,
                            recentFocusId = lastRecentId,
                            exitSignal = fullscreenExits,
                            onPlayRecent = { channel ->
                                lastRecentId = channel.id
                                val all = (liveState as? LiveState.Ready)?.content?.channels ?: recents
                                playChannel(channel)
                                fullscreenChannels = all
                                fullscreen = true
                            },
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
                            initialGroup = liveGroup,
                            initialFirstIndex = liveFirstIndex,
                            exitSignal = fullscreenExits,
                            onGroupChange = {
                                liveGroup = it
                                liveFirstIndex = 0
                            },
                            onScrollIndex = { liveFirstIndex = it },
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
                                    tracker.begin()
                                    tracker.connection.done(message = "Connected")
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

                        Section.Movies, Section.Series -> {
                            val isMovies = current == Section.Movies
                            val lib = library ?: homeFallback
                            LibraryScreen(
                                kind = if (isMovies) MediaType.Movie else MediaType.Series,
                                items = (if (isMovies) lib?.movies else lib?.series).orEmpty(),
                                categories = if (isMovies) movieCategories else seriesCategories,
                                loading = libraryLoading,
                                hasSource = activeSource != null,
                                sourceHasLibrary = activeSource?.type != SourceType.M3U,
                                showRatings = settings.showRatings,
                                gridState = if (isMovies) movieGrid else seriesGrid,
                                categoryIndex = if (isMovies) movieCategory else seriesCategory,
                                onCategoryIndex = { if (isMovies) movieCategory = it else seriesCategory = it },
                                sortMode = if (isMovies) movieSort else seriesSort,
                                onSortMode = { if (isMovies) movieSort = it else seriesSort = it },
                                restoreItemId = restoreItemId,
                                onRestored = { restoreItemId = null },
                                onOpen = {
                                    restoreItemId = it.id
                                    detailsItem = it
                                },
                                onAddSource = {
                                    editingSource = null
                                    formType = SourceType.XTREAM
                                },
                                dockFocus = dockFocus,
                                contentFocus = contentFocus,
                            )
                        }

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
                        onItemFocused = { dockFocusTarget = it },
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
                fullscreen = fullscreen || vodActive,
                previewBounds = previewBounds,
                scaleMode = settings.videoScale,
            )
        }

        // Movie and episode controls (skip, pause, progress).
        if (vodActive) {
            VodControls(
                controller = controller,
                onClose = {
                    // Save exactly where you stopped before the player is cleared.
                    controller.vod?.let { resumeStore.save(it.resumeKey, controller.exo.currentPosition, controller.exo.duration) }
                    controller.stop()
                },
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

        LoadProgressPopup(
            tracker = tracker,
            show = !fullscreen && controller.vod == null && booted,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp),
        )

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(animationSpec = tween(msBoot)),
        ) {
            BootScreen()
        }
    }
}
