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
import java.time.ZoneId
import java.time.ZonedDateTime

class RouteDetailViewModel(
    private val routeRepository: RouteRepository,
    private val routeId: Long,
) : ViewModel() {

    var route by mutableStateOf<Route?>(null)
        private set
    var avgSpeedKmh by mutableStateOf(25.0)
    var startsNow by mutableStateOf(true)
    var plannedDaysOffset by mutableStateOf(0)
    var plannedHour by mutableStateOf(8)
    var plannedMinute by mutableStateOf(0)

    init {
        viewModelScope.launch {
            val loaded = routeRepository.getRoute(routeId)
            route = loaded
            loaded?.recordedAvgSpeedKmh?.let { avgSpeedKmh = it.coerceIn(5.0, 60.0) }
        }
    }

    fun setPlannedTime(daysOffset: Int, hour: Int, minute: Int) {
        startsNow = false
        plannedDaysOffset = daysOffset
        plannedHour = hour
        plannedMinute = minute
    }

    fun useNow() {
        startsNow = true
    }

    fun computeStartInstant(): Instant = if (startsNow) {
        Instant.now()
    } else {
        ZonedDateTime.now(ZoneId.systemDefault())
            .plusDays(plannedDaysOffset.toLong())
            .withHour(plannedHour)
            .withMinute(plannedMinute)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }
}
