package com.windrm.app.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.gpx.GpxUriImporter
import com.windrm.app.model.CurrentWeatherSnapshot
import com.windrm.app.model.Route
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class HomeViewModel(
    private val appContext: Context,
    private val weatherRepository: WeatherRepository,
    private val routeRepository: RouteRepository,
) : ViewModel() {

    var snapshot by mutableStateOf<CurrentWeatherSnapshot?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasLocationPermission by mutableStateOf(hasPermission())
        private set
    var gpxError by mutableStateOf<String?>(null)
        private set

    init {
        if (hasLocationPermission) loadCurrentLocationWeather()
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

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun loadCurrentLocationWeather() {
        viewModelScope.launch {
            loading = true
            error = null
            runCatching {
                val location = withContext(Dispatchers.IO) { lastKnownLocation() }
                    ?: error("Location unavailable. Make sure location is turned on.")
                val label = withContext(Dispatchers.IO) { reverseGeocode(location.latitude, location.longitude) }
                weatherRepository.currentWeather(location.latitude, location.longitude, label)
            }
                .onSuccess { snapshot = it }
                .onFailure { error = it.message ?: "Couldn't load the current weather" }
            loading = false
        }
    }

    @SuppressLint("MissingPermission") // only called from paths already gated by hasPermission()
    private fun lastKnownLocation(): Location? {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) locationManager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION") // Geocoder's synchronous API; called off the main thread, kept simple for minSdk 26
    private fun reverseGeocode(lat: Double, lon: Double): String =
        runCatching {
            Geocoder(appContext, Locale.getDefault()).getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: "Current location"

    fun importGpx(context: Context, uri: Uri, onImported: (Route) -> Unit) {
        viewModelScope.launch {
            gpxError = null
            runCatching { GpxUriImporter.import(context, uri, routeRepository) }
                .onSuccess { onImported(it) }
                .onFailure { gpxError = it.message ?: "Couldn't import the GPX file" }
        }
    }
}
