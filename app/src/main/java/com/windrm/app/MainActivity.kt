package com.windrm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.windrm.app.ui.navigation.WindRmNavHost
import com.windrm.app.ui.theme.WindRmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as WindRmApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            WindRmTheme {
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
