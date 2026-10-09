package com.yourname.lumen.data.home

import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.domain.model.HomeState
import com.yourname.lumen.domain.model.Source
import kotlin.coroutines.cancellation.CancellationException

/** Builds the Home screen from the user's own source. Only the first source is used for now. */
suspend fun loadHomeState(source: Source?): HomeState {
    if (source == null) return HomeState.NoSource
    return try {
        HomeState.Ready(XtreamClient(source).loadHome())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HomeState.Failed
    }
}
