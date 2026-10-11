package com.yourname.lumen.data.live

import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.LiveState
import com.yourname.lumen.domain.model.Source
import kotlin.coroutines.cancellation.CancellationException

/** Loads the channel list and groups from the active source. */
suspend fun loadLiveState(source: Source?): LiveState {
    if (source == null) return LiveState.NoSource
    return try {
        val content = XtreamClient(source).loadLive()
        AppLog.add("Loaded ${content.channels.size} channels from ${source.name}")
        LiveState.Ready(content)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = describeFailure(e)
        AppLog.add("Could not load channels from ${source.name}: $message")
        LiveState.Failed(message)
    }
}
