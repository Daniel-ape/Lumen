package com.yourname.lumen.data.home

import com.yourname.lumen.core.diagnostics.AppLog
import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.Source
import kotlin.coroutines.cancellation.CancellationException

/** Builds the Home screen from the active source. */
suspend fun loadHomeState(source: Source?): HomeState {
    if (source == null) return HomeState.NoSource
    return try {
        val content = XtreamClient(source).loadHome()
        AppLog.add("Library updated from ${source.name}")
        HomeState.Ready(content)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = describeFailure(e)
        AppLog.add("Could not load the library from ${source.name}: $message")
        HomeState.Failed(message)
    }
}
