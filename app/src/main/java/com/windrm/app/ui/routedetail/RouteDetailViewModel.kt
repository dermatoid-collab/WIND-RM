package com.windrm.app.ui.routedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.model.Route
import com.windrm.app.repository.RouteRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Open-Meteo's practical hourly-forecast horizon; dates beyond this aren't offered in the picker. */
const val FORECAST_HORIZON_DAYS = 15L

class RouteDetailViewModel(
    private val routeRepository: RouteRepository,
    private val routeId: Long,
) : ViewModel() {

    var route by mutableStateOf<Route?>(null)
        private set
    var avgSpeedKmh by mutableStateOf(25.0)
    var startsNow by mutableStateOf(true)
    var plannedDate by mutableStateOf(LocalDate.now())
    var plannedHour by mutableStateOf(8)
    var plannedMinute by mutableStateOf(0)

    init {
        viewModelScope.launch {
            val loaded = routeRepository.getRoute(routeId)
            route = loaded
            loaded?.recordedAvgSpeedKmh?.let { avgSpeedKmh = it.coerceIn(5.0, 60.0) }
        }
    }

    fun setPlannedTime(date: LocalDate, hour: Int, minute: Int) {
        startsNow = false
        plannedDate = date
        plannedHour = hour
        plannedMinute = minute
    }

    fun useNow() {
        startsNow = true
    }

    fun computeStartInstant(): Instant = if (startsNow) {
        Instant.now()
    } else {
        plannedDate.atTime(plannedHour, plannedMinute).atZone(ZoneId.systemDefault()).toInstant()
    }
}
