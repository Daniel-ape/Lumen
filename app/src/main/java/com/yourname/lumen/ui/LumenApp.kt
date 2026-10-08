package com.yourname.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.ui.common.PlaceholderScreen
import com.yourname.lumen.ui.home.HomeScreen
import com.yourname.lumen.ui.navigation.LumenDock
import com.yourname.lumen.ui.navigation.Section

@Composable
fun LumenApp() {
    var section by remember { mutableStateOf(Section.Home) }
    var dockHasFocus by remember { mutableStateOf(false) }
    val dockFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }

    // Back from content returns to the dock; Back on the dock leaves the app.
    BackHandler(enabled = !dockHasFocus) {
        dockFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTheme.colors.background),
    ) {
        // Content is declared first so it is drawn under the dock.
        Crossfade(
            targetState = section,
            animationSpec = tween(220),
            label = "section",
        ) { current ->
            when (current) {
                Section.Home -> HomeScreen(dockFocus = dockFocus, heroFocus = heroFocus)
                else -> PlaceholderScreen(current)
            }
        }

        LumenDock(
            selected = section,
            onSelect = { section = it },
            selectedFocusRequester = dockFocus,
            downTarget = heroFocus,
            onDockFocusChange = { dockHasFocus = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
        )
    }
}
