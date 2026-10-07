package com.windrm.app.ui.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.cropped
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.WeatherRepository
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.time.Instant

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
    private val startEpochS: Long,
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

    init {
        viewModelScope.launch { mapStyle = settingsRepository.current().mapStyle }
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
