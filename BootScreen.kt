package com.yourname.lumen.ui.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTheme

/** Completely static: solid black with the app name. Nothing moves. */
@Composable
fun BootScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LumenText(
            text = "Lumen TV",
            style = LumenTheme.typography.title.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}
