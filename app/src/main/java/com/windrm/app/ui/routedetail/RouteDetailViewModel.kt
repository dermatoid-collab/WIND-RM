package com.windrm.app.ui.routedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.Route
import com.windrm.app.model.RouteStop
import com.windrm.app.repository.RouteRepository
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RouteDetailViewModel(
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
    private val routeId: Long,
) : ViewModel() {

    var route by mutableStateOf<Route?>(null)
        private set
    var avgSpeedKmh by mutableStateOf(25.0)
    var startsNow by mutableStateOf(true)
    var pacingMode by mutableStateOf(PacingMode.CONSTANT)
    var plannedDate by mutableStateOf(LocalDate.now())
    var plannedHour by mutableStateOf(8)
    var plannedMinute by mutableStateOf(0)
    var forecastHorizonDays by mutableStateOf(15L)
        private set
    var mapStyle by mutableStateOf(MapStyle.OSM_STANDARD)
        private set

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            avgSpeedKmh = settings.defaultAvgSpeedKmh
            forecastHorizonDays = settings.forecastHorizonDays.toLong()
            mapStyle = settings.mapStyle

            val loaded = routeRepository.getRoute(routeId)
            route = loaded
            loaded?.recordedAvgSpeedKmh?.let { avgSpeedKmh = it.coerceIn(5.0, 60.0) }
        }
    }

    /** Snaps a tap to the nearest track point; that point's distance is where the stop sits. */
    fun stopAt(lat: Double, lon: Double, durationMin: Int = DEFAULT_STOP_MIN): RouteStop? {
        val points = route?.points ?: return null
        val nearest = points.minByOrNull { haversineMeters(lat, lon, it.lat, it.lon) } ?: return null
        return RouteStop(nearest.distanceFromStartM, nearest.lat, nearest.lon, durationMin)
    }

    fun addStop(stop: RouteStop) = saveStops((route?.stops.orEmpty() + stop).sortedBy { it.distanceM })

    fun updateStop(index: Int, durationMin: Int) {
        val stops = route?.stops ?: return
        if (index !in stops.indices) return
        saveStops(stops.mapIndexed { i, s -> if (i == index) s.copy(durationMin = durationMin) else s })
    }

    fun removeStop(index: Int) {
        val stops = route?.stops ?: return
        saveStops(stops.filterIndexed { i, _ -> i != index })
    }

    private fun saveStops(stops: List<RouteStop>) {
        val current = route ?: return
        route = current.copy(stops = stops)
        viewModelScope.launch { routeRepository.setStops(current.id, stops) }
    }

    fun toggleFavorite() {
        val current = route ?: return
        val updated = current.copy(isFavorite = !current.isFavorite)
        route = updated
        viewModelScope.launch { routeRepository.setFavorite(updated.id, updated.isFavorite) }
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

const val DEFAULT_STOP_MIN = 30
