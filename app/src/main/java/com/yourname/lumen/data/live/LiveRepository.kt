package com.yourname.lumen.data.live

import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.cache.LibraryCache
import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.Source
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FRESH_MINUTES = 10L

/**
 * Loads the channel list. A saved copy under ten minutes old is used as it is; an older one is shown
 * straight away and refreshed in the background. [forceRefresh] always asks the source again.
 */
suspend fun loadLiveState(
    source: Source?,
    client: SourceClient?,
    cache: LibraryCache,
    forceRefresh: Boolean,
    onUpdate: (LiveState) -> Unit,
): LiveState {
    if (source == null || client == null) return LiveState.NoSource

    val saved = withContext(Dispatchers.IO) { cache.loadLive(source.id) }
    if (saved != null && saved.first.channels.isNotEmpty()) {
        val ready = LiveState.Ready(saved.first)
        if (!forceRefresh && saved.second < FRESH_MINUTES) return ready
        onUpdate(ready)
    }

    return try {
        val content = client.loadLive()
        withContext(Dispatchers.IO) { runCatching { cache.saveLive(source.id, content) } }
        AppLog.add("Loaded ${content.channels.size} channels from ${source.name}")
        LiveState.Ready(content)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = describeFailure(e)
        AppLog.add("Could not load channels from ${source.name}: $message")
        if (saved != null && saved.first.channels.isNotEmpty()) LiveState.Ready(saved.first) else LiveState.Failed(message)
    }
}
