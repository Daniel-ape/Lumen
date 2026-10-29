package com.yourname.lumen.ui.player

import com.yourname.lumen.core.designsystem.LumenButton
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.Dimens
import com.yourname.lumen.core.designsystem.LumenProgressBar
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L

/**
 * Controls for a movie or episode, drawn over the fullscreen video.
 * LEFT / RIGHT skip 10 seconds, OK (or the play/pause key) pauses and resumes, any key shows the bar,
 * and Back leaves the player. The bar hides by itself while playing.
 */
@Composable
fun VodControls(controller: PlaybackController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val session = controller.vod ?: return
    val colors = LumenTheme.colors
    val type = LumenTheme.typography
    val exo = controller.exo
    val root = remember { FocusRequester() }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(true) }
    var touched by remember { mutableLongStateOf(0L) }
    var panelOpen by remember { mutableStateOf(false) }
    // Whether the file offers any audio or subtitle choice (known once playback has started).
    var hasChoices by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    // Take focus so the remote reaches the controls; a few quick tries as the surface may not be ready at once.
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { root.requestFocus() }
            delay(40)
        }
    }
    // The player's clock, read twice a second (cheap, and the bar only redraws when the numbers change).
    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition.coerceAtLeast(0L)
            duration = exo.duration.takeIf { it > 0 } ?: 0L
            paused = !exo.playWhenReady
            val groups = exo.currentTracks.groups
            hasChoices = groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length } > 1 ||
                groups.any { it.type == C.TRACK_TYPE_TEXT }
            delay(500)
        }
    }
    // Hide the bar a few seconds after the last key press, but never while paused or on an error.
    LaunchedEffect(touched, paused, controller.failed) {
        barVisible = true
        if (!paused && !controller.failed) {
            delay(4_000)
            barVisible = false
        }
    }

    fun show() {
        touched = System.currentTimeMillis()
    }

    Box(modifier = modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (panelOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.MediaRewind -> {
                        exo.seekTo((exo.currentPosition - SEEK_MS).coerceAtLeast(0L))
                        show()
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val limit = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
                        exo.seekTo((exo.currentPosition + SEEK_MS).coerceAtMost(limit))
                        show()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (controller.failed) {
                            controller.retry()
                        } else {
                            exo.playWhenReady = !exo.playWhenReady
                        }
                        show()
                        true
                    }
                    Key.MediaPlay -> {
                        exo.playWhenReady = true
                        show()
                        true
                    }
                    Key.MediaPause -> {
                        exo.playWhenReady = false
                        show()
                        true
                    }
                    Key.DirectionUp -> {
                        // UP opens the audio and subtitle choices, when the file has any.
                        if (hasChoices) panelOpen = true else show()
                        true
                    }
                    Key.DirectionDown -> {
                        show()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Title at the top, progress at the bottom, each on a soft shade so they read over any picture.
        AnimatedVisibility(
            visible = barVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(260)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 22.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LumenText(session.title, type.title.copy(fontSize = 20.sp), maxLines = 1)
                session.subtitle?.let { LumenText(it, type.label.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.8f), maxLines = 1) }
            }
        }
        AnimatedVisibility(
            visible = barVisible,
            modifier = Modifier.align(Alignment.BottomStart),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(260)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 40.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LumenProgressBar(progress = if (duration > 0) position.toFloat() / duration else 0f)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    LumenText(
                        text = if (paused) "Paused" else "Playing",
                        style = type.label.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                    LumenText(
                        text = clock(position) + if (duration > 0) " / " + clock(duration) else "",
                        style = type.label.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                }
                LumenText(
                    text = "Left / right: skip 10 seconds     OK: pause or play     " +
                        (if (hasChoices) "Up: audio and subtitles     " else "") + "Back: exit",
                    style = type.label.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }
        }

        if (controller.failed) {
            Message("This couldn't be played. Press OK to try again, or Back to return.")
        } else if (controller.buffering) {
            Message("Buffering…")
        }
    }

    if (panelOpen) {
        TrackPanel(
            exo = exo,
            onClose = {
                panelOpen = false
                runCatching { root.requestFocus() }
            },
        )
    }
    }
}

private class TrackOption(val label: String, val selected: Boolean, val apply: () -> Unit)

private fun labelOf(format: Format, index: Int): String {
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { java.util.Locale(it).displayLanguage.takeIf { name -> name.isNotBlank() } }
    val base = format.label?.takeIf { it.isNotBlank() } ?: language ?: "Track ${index + 1}"
    val channels = when (format.channelCount) {
        6 -> " 5.1"
        8 -> " 7.1"
        else -> ""
    }
    return base + channels
}

/** The audio languages and subtitles the file carries, taken from the player itself. Nothing is made up. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TrackPanel(exo: androidx.media3.exoplayer.ExoPlayer, onClose: () -> Unit) {
    val type = LumenTheme.typography
    val colors = LumenTheme.colors
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { first.requestFocus() }
            delay(40)
        }
    }

    val tracks = exo.currentTracks
    val textDisabled = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val audio = buildList {
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                add(
                    TrackOption(labelOf(g.getTrackFormat(i), size), g.isTrackSelected(i)) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                            .build()
                    },
                )
            }
        }
    }
    val subtitles = buildList {
        val anySelected = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        add(
            TrackOption("Off", textDisabled || !anySelected) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            },
        )
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                add(
                    TrackOption(labelOf(g.getTrackFormat(i), size - 1), !textDisabled && g.isTrackSelected(i)) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                            .build()
                    },
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0x99141824), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .padding(18.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (audio.size > 1) {
                LumenText("Audio", type.heading.copy(fontSize = 15.sp))
                audio.forEachIndexed { i, option ->
                    LumenButton(
                        text = (if (option.selected) "✓  " else "     ") + option.label,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        onClick = {
                            option.apply()
                            onClose()
                        },
                    )
                }
            }
            if (subtitles.size > 1) {
                LumenText("Subtitles", type.heading.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp))
                subtitles.forEachIndexed { i, option ->
                    LumenButton(
                        text = (if (option.selected) "✓  " else "     ") + option.label,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (audio.size <= 1 && i == 0) Modifier.focusRequester(first) else Modifier),
                        onClick = {
                            option.apply()
                            onClose()
                        },
                    )
                }
            }
            if (audio.size <= 1 && subtitles.size <= 1) {
                LumenText("This file has no other audio or subtitle tracks.", type.label, color = colors.textSecondary)
                LumenButton(text = "Close", compact = true, modifier = Modifier.fillMaxWidth().focusRequester(first), onClick = onClose)
            }
        }
    }
}

@Composable
private fun BoxScope.Message(text: String) {
    LumenText(
        text = text,
        style = LumenTheme.typography.body.copy(fontSize = 15.sp),
        modifier = Modifier
            .align(Alignment.Center)
            .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
