package com.yourname.lumen.data.epg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.data.xtream.describeFailure
import com.yourname.lumen.domain.model.GuideEntry
import com.yourname.lumen.domain.model.LiveChannel
import kotlin.coroutines.cancellation.CancellationException

/**
 * The programme guide for the next day or so, loaded once from the source and kept in memory so
 * programme search is instant. It holds only what the source provides; if the source has no guide
 * file, [phase] says so and search simply has no programme results.
 */
class GuideIndex {
    enum class Phase { Idle, Loading, Ready, Unavailable }

    var phase by mutableStateOf(Phase.Idle)
        private set
    var entries by mutableStateOf<List<GuideEntry>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var loadedAt = 0L

    suspend fun ensureLoaded(client: SourceClient, channels: List<LiveChannel>, offsetSec: Long) {
        val now = System.currentTimeMillis()
        when (phase) {
            Phase.Loading -> return
            Phase.Ready -> if (now - loadedAt < READY_MS) return
            Phase.Unavailable -> if (now - loadedAt < RETRY_MS) return
            Phase.Idle -> Unit
        }
        phase = Phase.Loading
        try {
            val result = client.loadGuide(channels, offsetSec)
            if (result.isNullOrEmpty()) {
                message = "This source has no programme guide to search."
                phase = Phase.Unavailable
            } else {
                entries = result
                message = null
                phase = Phase.Ready
            }
        } catch (e: CancellationException) {
            phase = Phase.Idle
            throw e
        } catch (e: Exception) {
            message = describeFailure(e)
            phase = Phase.Unavailable
        }
        loadedAt = System.currentTimeMillis()
    }

    private companion object {
        const val READY_MS = 3 * 60 * 60 * 1000L
        const val RETRY_MS = 15 * 60 * 1000L
    }
}
