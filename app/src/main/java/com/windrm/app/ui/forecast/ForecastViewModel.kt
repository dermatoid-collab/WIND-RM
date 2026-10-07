package com.windrm.app.ui.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.RideThresholds
import com.windrm.app.domain.cropped
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.WeatherRepository
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import com.windrm.app.model.WeatherPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface ForecastUiState {
    data object Loading : ForecastUiState
    data class Error(val message: String) : ForecastUiState
    data class Success(val result: RouteForecastResult) : ForecastUiState
}

class ForecastViewModel(
    private val routeRepository: RouteRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val routeId: Long,
    initialStartEpochS: Long,
    private val speedKmh: Double,
    private val pacing: PacingMode,
    /** Kept part of the route, metres from its start (the route screen's crop slider). */
    private val cropRangeM: ClosedFloatingPointRange<Double>,
) : ViewModel() {

    var uiState by mutableStateOf<ForecastUiState>(ForecastUiState.Loading)
        private set

    /** Position (0..1 of the route's distance) currently under a finger on a chart or the slider; null = not touching. */
    var scrubFraction by mutableStateOf<Float?>(null)

    var mapStyle by mutableStateOf(MapStyle.OSM_STANDARD)
        private set

    /** Start of the ride; changed from inside the forecast with the start-time picker. */
    var startEpochS by mutableStateOf(initialStartEpochS)
        private set

    /** Hourly weather at the route start over the whole forecast horizon, for the start-time picker. */
    var startPickerWeather by mutableStateOf<List<WeatherPoint>?>(null)
        private set

    /** Limits for the ride traffic light, from Settings. */
    var rideThresholds by mutableStateOf(RideThresholds())
        private set
    var forecastHorizonDays by mutableStateOf(15)
        private set

    fun loadStartPickerWeather() {
        if (startPickerWeather != null) return
        val start = (uiState as? ForecastUiState.Success)?.result?.route?.points?.firstOrNull() ?: return
        viewModelScope.launch {
            val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            runCatching { weatherRepository.hourlyWeatherAt(start.lat, start.lon, today, (forecastHorizonDays + 1) * 24) }
                .onSuccess { startPickerWeather = it }
        }
    }

    fun changeStart(epochS: Long) {
        startEpochS = epochS
        load()
    }

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            mapStyle = settings.mapStyle
            forecastHorizonDays = settings.forecastHorizonDays
            rideThresholds = settings.rideThresholds
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = ForecastUiState.Loading
            runCatching {
                val route = (routeRepository.getRoute(routeId) ?: error("Route not found"))
                    .cropped(cropRangeM.start, cropRangeM.endInclusive)
                val profile = settingsRepository.current().riderProfile
                weatherRepository.forecastRoute(route, Instant.ofEpochSecond(startEpochS), speedKmh, pacing, profile)
            }
                .onSuccess {
                    scrubFraction = null
                    uiState = ForecastUiState.Success(it)
                }
                .onFailure { uiState = ForecastUiState.Error(it.message ?: "Couldn't calculate the forecast") }
        }
    }
}
