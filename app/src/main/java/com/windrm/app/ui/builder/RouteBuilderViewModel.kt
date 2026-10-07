package com.windrm.app.ui.builder

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.ActivityType
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
 * routed from the previous one along the most direct way; on a ride with "paved only" a stretch that
 * would use unpaved ways is held back until the user accepts it.
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

    /** Tapped points, snapped to the road network; always one more than [segments] once routing started. */
    var waypoints by mutableStateOf<List<LatLon>>(emptyList())
        private set
    var segments by mutableStateOf<List<RoutedSegment>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<RoutingError?>(null)
        private set

    /** A stretch with unpaved ways, waiting for the user to accept or drop it. */
    var pendingUnpaved by mutableStateOf<RoutedSegment?>(null)
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

    private var job: Job? = null

    val profile: BuilderProfile get() = BuilderProfile.of(activity, allowUnpaved)

    /** Paved-only rides keep unpaved ways out, so any that remain after a settings change are flagged. */
    val strictPaved: Boolean get() = profile == BuilderProfile.ROAD_PAVED

    val totalDistanceM: Double get() = segments.sumOf { s -> s.points.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }.sum() }
    val pavedM: Double get() = segments.sumOf { it.pavedM }
    val unpavedM: Double get() = segments.sumOf { it.unpavedM }
    val trailM: Double get() = segments.sumOf { it.trailM }
    val surfaceKnown: Boolean get() = segments.isNotEmpty() && segments.all { it.surfaceKnown }

    /** Unpaved or trail metres inside a route that is meant to be paved only. */
    val leftoverRoughM: Double get() = if (strictPaved) unpavedM + trailM else 0.0

    /** The finished route, or null until there are two points; the name is only a placeholder here. */
    val draft: Route? get() = assembleRoute("", activity, segments, 0L)

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            mapStyle = settings.mapStyle
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
        if (busy || pendingUnpaved != null) return
        error = null
        val target = LatLon(lat, lon)
        val from = waypoints.lastOrNull()
        if (from == null) waypoints = listOf(target) else extend(from, target)
    }

    /** Routes from the current end of the route to [to] and adds the stretch. */
    private fun extend(from: LatLon, to: LatLon) {
        job = viewModelScope.launch {
            busy = true
            try {
                val segment = routingRepository.route(from, to, profile)
                if (strictPaved && segment.surfaceKnown && segment.roughM > ROUGH_EPSILON_M) pendingUnpaved = segment else append(segment)
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

    fun acceptUnpaved() {
        pendingUnpaved?.let(::append)
        pendingUnpaved = null
    }

    fun dismissUnpaved() {
        pendingUnpaved = null
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
        pendingUnpaved = null
        error = null
        val points = waypoints
        if (points.size < 2) return
        job?.cancel()
        job = viewModelScope.launch {
            busy = true
            try {
                val routed = ArrayList<RoutedSegment>()
                for (i in 1 until points.size) routed += routingRepository.route(points[i - 1], points[i], profile)
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
        pendingUnpaved = null
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
        pendingUnpaved = null
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
        if (busy || pendingUnpaved != null || !canReturnToStart()) return
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
        val route = assembleRoute(name.trim().ifEmpty { defaultName() }, activity, segments, System.currentTimeMillis()) ?: return
        viewModelScope.launch {
            val id = routeRepository.saveRoute(route)
            val saved = route.copy(id = id)
            if (toFolder) writeToFolder(saved)
            onSaved(saved)
        }
    }

    private suspend fun writeToFolder(route: Route) = GpxFolderExport.save(appContext, settingsRepository, route, gpxFolderName)

    private companion object {
        const val DEFAULT_ZOOM = 14.0
        const val DEFAULT_COUNTRY_ZOOM = 6.0
        val DEFAULT_CENTER = 42.5 to 12.5
        const val ROUGH_EPSILON_M = 1.0
        const val RETURN_MIN_M = 30.0
    }
}
