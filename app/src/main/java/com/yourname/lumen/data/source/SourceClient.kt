package com.yourname.lumen.data.source

import com.yourname.lumen.data.m3u.M3uClient
import com.yourname.lumen.data.xtream.XtreamClient
import com.yourname.lumen.domain.model.ConnectionResult
import com.yourname.lumen.domain.model.HomeContent
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.LiveContent
import com.yourname.lumen.domain.model.MediaDetails
import com.yourname.lumen.domain.model.MediaItem
import com.yourname.lumen.domain.model.NowNext
import com.yourname.lumen.domain.model.ProgramInfo
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType

/**
 * What the app needs from a connection, whatever kind it is. Xtream Codes (including self-hosted
 * Xtream-compatible servers) and M3U playlists both implement this, so the screens don't care.
 */
interface SourceClient {
    suspend fun checkConnection(): ConnectionResult
    suspend fun loadHome(): HomeContent
    suspend fun loadLive(): LiveContent
    suspend fun loadDetails(item: MediaItem): MediaDetails?

    /** Current and next programme. [offsetSec] shifts the times for sources whose clock is off. */
    suspend fun shortEpg(channel: LiveChannel, offsetSec: Long): NowNext
    suspend fun schedule(channel: LiveChannel, offsetSec: Long): List<ProgramInfo>

    /** Address to play. Contains the login for some sources, so never log it. */
    fun streamUrl(channel: LiveChannel, extension: String): String

    /** True when the stream may be offered as HLS first and TS second (only Xtream-style sources). */
    val supportsStreamFormats: Boolean
}

fun clientFor(source: Source): SourceClient = when (source.type) {
    SourceType.M3U -> M3uClient(source)
    SourceType.XTREAM, SourceType.SELF_HOSTED -> XtreamClient(source)
}
