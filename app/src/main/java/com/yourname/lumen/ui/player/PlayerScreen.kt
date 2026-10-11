package com.yourname.lumen.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.designsystem.glass
import com.yourname.lumen.domain.model.LiveChannel
import com.yourname.lumen.domain.model.NowNext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Full-screen live player. Up / Down (or Channel +/-) change channel, OK shows the info bar,
 * Back returns to the channel list. If a stream won't start as HLS it is retried as plain TS,
 * and if that fails too you get a clear message and can press OK to try again.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channels: List<LiveChannel>,
    startIndex: Int,
    streamUrl: (LiveChannel, String) -> String,
    loadNowNext: suspend (LiveChannel) -> NowNext?,
    onChannelChanged: (LiveChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LumenTheme.colors
    val type = LumenTheme.typography

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, channels.lastIndex)) }
    var extension by remember { mutableStateOf("m3u8") }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(true) }
    var nowNext by remember { mutableStateOf<NowNext?>(null) }
    val focus = remember { FocusRequester() }
    val channel = channels[index]

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Lumen/0.1")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) failed = false
            }

            override fun onPlayerError(error: PlaybackException) {
                if (extension == "m3u8") {
                    extension = "ts"
                } else {
                    failed = true
                    buffering = false
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    BackHandler(onBack = onClose)

    // Start (or restart) playback whenever the channel, the stream type or a retry changes.
    LaunchedEffect(index, extension, attempt) {
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(streamUrl(channel, extension)))
        exo.prepare()
        exo.playWhenReady = true
    }

    LaunchedEffect(index) {
        onChannelChanged(channel)
        nowNext = null
        nowNext = try {
            loadNowNext(channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // The info bar shows on a channel change or OK, then hides itself.
    LaunchedEffect(bannerTick) {
        bannerVisible = true
        delay(4_000)
        bannerVisible = false
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun zap(delta: Int) {
        index = (index + delta + channels.size) % channels.size
        extension = "m3u8"
        failed = false
        bannerTick++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            zap(1)
                            true
                        }

                        Key.DirectionDown, Key.ChannelDown -> {
                            zap(-1)
                            true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            if (failed) {
                                failed = false
                                extension = "m3u8"
                                attempt++
                            }
                            bannerTick++
                            true
                        }

                        // Keep focus here instead of letting it wander to the screen underneath.
                        Key.DirectionLeft, Key.DirectionRight -> true

                        else -> false
                    }
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
        )

        if (buffering && !failed) {
            StatusPill(
                text = "Loading…",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (failed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .glass(RoundedCornerShape(16.dp), strong = true)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LumenText("This channel isn't playing right now.", type.heading)
                LumenText(
                    "Press OK to try again, or Up and Down to change channel.",
                    type.body,
                    color = colors.textSecondary,
                )
            }
        }

        AnimatedVisibility(
            visible = bannerVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
        ) {
            InfoBar(channel = channel, nowNext = nowNext)
        }
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    LumenText(
        text = text,
        style = LumenTheme.typography.body,
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun InfoBar(channel: LiveChannel, nowNext: NowNext?) {
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = Dimens.ScreenPadding, vertical = 32.dp)
            .fillMaxWidth()
            .glass(shape, strong = true)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LumenText(
            text = "${channel.number}  ${channel.name}",
            style = type.heading,
            maxLines = 1,
        )
        val now = nowNext?.now
        if (now != null) {
            LumenText(
                text = now.title + timeRange(now.startSec, now.endSec),
                style = type.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            progressOf(now.startSec, now.endSec)?.let { LumenProgressBar(progress = it) }
        } else {
            LumenText("No program information", type.body, color = colors.textSecondary)
        }
        nowNext?.next?.let { next ->
            LumenText(
                text = "Next: ${next.title}" + timeRange(next.startSec, null),
                style = type.label,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun timeRange(startSec: Long?, endSec: Long?): String = when {
    startSec != null && endSec != null -> "   ${clock.format(Date(startSec * 1000))} – ${clock.format(Date(endSec * 1000))}"
    startSec != null -> "   ${clock.format(Date(startSec * 1000))}"
    else -> ""
}

private fun progressOf(startSec: Long?, endSec: Long?): Float? {
    if (startSec == null || endSec == null || endSec <= startSec) return null
    val now = System.currentTimeMillis() / 1000
    return ((now - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
}
