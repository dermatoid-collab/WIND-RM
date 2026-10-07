package com.windrm.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.gpx.GpxFolder
import com.windrm.app.gpx.GpxFolderExport
import com.windrm.app.gpx.GpxUriImporter
import com.windrm.app.location.DeviceLocation
import com.windrm.app.model.CurrentWeatherSnapshot
import com.windrm.app.model.Route
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.WeatherRepository
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val appContext: Context,
    private val weatherRepository: WeatherRepository,
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var snapshot by mutableStateOf<CurrentWeatherSnapshot?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasLocationPermission by mutableStateOf(hasPermission())
        private set
    var hasFixedHomeLocation by mutableStateOf(false)
        private set
    var gpxError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            hasFixedHomeLocation = settingsRepository.current().hasFixedHomeLocation
            if (hasFixedHomeLocation || hasLocationPermission) loadCurrentLocationWeather()
        }
    }

    /** Call after the runtime permission dialog resolves. */
    fun onPermissionResult(granted: Boolean) {
        hasLocationPermission = granted
        if (granted) loadCurrentLocationWeather()
    }

    fun retry() {
        if (hasPermission()) {
            hasLocationPermission = true
            loadCurrentLocationWeather()
        }
    }

    /** Re-checks permission/settings and refreshes the snapshot each time the home screen resumes. */
    fun refreshOnResume() {
        viewModelScope.launch {
            hasFixedHomeLocation = settingsRepository.current().hasFixedHomeLocation
            hasLocationPermission = hasPermission()
            if (hasFixedHomeLocation || hasLocationPermission) loadCurrentLocationWeather()
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun loadCurrentLocationWeather() {
        viewModelScope.launch {
            loading = true
            error = null
            runCatching {
                val fixed = settingsRepository.settings.first()
                if (fixed.hasFixedHomeLocation) {
                    weatherRepository.currentWeather(fixed.homeLat!!, fixed.homeLon!!, fixed.homeLabel ?: "Home")
                } else {
                    val location = withContext(Dispatchers.IO) { DeviceLocation.lastKnown(appContext) }
                        ?: error("Location unavailable. Make sure location is turned on.")
                    val label = withContext(Dispatchers.IO) { DeviceLocation.reverseGeocode(appContext, location.latitude, location.longitude) }
                    weatherRepository.currentWeather(location.latitude, location.longitude, label)
                }
            }
                .onSuccess { snapshot = it }
                .onFailure { error = it.message ?: "Couldn't load the current weather" }
            loading = false
        }
    }

    /** A file picked with the "+" menu, read but not saved yet: it waits for the user to name it in the import dialog. */
    var pendingImport by mutableStateOf<Route?>(null)
        private set

    /** Name of the GPX folder chosen in Settings, offered by the import dialog; null = none. */
    var gpxFolderName by mutableStateOf<String?>(null)
        private set

    fun prepareImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            gpxError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val route = GpxUriImporter.read(context, uri)
                    val folder = settingsRepository.current().gpxFolderUri?.let(Uri::parse)
                    route to folder?.let { GpxFolder.displayName(appContext, it) }
                }
            }
                .onSuccess { (route, folderName) ->
                    gpxFolderName = folderName
                    pendingImport = route
                }
                .onFailure { gpxError = it.message ?: "Couldn't import the GPX file" }
        }
    }

    fun cancelImport() {
        pendingImport = null
    }

    /** Saves the picked file's route under [name]; with [toFolder] also keeps a GPX copy in the chosen folder. */
    fun confirmImport(name: String, toFolder: Boolean, onSaved: (Route) -> Unit) {
        val route = pendingImport ?: return
        pendingImport = null
        viewModelScope.launch {
            val named = route.copy(name = name.trim().ifEmpty { route.name })
            val saved = named.copy(id = routeRepository.saveRoute(named))
            if (toFolder) GpxFolderExport.save(appContext, settingsRepository, saved, gpxFolderName)
            onSaved(saved)
        }
    }
}
