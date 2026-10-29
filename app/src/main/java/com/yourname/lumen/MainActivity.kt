package com.yourname.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.yourname.lumen.core.designsystem.AppearanceFlags
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.core.diagnostics.Perf
import com.yourname.lumen.data.settings.AppSettings
import com.yourname.lumen.ui.LumenApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Perf.markStart()
        // The saved appearance is loaded here, before anything is drawn, so the first frame already
        // uses it and the old look never flashes. It stays fixed until the next launch.
        val settings = AppSettings(applicationContext)
        val appearance = settings.active
        setContent {
            val flags = remember {
                AppearanceFlags(
                    reducedMotion = appearance.reducedMotion,
                    reducedBackground = appearance.reducedBackground,
                )
            }
            LumenTheme(
                density = appearance.density,
                textScale = appearance.textScale,
                flags = flags,
            ) {
                LumenApp(settings = settings)
            }
        }
    }
}
