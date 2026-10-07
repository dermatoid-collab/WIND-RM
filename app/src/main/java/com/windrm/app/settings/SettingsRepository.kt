package com.windrm.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.windrm.app.domain.RiderProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** Raster tile source for every map shown in the app. */
enum class MapStyle(val label: String, val requiresApiKey: Boolean) {
    OSM_STANDARD("OSM Standard", requiresApiKey = false),
    OPEN_TOPO("OpenTopoMap", requiresApiKey = false),
    MAPBOX_OUTDOORS("Mapbox Outdoors (same as Strava)", requiresApiKey = true),
    CARTO_POSITRON("CARTO Positron", requiresApiKey = true),
    THUNDERFOREST_OUTDOORS("Thunderforest Outdoors", requiresApiKey = true),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSettings(
    val mapStyle: MapStyle = MapStyle.OSM_STANDARD,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultAvgSpeedKmh: Double = 25.0,
    val forecastHorizonDays: Int = 15,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeLabel: String? = null,
    val riderMassKg: Double = 62.0,
    val bikeMassKg: Double = 10.0,
    val maxDescentSpeedKmh: Double = 40.0,
) {
    val riderProfile: RiderProfile get() = RiderProfile(riderMassKg, bikeMassKg, maxDescentSpeedKmh)

    val hasFixedHomeLocation: Boolean get() = homeLat != null && homeLon != null
}

/** User-configurable app preferences, persisted locally (never synced, no account needed). */
class SettingsRepository(private val context: Context) {
    private val keyMapStyle = stringPreferencesKey("map_style")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyDefaultAvgSpeedKmh = doublePreferencesKey("default_avg_speed_kmh")
    private val keyForecastHorizonDays = intPreferencesKey("forecast_horizon_days")
    private val keyHomeLat = doublePreferencesKey("home_lat")
    private val keyHomeLon = doublePreferencesKey("home_lon")
    private val keyHomeLabel = stringPreferencesKey("home_label")
    private val keyHasHomeLocation = booleanPreferencesKey("has_home_location")
    private val keyRiderMassKg = doublePreferencesKey("rider_mass_kg")
    private val keyBikeMassKg = doublePreferencesKey("bike_mass_kg")
    private val keyMaxDescentSpeedKmh = doublePreferencesKey("max_descent_speed_kmh")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            mapStyle = prefs[keyMapStyle]?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() } ?: MapStyle.OSM_STANDARD,
            themeMode = prefs[keyThemeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultAvgSpeedKmh = prefs[keyDefaultAvgSpeedKmh] ?: 25.0,
            forecastHorizonDays = prefs[keyForecastHorizonDays] ?: 15,
            homeLat = if (prefs[keyHasHomeLocation] == true) prefs[keyHomeLat] else null,
            homeLon = if (prefs[keyHasHomeLocation] == true) prefs[keyHomeLon] else null,
            homeLabel = prefs[keyHomeLabel],
            riderMassKg = prefs[keyRiderMassKg] ?: 62.0,
            bikeMassKg = prefs[keyBikeMassKg] ?: 10.0,
            maxDescentSpeedKmh = prefs[keyMaxDescentSpeedKmh] ?: 40.0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMapStyle(style: MapStyle) {
        context.settingsDataStore.edit { it[keyMapStyle] = style.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun setDefaultAvgSpeedKmh(speedKmh: Double) {
        context.settingsDataStore.edit { it[keyDefaultAvgSpeedKmh] = speedKmh }
    }

    suspend fun setForecastHorizonDays(days: Int) {
        context.settingsDataStore.edit { it[keyForecastHorizonDays] = days }
    }

    suspend fun setRiderMassKg(kg: Double) {
        context.settingsDataStore.edit { it[keyRiderMassKg] = kg }
    }

    suspend fun setBikeMassKg(kg: Double) {
        context.settingsDataStore.edit { it[keyBikeMassKg] = kg }
    }

    suspend fun setMaxDescentSpeedKmh(speedKmh: Double) {
        context.settingsDataStore.edit { it[keyMaxDescentSpeedKmh] = speedKmh }
    }

    suspend fun setHomeLocation(lat: Double, lon: Double, label: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[keyHomeLat] = lat
            prefs[keyHomeLon] = lon
            prefs[keyHomeLabel] = label
            prefs[keyHasHomeLocation] = true
        }
    }

    suspend fun clearHomeLocation() {
        context.settingsDataStore.edit { prefs ->
            prefs[keyHasHomeLocation] = false
        }
    }
}
