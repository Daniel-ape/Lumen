package com.yourname.lumen.data.epg

import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.NowNext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Remembers what's on right now for channels the user has seen, so scrolling back doesn't ask the
 * server again. At most three requests run at once, which keeps long lists gentle on the server.
 * Only real data from the source is kept; a failed or empty answer is simply "no information".
 */
class EpgCache(
    private val client: SourceClient,
    private val offsetSec: Long,
) {
    private class Entry(val value: NowNext?, val fetchedAt: Long)

    private val entries = HashMap<String, Entry>()
    private val gate = Semaphore(3)

    /** Instant answer for the UI: the last known value, or null if none or if it has run out. */
    fun peek(channel: LiveChannel): NowNext? {
        val entry = entries[channel.id] ?: return null
        return if (isFresh(entry)) entry.value else null
    }

    suspend fun nowNext(channel: LiveChannel): NowNext? {
        entries[channel.id]?.let { if (isFresh(it)) return it.value }
        return gate.withPermit {
            // Another row may have filled it in while we waited.
            entries[channel.id]?.let { if (isFresh(it)) return@withPermit it.value }
            val value = try {
                client.shortEpg(channel, offsetSec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            entries[channel.id] = Entry(value, System.currentTimeMillis())
            value
        }
    }

    suspend fun schedule(channel: LiveChannel) = client.schedule(channel, offsetSec)

    fun clear() = entries.clear()

    private fun isFresh(entry: Entry): Boolean {
        val now = System.currentTimeMillis()
        if (now - entry.fetchedAt > MAX_AGE_MS) return false
        val end = entry.value?.now?.endSec ?: return true
        return end > now / 1000
    }

    private companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
