package com.yourname.lumen.data.home

import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.cache.LibraryCache
import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.Source
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FRESH_MINUTES = 20L

/**
 * Builds the Home screen from the active source in stages, calling [onUpdate] as each is ready:
 * 1. the saved copy from the last run, so there is real content immediately;
 * 2. the first fresh results (series and the hero), then the rest;
 * 3. the final content, which is saved for next time.
 * If fresh data can't be loaded but a saved copy was shown, that copy stays on screen.
 */
suspend fun loadHomeState(
    source: Source?,
    client: SourceClient?,
    cache: LibraryCache,
    forceRefresh: Boolean,
    onUpdate: (HomeState) -> Unit,
): HomeState {
    if (source == null || client == null) return HomeState.NoSource

    var shown = false
    val saved = withContext(Dispatchers.IO) { cache.loadHome(source.id) }
    val savedAge = withContext(Dispatchers.IO) { cache.homeAgeMinutes(source.id) }
    // A copy from the last few minutes is as good as new: show it and skip the network completely.
    if (saved != null && !forceRefresh && savedAge != null && savedAge < FRESH_MINUTES &&
        (saved.hero.isNotEmpty() || saved.movies.isNotEmpty() || saved.series.isNotEmpty())
    ) {
        return HomeState.Ready(saved)
    }
    if (saved != null && (saved.hero.isNotEmpty() || saved.movies.isNotEmpty() || saved.series.isNotEmpty())) {
        onUpdate(HomeState.Ready(saved))
        shown = true
    }

    return try {
        val content = client.loadHome { partial ->
            onUpdate(HomeState.Ready(partial))
            shown = true
        }
        withContext(Dispatchers.IO) { runCatching { cache.saveHome(source.id, content) } }
        AppLog.add("Library updated from ${source.name}")
        HomeState.Ready(content)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = describeFailure(e)
        AppLog.add("Could not load the library from ${source.name}: $message")
        // Keep showing the saved copy if there is one; only an empty screen needs the error.
        if (shown && saved != null) HomeState.Ready(saved) else HomeState.Failed(message)
    }
}
