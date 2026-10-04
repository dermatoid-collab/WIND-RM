package com.windrm.app.ui.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    var uiState by mutableStateOf<ForecastUiState>(ForecastUiState.Loading)
        private set

    /** Index into the forecast points currently selected by the bottom time scrubber. */
    var scrubIndex by mutableIntStateOf(0)

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
                val route = routeRepository.getRoute(routeId) ?: error("Route not found")
                weatherRepository.forecastRoute(route, Instant.ofEpochSecond(startEpochS), speedKmh)
            }
                .onSuccess {
                    scrubIndex = 0
                    uiState = ForecastUiState.Success(it)
                }
                .onFailure { uiState = ForecastUiState.Error(it.message ?: "Couldn't calculate the forecast") }
        }
    }
}
