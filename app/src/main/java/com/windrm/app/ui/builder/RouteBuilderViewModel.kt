package com.windrm.app.ui.builder

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.ActivityType
import com.windrm.app.domain.CaiPacing
import com.windrm.app.domain.CaiProfile
import com.windrm.app.domain.assembleRoute
import com.windrm.app.domain.haversineMeters
import com.windrm.app.gpx.GpxFolder
import com.windrm.app.gpx.GpxFolderExport
import com.windrm.app.location.DeviceLocation
import com.windrm.app.model.LatLon
import com.windrm.app.model.Route
import com.windrm.app.repository.BuilderProfile
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.RoutedSegment
import com.windrm.app.repository.RoutingError
import com.windrm.app.repository.RoutingException
import com.windrm.app.repository.RoutingRepository
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The route being drawn: points tapped on the map, joined by routed stretches. Each new point is
 * routed from the previous one along the most direct way; on a ride with "paved only" the stretches
 * that still have to use unpaved ways are shown as such, for the user to see.
 */
class RouteBuilderViewModel(
    private val appContext: Context,
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
    private val routingRepository: RoutingRepository,
) : ViewModel() {

    var activity by mutableStateOf(ActivityType.RIDE)
        private set

    /** The user's deliberate choice to let a ride use unpaved roads; off by default. */
    var allowUnpaved by mutableStateOf(false)
        private set

    /**
     * Manual mode: new points are joined by straight lines, off any road or path, until it is switched off again.
     * Stretches already drawn keep the way they were made.
     */
    var manualMode by mutableStateOf(false)
        private set

    /** Tapped points, snapped to the road network; always one more than [segments] once routing started. */
    var waypoints by mutableStateOf<List<LatLon>>(emptyList())
        private set
    var segments by mutableStateOf<List<RoutedSegment>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<RoutingError?>(null)
        private set

    var mapStyle by mutableStateOf(MapStyle.OSM_STANDARD)
        private set

    /** Where the map opens (latitude, longitude): Home, else the last known position, else Italy. Null until known. */
    var mapCenter by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var mapZoom by mutableStateOf(DEFAULT_ZOOM)
        private set

    /** Name of the GPX folder chosen in Settings, shown in the save dialog; null = none (or it can't be read). */
    var gpxFolderName by mutableStateOf<String?>(null)
        private set

    /** Speed behind the time estimate: the Settings average for a ride, the CAI flat speed for a trek. */
    var rideSpeedKmh by mutableStateOf(25.0)
        private set
    private var caiProfile = CaiProfile()
    val estimateSpeedKmh: Double get() = if (activity == ActivityType.TREK) caiProfile.flatKmh else rideSpeedKmh

    private var job: Job? = null

    val profile: BuilderProfile get() = BuilderProfile.of(activity, allowUnpaved)

    /** Paved-only rides keep unpaved ways out, so any that remain after a settings change are flagged. */
    val strictPaved: Boolean get() = profile == BuilderProfile.ROAD_PAVED

    val totalDistanceM: Double get() = segments.sumOf { s -> s.points.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }.sum() }
    val pavedM: Double get() = segments.sumOf { it.pavedM }
    val unpavedM: Double get() = segments.sumOf { it.unpavedM }
    val trailM: Double get() = segments.sumOf { it.trailM }
    /** The surface can be judged when every routed stretch (hand-drawn ones have no surface) has its way tags. */
    val surfaceKnown: Boolean get() = segments.filter { !it.manual }.let { routed -> routed.isNotEmpty() && routed.all { it.surfaceKnown } }
    val manualM: Double get() = segments.filter { it.manual }.sumOf { it.lengthM }

    /** Unpaved or trail metres inside a route that is meant to be paved only. */
    val leftoverRoughM: Double get() = if (strictPaved) unpavedM + trailM else 0.0

    /**
     * Moving time of [route]: a ride at the Settings average speed (the realistic pace keeps the same
     * total), a trek by the CAI trail times, which add time for the climbing.
     */
    fun estimateSeconds(route: Route): Long = when {
        activity == ActivityType.TREK -> CaiPacing.totalSeconds(route.points, caiProfile.flatKmh, caiProfile)
        rideSpeedKmh > 0 -> (route.distanceKm / rideSpeedKmh * 3600).toLong()
        else -> 0L
    }

    /** The finished route, or null until there are two points; the name is only a placeholder here. */
    val draft: Route? get() = assembleRoute("", activity, segments, 0L, allowUnpaved)

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            mapStyle = settings.mapStyle
            rideSpeedKmh = settings.defaultAvgSpeedKmh
            caiProfile = settings.caiProfile
            gpxFolderName = settings.gpxFolderUri?.let(Uri::parse)?.let { tree ->
                withContext(Dispatchers.IO) { GpxFolder.displayName(appContext, tree) }
            }
            val home = settings.homeLat?.let { lat -> settings.homeLon?.let { lon -> lat to lon } }
            val here = home ?: withContext(Dispatchers.IO) {
                runCatching { DeviceLocation.lastKnown(appContext) }.getOrNull()?.let { it.latitude to it.longitude }
            }
            if (here != null) {
                mapCenter = here
            } else {
                mapCenter = DEFAULT_CENTER
                mapZoom = DEFAULT_COUNTRY_ZOOM
            }
        }
    }

    fun onMapTap(lat: Double, lon: Double) {
        if (busy) return
        error = null
        val target = LatLon(lat, lon)
        val from = waypoints.lastOrNull()
        if (from == null) waypoints = listOf(target) else extend(from, target)
    }

    fun changeManualMode(on: Boolean) {
        manualMode = on
    }

    /** One stretch between two points: along the roads, or a straight line when it is [manual]. */
    private suspend fun leg(from: LatLon, to: LatLon, manual: Boolean): RoutedSegment =
        if (manual) routingRepository.straight(from, to) else routingRepository.route(from, to, profile)

    /** Adds the stretch from the current end of the route to [to]: routed, or straight in manual mode. */
    private fun extend(from: LatLon, to: LatLon) {
        job = viewModelScope.launch {
            busy = true
            try {
                append(leg(from, to, manualMode))
            } catch (e: RoutingException) {
                error = e.error
            } finally {
                busy = false
            }
        }
    }

    private fun append(segment: RoutedSegment) {
        val start = segment.points.first().let { LatLon(it.lat, it.lon) }
        val end = segment.points.last().let { LatLon(it.lat, it.lon) }
        // The very first tap is replaced by where the router actually snapped it onto the road.
        waypoints = (if (segments.isEmpty()) listOf(start) else waypoints) + end
        segments = segments + segment
    }

    /**
     * A point dragged to a new place: the stretch before it and the one after it are routed again through
     * its new position (snapped to the road). Nothing changes if routing fails.
     */
    fun moveWaypoint(index: Int, lat: Double, lon: Double) {
        if (busy || index !in waypoints.indices) return
        error = null
        val target = LatLon(lat, lon)
        // A lone first point has no stretch to route: it simply moves.
        if (segments.isEmpty()) {
            waypoints = listOf(target)
            return
        }
        val points = waypoints
        job = viewModelScope.launch {
            busy = true
            try {
                val last = points.size - 1
                val beforeManual = index > 0 && segments[index - 1].manual
                val afterManual = index < last && segments[index].manual
                // The routed sides first: the road decides where the point lands; hand-drawn sides then go to that place.
                var before = if (index > 0 && !beforeManual) routingRepository.route(points[index - 1], target, profile) else null
                var after = if (index < last && !afterManual) routingRepository.route(target, points[index + 1], profile) else null
                val snapped = (before?.points?.last() ?: after?.points?.first())?.let { LatLon(it.lat, it.lon) } ?: target
                if (index > 0 && beforeManual) before = routingRepository.straight(points[index - 1], snapped)
                if (index < last && afterManual) after = routingRepository.straight(snapped, points[index + 1])
                val newSegments = segments.toMutableList()
                if (before != null) newSegments[index - 1] = before
                if (after != null) newSegments[index] = after
                segments = newSegments
                waypoints = points.toMutableList().also { it[index] = snapped }
            } catch (e: RoutingException) {
                error = e.error
            } finally {
                busy = false
            }
        }
    }

    /**
     * Where the line segment [flatIndex] of the drawn track (points of all stretches one after the other) lies:
     * the stretch it belongs to and its index inside it.
     */
    private fun locate(flatIndex: Int): Pair<Int, Int>? {
        var offset = 0
        for ((s, segment) in segments.withIndex()) {
            val last = offset + segment.points.size - 1
            if (flatIndex < last) return s to (flatIndex - offset).coerceAtLeast(0)
            offset += segment.points.size
        }
        return null
    }

    /** A new waypoint on the track at ([lat], [lon]), on line segment [flatIndex]: the stretch is cut there, with no new routing. */
    fun insertWaypointOnTrack(flatIndex: Int, lat: Double, lon: Double) {
        if (busy) return
        val (s, j) = locate(flatIndex) ?: return
        val (before, after) = segments[s].splitAt(j, lat, lon)
        segments = segments.toMutableList().also {
            it[s] = before
            it.add(s + 1, after)
        }
        waypoints = waypoints.toMutableList().also { it.add(s + 1, LatLon(lat, lon)) }
    }

    /**
     * The route ends at ([lat], [lon]): a new last waypoint is added there, joined to the old end like any tapped
     * point. Nothing already drawn is ever dropped, also when the place is on a stretch already ridden.
     */
    fun endRouteAt(lat: Double, lon: Double) {
        if (busy) return
        val from = waypoints.lastOrNull() ?: return
        error = null
        extend(from, LatLon(lat, lon))
    }

    /** The route ends at the waypoint [index]: the way back to it is added after the old end; no waypoint is removed. */
    fun endRouteAtWaypoint(index: Int) {
        if (busy || index !in waypoints.indices || index == waypoints.lastIndex) return
        error = null
        extend(waypoints.last(), waypoints[index])
    }

    /**
     * Deletes the waypoint [index], because the user asked for it: its two stretches become one, routed (or
     * straight, when both were drawn by hand) from the waypoint before to the one after. Deleting an end just
     * drops its stretch.
     */
    fun deleteWaypoint(index: Int) {
        if (busy || index !in waypoints.indices) return
        error = null
        when {
            waypoints.size == 1 -> clear()
            index == 0 -> {
                segments = segments.drop(1)
                waypoints = waypoints.drop(1)
            }
            index == waypoints.lastIndex -> {
                segments = segments.dropLast(1)
                waypoints = waypoints.dropLast(1)
            }
            else -> {
                val points = waypoints
                val manual = segments[index - 1].manual && segments[index].manual
                job = viewModelScope.launch {
                    busy = true
                    try {
                        val joined = leg(points[index - 1], points[index + 1], manual)
                        segments = segments.toMutableList().also {
                            it[index - 1] = joined
                            it.removeAt(index)
                        }
                        waypoints = points.toMutableList().also { it.removeAt(index) }
                    } catch (e: RoutingException) {
                        error = e.error
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    fun changeActivity(newActivity: ActivityType) {
        if (newActivity == activity) return
        val previous = activity to allowUnpaved
        activity = newActivity
        rerouteAll(previous)
    }

    fun changeAllowUnpaved(allow: Boolean) {
        if (allow == allowUnpaved) return
        val previous = activity to allowUnpaved
        allowUnpaved = allow
        rerouteAll(previous)
    }

    /** A different profile means different roads: every stretch is routed again, or the change is undone. */
    private fun rerouteAll(previous: Pair<ActivityType, Boolean>) {
        error = null
        val points = waypoints
        if (points.size < 2) return
        job?.cancel()
        job = viewModelScope.launch {
            busy = true
            try {
                val routed = ArrayList<RoutedSegment>()
                // Hand-drawn stretches have no roads to choose again: they stay as they are.
                for (i in 1 until points.size) {
                    val kept = segments.getOrNull(i - 1)?.takeIf { it.manual }
                    routed += kept ?: routingRepository.route(points[i - 1], points[i], profile)
                }
                segments = routed
            } catch (e: RoutingException) {
                activity = previous.first
                allowUnpaved = previous.second
                error = e.error
            } finally {
                busy = false
            }
        }
    }

    fun undo() {
        if (busy) return
        error = null
        when {
            segments.isNotEmpty() -> {
                segments = segments.dropLast(1)
                waypoints = waypoints.dropLast(1)
            }
            waypoints.isNotEmpty() -> waypoints = emptyList()
        }
    }

    fun clear() {
        job?.cancel()
        busy = false
        error = null
        segments = emptyList()
        waypoints = emptyList()
    }

    fun canReturnToStart(): Boolean {
        val first = waypoints.firstOrNull() ?: return false
        val last = waypoints.last()
        return waypoints.size >= 3 && haversineMeters(first.lat, first.lon, last.lat, last.lon) > RETURN_MIN_M
    }

    fun returnToStart() {
        if (busy || !canReturnToStart()) return
        error = null
        extend(waypoints.last(), waypoints.first())
    }

    fun clearError() {
        error = null
    }

    fun defaultName(): String =
        "Route ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"

    /**
     * Saves the route under [name]; with [toFolder] also writes its GPX into the folder chosen in
     * Settings. The saved route (with its new id) is handed back for the caller to open or share.
     */
    fun save(name: String, toFolder: Boolean, onSaved: (Route) -> Unit) {
        val route = assembleRoute(name.trim().ifEmpty { defaultName() }, activity, segments, System.currentTimeMillis(), allowUnpaved) ?: return
        viewModelScope.launch {
            val id = routeRepository.saveRoute(route)
            val saved = route.copy(id = id)
            if (toFolder) writeToFolder(saved)
            onSaved(saved)
        }
    }

    private suspend fun writeToFolder(route: Route) = GpxFolderExport.save(appContext, settingsRepository, route, gpxFolderName)

    private companion object {
        // One zoom level more than before: twice as close.
        const val DEFAULT_ZOOM = 15.0
        const val DEFAULT_COUNTRY_ZOOM = 7.0
        val DEFAULT_CENTER = 42.5 to 12.5
        const val RETURN_MIN_M = 30.0
    }
}
