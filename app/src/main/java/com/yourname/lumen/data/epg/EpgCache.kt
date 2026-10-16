package com.yourname.lumen.data.epg

import com.yourname.lumen.data.source.SourceClient
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.ProgramInfo
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

    private class ScheduleEntry(val value: List<ProgramInfo>, val fetchedAt: Long)

    private val schedules = HashMap<String, ScheduleEntry>()

    /** Last known schedule for a channel, or null if none yet or if it is old. */
    fun peekSchedule(channel: LiveChannel): List<ProgramInfo>? {
        val entry = schedules[channel.id] ?: return null
        return if (System.currentTimeMillis() - entry.fetchedAt < SCHEDULE_MAX_AGE_MS) entry.value else null
    }

    /** A channel's upcoming programmes. Failed or empty answers become an empty list: no information. */
    suspend fun schedule(channel: LiveChannel): List<ProgramInfo> {
        peekSchedule(channel)?.let { return it }
        return gate.withPermit {
            peekSchedule(channel)?.let { return@withPermit it }
            val value = try {
                client.schedule(channel, offsetSec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            schedules[channel.id] = ScheduleEntry(value, System.currentTimeMillis())
            value
        }
    }

    fun clear() {
        entries.clear()
        schedules.clear()
    }

    private fun isFresh(entry: Entry): Boolean {
        val now = System.currentTimeMillis()
        if (now - entry.fetchedAt > MAX_AGE_MS) return false
        val end = entry.value?.now?.endSec ?: return true
        return end > now / 1000
    }

    private companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
        const val SCHEDULE_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
