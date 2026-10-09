package com.windrm.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.windrm.app.BuildConfig
import com.windrm.app.settings.MapKeyProvider

/**
 * The map services' keys in use: the one typed in Settings, else the one built into the app (the GitHub secret).
 * Held as Compose state so a map redraws its tiles when a key is typed.
 */
object MapApiKeys {
    var carto by mutableStateOf("")
    var mapbox by mutableStateOf("")
    var thunderforest by mutableStateOf("")

    fun typed(provider: MapKeyProvider): String = when (provider) {
        MapKeyProvider.CARTO -> carto
        MapKeyProvider.MAPBOX -> mapbox
        MapKeyProvider.THUNDERFOREST -> thunderforest
    }.trim()

    fun builtIn(provider: MapKeyProvider): String = when (provider) {
        MapKeyProvider.CARTO -> BuildConfig.CARTO_API_KEY
        MapKeyProvider.MAPBOX -> BuildConfig.MAPBOX_ACCESS_TOKEN
        MapKeyProvider.THUNDERFOREST -> BuildConfig.THUNDERFOREST_API_KEY
    }.trim()

    /** The key to send: the typed one wins over the built-in one. */
    fun keyFor(provider: MapKeyProvider): String = typed(provider).ifBlank { builtIn(provider) }

    /** Changes whenever a key does, so a map knows its tile sources are out of date. */
    fun fingerprint(): String = MapKeyProvider.entries.joinToString("|") { keyFor(it) }
}
