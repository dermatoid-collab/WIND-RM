package com.windrm.app.ui.routedetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
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

    /** Kept part of the route, as fractions of its length (the two thumbs of the crop slider). */
    var cropRange by mutableStateOf(0f..1f)

    private val totalDistanceM: Double get() = route?.points?.lastOrNull()?.distanceFromStartM ?: 0.0

    fun cropRangeM(): ClosedFloatingPointRange<Double> =
        (cropRange.start * totalDistanceM)..(cropRange.endInclusive * totalDistanceM)
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
    /**
     * Where a tap could place a stop: one candidate per time the ride passes there. Track points
     * within 2.5x the distance to the nearest one (at least 60 m) count as "here" -- two recordings
     * of the same road are rarely closer than 5-20 m, and a zoomed-out tap lands farther off -- and
     * candidates more than 500 m apart along the route are separate passes (out and back).
     */
    fun stopCandidates(lat: Double, lon: Double, durationMin: Int = DEFAULT_STOP_MIN): List<RouteStop> {
        val range = cropRangeM()
        // Only the kept part of a cropped route: a stop in the cut-off part would never be reached.
        val points = route?.points?.filter { it.distanceFromStartM in range }.orEmpty()
        if (points.isEmpty()) return emptyList()
        val withDistance = points.map { it to haversineMeters(lat, lon, it.lat, it.lon) }
        val nearestM = withDistance.minOf { it.second }
        val radius = maxOf(MIN_SAME_PLACE_M, nearestM * SAME_PLACE_FACTOR)
        val passes = mutableListOf<MutableList<Pair<RoutePoint, Double>>>()
        for (candidate in withDistance.filter { it.second <= radius }) {
            val current = passes.lastOrNull()
            if (current == null || candidate.first.distanceFromStartM - current.last().first.distanceFromStartM > SEPARATE_PASS_M) {
                passes += mutableListOf(candidate)
            } else {
                current += candidate
            }
        }
        return passes.map { pass ->
            val best = pass.minBy { it.second }.first
            RouteStop(best.distanceFromStartM, best.lat, best.lon, durationMin)
        }
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
private const val MIN_SAME_PLACE_M = 60.0
private const val SAME_PLACE_FACTOR = 2.5
private const val SEPARATE_PASS_M = 500.0
