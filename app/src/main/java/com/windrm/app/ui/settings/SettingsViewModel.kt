package com.windrm.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.CaiProfile
import com.windrm.app.domain.RideThresholds
import com.windrm.app.gpx.GpxFolder
import com.windrm.app.location.DeviceLocation
import com.windrm.app.remote.strava.StravaAuthEvent
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.settings.AppSettings
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import com.windrm.app.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val stravaAuthManager: StravaAuthManager,
) : ViewModel() {

    var settings by mutableStateOf(AppSettings())
        private set
    var stravaAuthorized by mutableStateOf(false)
        private set
    var stravaError by mutableStateOf<String?>(null)
        private set
    var homeLocationError by mutableStateOf<String?>(null)
        private set
    var homeLocationLoading by mutableStateOf(false)
        private set

    /** Name of the chosen GPX folder as its provider reports it; null when none is chosen or it can't be read. */
    var gpxFolderName by mutableStateOf<String?>(null)
        private set

    val stravaConfigured: Boolean get() = stravaAuthManager.isConfigured

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings = it }
        }
        viewModelScope.launch {
            settingsRepository.settings.map { it.gpxFolderUri }.distinctUntilChanged().collect { uri ->
                gpxFolderName = uri?.let { withContext(Dispatchers.IO) { GpxFolder.displayName(appContext, Uri.parse(it)) } }
            }
        }
        viewModelScope.launch {
            stravaAuthorized = stravaAuthManager.isAuthorized()
        }
        viewModelScope.launch {
            stravaAuthManager.authEvents.collect { event ->
                when (event) {
                    is StravaAuthEvent.Authorized -> { stravaError = null; stravaAuthorized = true }
                    is StravaAuthEvent.Failed -> stravaError = event.message
                }
            }
        }
    }

    fun connectStrava() = stravaAuthManager.launchAuthorization()

    fun disconnectStrava() {
        viewModelScope.launch {
            stravaAuthManager.signOut()
            stravaAuthorized = false
        }
    }

    fun setMapStyle(style: MapStyle) = viewModelScope.launch { settingsRepository.setMapStyle(style) }

    fun setMapKey(provider: com.windrm.app.settings.MapKeyProvider, value: String) = viewModelScope.launch { settingsRepository.setMapKey(provider, value) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDefaultAvgSpeedKmh(speedKmh: Double) = viewModelScope.launch {
        settingsRepository.setDefaultAvgSpeedKmh(speedKmh.coerceIn(1.0, 80.0))
    }

    fun setRiderMassKg(kg: Double) = viewModelScope.launch { settingsRepository.setRiderMassKg(kg.coerceIn(20.0, 200.0)) }

    fun setBikeMassKg(kg: Double) = viewModelScope.launch { settingsRepository.setBikeMassKg(kg.coerceIn(3.0, 50.0)) }

    fun setWindHeightFactor(factor: Double) = viewModelScope.launch {
        settingsRepository.setWindHeightFactor(factor.coerceIn(0.2, 1.0))
    }

    fun setMaxDescentSpeedKmh(speedKmh: Double) = viewModelScope.launch {
        settingsRepository.setMaxDescentSpeedKmh(speedKmh.coerceIn(10.0, 120.0))
    }

    fun setRideThresholds(thresholds: RideThresholds) = viewModelScope.launch { settingsRepository.setRideThresholds(thresholds) }

    fun resetRideThresholds() = viewModelScope.launch { settingsRepository.resetRideThresholds() }

    fun setCaiProfile(profile: CaiProfile) = viewModelScope.launch { settingsRepository.setCaiProfile(profile) }

    fun resetCaiProfile() = viewModelScope.launch { settingsRepository.resetCaiProfile() }

    fun setGpxFolder(uri: Uri?) = viewModelScope.launch { settingsRepository.setGpxFolder(uri) }

    fun setForecastHorizonDays(days: Int) = viewModelScope.launch {
        settingsRepository.setForecastHorizonDays(days.coerceIn(1, 16))
    }

    /** Captures a single GPS fix and stores it as the fixed "Home" location for the home screen. */
    fun useCurrentLocationAsHome() {
        viewModelScope.launch {
            homeLocationLoading = true
            homeLocationError = null
            runCatching {
                val location = withContext(Dispatchers.IO) { DeviceLocation.lastKnown(appContext) }
                    ?: error("Location unavailable. Make sure location is turned on.")
                val label = withContext(Dispatchers.IO) { DeviceLocation.reverseGeocode(appContext, location.latitude, location.longitude) }
                settingsRepository.setHomeLocation(location.latitude, location.longitude, label)
            }.onFailure { homeLocationError = it.message ?: "Couldn't get the current location" }
            homeLocationLoading = false
        }
    }

    fun clearHomeLocation() = viewModelScope.launch { settingsRepository.clearHomeLocation() }

    /** The favourite place gets the name the user typed. */
    fun renameHome(label: String) = viewModelScope.launch { settingsRepository.setHomeLabel(label) }

    /** The place tapped on the map becomes the favourite one, named after what the geocoder calls it (it can be renamed). */
    fun setHomeFromMap(lat: Double, lon: Double) {
        viewModelScope.launch {
            val label = withContext(Dispatchers.IO) { DeviceLocation.reverseGeocode(appContext, lat, lon) }
            settingsRepository.setHomeLocation(lat, lon, label)
        }
    }

    /** What the last export or import of the settings file did, in words; null until one is done. */
    var backupMessage by mutableStateOf<String?>(null)
        private set

    /** Writes the settings to the file the user picked ([uri]). */
    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            backupMessage = try {
                val text = settingsRepository.exportBackup()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Can't write to that file")
                }
                "Settings saved to the file."
            } catch (e: Exception) {
                "Couldn't save the settings: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** Reads the settings back from the file the user picked ([uri]). */
    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            backupMessage = try {
                val text = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?.also { if (it.length > MAX_BACKUP_CHARS) error("That file is too big to be a settings file") }
                        ?: error("Can't read that file")
                }
                val count = settingsRepository.importBackup(text)
                if (count == 0) "The file has no settings to restore." else "$count settings restored."
            } catch (e: com.windrm.app.settings.BackupException) {
                "That is not a WIND-RM settings file (${e.message})."
            } catch (e: Exception) {
                "Couldn't read the settings: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private companion object {
        const val MAX_BACKUP_CHARS = 200_000
    }

    /** Where the place picker opens: the favourite place, else the last known position, else the middle of Italy. */
    fun pickerStart(): Pair<Double, Double> {
        val s = settings
        if (s.homeLat != null && s.homeLon != null) return s.homeLat to s.homeLon
        val here = runCatching { DeviceLocation.lastKnown(appContext) }.getOrNull()
        return if (here != null) here.latitude to here.longitude else 42.5 to 12.5
    }
}
