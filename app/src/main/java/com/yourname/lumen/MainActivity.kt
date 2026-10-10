package com.yourname.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.ui.LumenApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = remember { AppSettings(applicationContext) }
            // Changing "Interface size" in Settings updates this live.
            LumenTheme(uiScale = settings.uiScale) {
                LumenApp(settings = settings)
            }
        }
    }
}
