package com.windrm.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.windrm.app.domain.CaiProfile
import com.windrm.app.domain.RideThresholds
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

    // Base map without names plus a transparent layer of names only, drawn above the route (like Strava).
    // CARTO needs its free key, like CARTO Positron; Esri's gray canvas needs none.
    CARTO_VOYAGER("CARTO Voyager (names above the route)", requiresApiKey = true),
    CARTO_DARK("CARTO Dark Matter (names above the route)", requiresApiKey = true),
    ESRI_LIGHT_GRAY("Esri Light Gray (names above the route)", requiresApiKey = false),
    ESRI_DARK_GRAY("Esri Dark Gray (names above the route)", requiresApiKey = false),
}

/**
 * How the app looks: the system / light / dark choice of the orange default palette, or one of the
 * colour palettes. [dark] says whether a palette is a dark or a light one (null = follows the phone).
 */
enum class ThemeMode(val label: String, val dark: Boolean? = null) {
    SYSTEM("System default"),
    LIGHT("Light", false),
    DARK("Dark", true),

    // Dark palettes
    TOKYO_NIGHT("Tokyo Night", true),
    NORD("Nord", true),
    DRACULA("Dracula", true),
    GRUVBOX_DARK("Gruvbox Dark", true),
    CATPPUCCIN_MOCHA("Catppuccin Mocha", true),
    ONE_DARK("One Dark", true),
    SOLARIZED_DARK("Solarized Dark", true),
    AMOLED("Black (OLED)", true),

    // Light palettes
    SOLARIZED_LIGHT("Solarized Light", false),
    CATPPUCCIN_LATTE("Catppuccin Latte", false),
    GRUVBOX_LIGHT("Gruvbox Light", false),
}

data class AppSettings(
    val mapStyle: MapStyle = MapStyle.OSM_STANDARD,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Which routing profile the last route used: the WIND-RM main-roads one or the stock one (and why). */
    val routingProfileNote: String? = null,
    val defaultAvgSpeedKmh: Double = 25.0,
    val forecastHorizonDays: Int = 15,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeLabel: String? = null,
    val riderMassKg: Double = 62.0,
    val bikeMassKg: Double = 10.0,
    val maxDescentSpeedKmh: Double = 40.0,
    /** Share of the 10 m forecast wind a rider feels (see [RiderProfile.windHeightFactor]). */
    val windHeightFactor: Double = RiderProfile().windHeightFactor,
    val rideThresholds: RideThresholds = RideThresholds(),
    val caiProfile: CaiProfile = CaiProfile(),
    /** Document-tree uri of the folder chosen for GPX files; null = none chosen. */
    val gpxFolderUri: String? = null,
) {
    val riderProfile: RiderProfile get() = RiderProfile(riderMassKg, bikeMassKg, maxDescentSpeedKmh, windHeightFactor)

    val hasFixedHomeLocation: Boolean get() = homeLat != null && homeLon != null
}

/** How one [RideThresholds] value is stored: its DataStore key, and how to read it from and write it into the data class. */
private class ThresholdField(
    val key: Preferences.Key<Double>,
    val read: (RideThresholds) -> Double,
    val write: (RideThresholds, Double) -> RideThresholds,
)

/** Same as [ThresholdField], for one [CaiProfile] value. */
private class CaiField(
    val key: Preferences.Key<Double>,
    val read: (CaiProfile) -> Double,
    val write: (CaiProfile, Double) -> CaiProfile,
)

/** User-configurable app preferences, persisted locally (never synced, no account needed). */
class SettingsRepository(private val context: Context) {
    private val keyMapStyle = stringPreferencesKey("map_style")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyRoutingProfileNote = stringPreferencesKey("routing_profile_note")
    private val keyDefaultAvgSpeedKmh = doublePreferencesKey("default_avg_speed_kmh")
    private val keyForecastHorizonDays = intPreferencesKey("forecast_horizon_days")
    private val keyHomeLat = doublePreferencesKey("home_lat")
    private val keyHomeLon = doublePreferencesKey("home_lon")
    private val keyHomeLabel = stringPreferencesKey("home_label")
    private val keyHasHomeLocation = booleanPreferencesKey("has_home_location")
    private val keyRiderMassKg = doublePreferencesKey("rider_mass_kg")
    private val keyBikeMassKg = doublePreferencesKey("bike_mass_kg")
    private val keyMaxDescentSpeedKmh = doublePreferencesKey("max_descent_speed_kmh")
    private val keyWindHeightFactor = doublePreferencesKey("wind_height_factor")
    private val keyGpxFolderUri = stringPreferencesKey("gpx_folder_uri")
    // "routeId|documentUri" entries: which saved route a file of the GPX folder already is, so opening it again doesn't import a copy.
    private val keyGpxLinks = stringSetPreferencesKey("gpx_links")

