package com.windrm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.windrm.app.settings.AppSettings
import com.windrm.app.settings.MapKeyProvider
import com.windrm.app.ui.components.MapApiKeys
import com.windrm.app.settings.ThemeMode
import com.windrm.app.ui.navigation.WindRmNavHost
import com.windrm.app.ui.theme.WindRmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as WindRmApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bars at the top of every screen are always a saturated colour: the status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        handleIntent(intent)

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
            // The keys typed in Settings go to every map.
            LaunchedEffect(settings.mapKeys) {
                MapApiKeys.carto = settings.mapKeys[MapKeyProvider.CARTO].orEmpty()
                MapApiKeys.mapbox = settings.mapKeys[MapKeyProvider.MAPBOX].orEmpty()
                MapApiKeys.thunderforest = settings.mapKeys[MapKeyProvider.THUNDERFOREST].orEmpty()
            }
            WindRmTheme(mode = settings.themeMode) {
                WindRmNavHost(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        lifecycleScope.launch {
            runCatching { container.stravaAuthManager.handleRedirect(uri) }
        }
    }
}
