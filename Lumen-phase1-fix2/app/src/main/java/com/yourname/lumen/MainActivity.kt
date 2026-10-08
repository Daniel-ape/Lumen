package com.yourname.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.ui.LumenApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                LumenApp()
            }
        }
    }
}
