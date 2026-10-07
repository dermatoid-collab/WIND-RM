package com.windrm.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.CaiProfile
import com.windrm.app.domain.RideThresholds
import com.windrm.app.location.DeviceLocation
import com.windrm.app.remote.strava.StravaAuthEvent
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.settings.AppSettings
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import com.windrm.app.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
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

    val stravaConfigured: Boolean get() = stravaAuthManager.isConfigured

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings = it }
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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDefaultAvgSpeedKmh(speedKmh: Double) = viewModelScope.launch {
        settingsRepository.setDefaultAvgSpeedKmh(speedKmh.coerceIn(1.0, 80.0))
    }

    fun setRiderMassKg(kg: Double) = viewModelScope.launch { settingsRepository.setRiderMassKg(kg.coerceIn(20.0, 200.0)) }

    fun setBikeMassKg(kg: Double) = viewModelScope.launch { settingsRepository.setBikeMassKg(kg.coerceIn(3.0, 50.0)) }

    fun setMaxDescentSpeedKmh(speedKmh: Double) = viewModelScope.launch {
        settingsRepository.setMaxDescentSpeedKmh(speedKmh.coerceIn(10.0, 120.0))
    }

    fun setRideThresholds(thresholds: RideThresholds) = viewModelScope.launch { settingsRepository.setRideThresholds(thresholds) }

    fun resetRideThresholds() = viewModelScope.launch { settingsRepository.resetRideThresholds() }

    fun setCaiProfile(profile: CaiProfile) = viewModelScope.launch { settingsRepository.setCaiProfile(profile) }

    fun resetCaiProfile() = viewModelScope.launch { settingsRepository.resetCaiProfile() }

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
}