    /** One key per traffic-light limit; a missing key falls back to the default in [RideThresholds]. */
    /** Trekking (CAI) pacing values, stored the same way; a missing key falls back to [CaiProfile]'s default. */
    private val caiFields = listOf(
        CaiField(doublePreferencesKey("cai_flat_kmh"), { it.flatKmh }, { c, v -> c.copy(flatKmh = v) }),
        CaiField(doublePreferencesKey("cai_up_mh"), { it.upMetresPerHour }, { c, v -> c.copy(upMetresPerHour = v) }),
        CaiField(doublePreferencesKey("cai_down_mh"), { it.downMetresPerHour }, { c, v -> c.copy(downMetresPerHour = v) }),
        CaiField(doublePreferencesKey("cai_high_altitude_m"), { it.highAltitudeM }, { c, v -> c.copy(highAltitudeM = v) }),
        CaiField(doublePreferencesKey("cai_high_up_mh"), { it.highUpMetresPerHour }, { c, v -> c.copy(highUpMetresPerHour = v) }),
        CaiField(doublePreferencesKey("cai_high_down_mh"), { it.highDownMetresPerHour }, { c, v -> c.copy(highDownMetresPerHour = v) }),
    )

    private val rideThresholdFields = listOf(
        ThresholdField(doublePreferencesKey("ride_feels_cold_yellow"), { it.feelsColdYellowC }, { t, v -> t.copy(feelsColdYellowC = v) }),
        ThresholdField(doublePreferencesKey("ride_feels_cold_red"), { it.feelsColdRedC }, { t, v -> t.copy(feelsColdRedC = v) }),
        ThresholdField(doublePreferencesKey("ride_feels_hot_yellow"), { it.feelsHotYellowC }, { t, v -> t.copy(feelsHotYellowC = v) }),
        ThresholdField(doublePreferencesKey("ride_feels_hot_red"), { it.feelsHotRedC }, { t, v -> t.copy(feelsHotRedC = v) }),
        ThresholdField(doublePreferencesKey("ride_rain_prob_yellow"), { it.rainProbYellowPct }, { t, v -> t.copy(rainProbYellowPct = v) }),
        ThresholdField(doublePreferencesKey("ride_rain_prob_red"), { it.rainProbRedPct }, { t, v -> t.copy(rainProbRedPct = v) }),
        ThresholdField(doublePreferencesKey("ride_rain_yellow"), { it.rainYellowMmH }, { t, v -> t.copy(rainYellowMmH = v) }),
        ThresholdField(doublePreferencesKey("ride_rain_red"), { it.rainRedMmH }, { t, v -> t.copy(rainRedMmH = v) }),
        ThresholdField(doublePreferencesKey("ride_wind_yellow"), { it.windYellowKmh }, { t, v -> t.copy(windYellowKmh = v) }),
        ThresholdField(doublePreferencesKey("ride_wind_red"), { it.windRedKmh }, { t, v -> t.copy(windRedKmh = v) }),
        ThresholdField(doublePreferencesKey("ride_gust_yellow"), { it.gustYellowKmh }, { t, v -> t.copy(gustYellowKmh = v) }),
        ThresholdField(doublePreferencesKey("ride_gust_red"), { it.gustRedKmh }, { t, v -> t.copy(gustRedKmh = v) }),
        ThresholdField(doublePreferencesKey("ride_aqi_yellow"), { it.aqiYellow }, { t, v -> t.copy(aqiYellow = v) }),
        ThresholdField(doublePreferencesKey("ride_aqi_red"), { it.aqiRed }, { t, v -> t.copy(aqiRed = v) }),
        ThresholdField(doublePreferencesKey("ride_uv_yellow"), { it.uvYellow }, { t, v -> t.copy(uvYellow = v) }),
        ThresholdField(doublePreferencesKey("ride_uv_red"), { it.uvRed }, { t, v -> t.copy(uvRed = v) }),
        ThresholdField(doublePreferencesKey("ride_dew_yellow"), { it.dewPointYellowC }, { t, v -> t.copy(dewPointYellowC = v) }),
        ThresholdField(doublePreferencesKey("ride_dew_red"), { it.dewPointRedC }, { t, v -> t.copy(dewPointRedC = v) }),
        ThresholdField(doublePreferencesKey("ride_ice_max_temp"), { it.iceMaxTempC }, { t, v -> t.copy(iceMaxTempC = v) }),
    )

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            mapStyle = prefs[keyMapStyle]?.let { runCatching { MapStyle.valueOf(it) }.getOrNull() } ?: MapStyle.OSM_STANDARD,
            routingProfileNote = prefs[keyRoutingProfileNote],
            themeMode = prefs[keyThemeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultAvgSpeedKmh = prefs[keyDefaultAvgSpeedKmh] ?: 25.0,
            forecastHorizonDays = prefs[keyForecastHorizonDays] ?: 15,
            homeLat = if (prefs[keyHasHomeLocation] == true) prefs[keyHomeLat] else null,
            homeLon = if (prefs[keyHasHomeLocation] == true) prefs[keyHomeLon] else null,
            homeLabel = prefs[keyHomeLabel],
            riderMassKg = prefs[keyRiderMassKg] ?: 62.0,
            bikeMassKg = prefs[keyBikeMassKg] ?: 10.0,
            maxDescentSpeedKmh = prefs[keyMaxDescentSpeedKmh] ?: 40.0,
            windHeightFactor = prefs[keyWindHeightFactor] ?: RiderProfile().windHeightFactor,
            rideThresholds = rideThresholdFields.fold(RideThresholds()) { t, field -> prefs[field.key]?.let { field.write(t, it) } ?: t },
            caiProfile = caiFields.fold(CaiProfile()) { c, field -> prefs[field.key]?.let { field.write(c, it) } ?: c },
            gpxFolderUri = prefs[keyGpxFolderUri],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** The id BRouter's server gave the custom routing profile stored under [key] (null = none uploaded yet). */
    suspend fun brouterProfileId(key: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("brouter_profile_$key")]

    /** Which routing profile the last route really used (see [com.windrm.app.repository.ProfileIdStore.note]). */
    suspend fun setRoutingProfileNote(text: String) {
        context.settingsDataStore.edit { it[keyRoutingProfileNote] = text }
    }

    suspend fun setBrouterProfileId(key: String, id: String) {
        context.settingsDataStore.edit { it[stringPreferencesKey("brouter_profile_$key")] = id }
    }

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

    suspend fun setWindHeightFactor(factor: Double) {
        context.settingsDataStore.edit { it[keyWindHeightFactor] = factor }
    }

    suspend fun setMaxDescentSpeedKmh(speedKmh: Double) {
        context.settingsDataStore.edit { it[keyMaxDescentSpeedKmh] = speedKmh }
    }

    suspend fun setRideThresholds(thresholds: RideThresholds) {
        context.settingsDataStore.edit { prefs -> rideThresholdFields.forEach { prefs[it.key] = it.read(thresholds) } }
    }

    /**
     * Remembers the chosen GPX folder and keeps read/write access to it across restarts; passing
     * null forgets it. The grant on a previously chosen folder is released.
     */
    suspend fun setGpxFolder(uri: Uri?) {
        val previous = current().gpxFolderUri
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        context.settingsDataStore.edit { prefs ->
            if (uri == null) prefs.remove(keyGpxFolderUri) else prefs[keyGpxFolderUri] = uri.toString()
        }
        if (previous != null && previous != uri?.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(previous), flags) }
        }
    }

    /** The saved route a GPX file (by document uri) was already imported as or written from, if any. */
    suspend fun linkedRouteId(documentUri: String): Long? =
        context.settingsDataStore.data.first()[keyGpxLinks].orEmpty()
            .firstOrNull { it.substringAfter('|') == documentUri }
            ?.substringBefore('|')?.toLongOrNull()

    suspend fun linkGpx(documentUri: String, routeId: Long) {
        context.settingsDataStore.edit { prefs ->
            val links = prefs[keyGpxLinks].orEmpty().filterNot { it.substringAfter('|') == documentUri }.toSet()
            prefs[keyGpxLinks] = links + "$routeId|$documentUri"
        }
    }

    suspend fun setCaiProfile(profile: CaiProfile) {
        context.settingsDataStore.edit { prefs -> caiFields.forEach { prefs[it.key] = it.read(profile) } }
    }

    /** Drops the stored trekking values so the CAI defaults apply again. */
    suspend fun resetCaiProfile() {
        context.settingsDataStore.edit { prefs -> caiFields.forEach { prefs.remove(it.key) } }
    }

    /** Drops the stored limits so the defaults in [RideThresholds] apply again. */
    suspend fun resetRideThresholds() {
        context.settingsDataStore.edit { prefs -> rideThresholdFields.forEach { prefs.remove(it.key) } }
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
