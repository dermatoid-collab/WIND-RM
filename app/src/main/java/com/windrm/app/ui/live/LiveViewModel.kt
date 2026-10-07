package com.windrm.app.ui.live

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.domain.ArrivalTimeCalculator
import com.windrm.app.domain.ElevationProfile
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.TimeProfile
import com.windrm.app.domain.TrackMatcher
import com.windrm.app.domain.cropped
import com.windrm.app.location.DeviceLocation
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.repository.RouteRepository
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is left of the route from the rider's place on it. */
data class LiveProgress(
    val distanceM: Double,
    val offTrackM: Double,
    val remainingKm: Double,
    /** Planned riding plus planned stops still to come, seconds. */
    val plannedLeftS: Long,
    /** The same corrected by the pace ridden so far; null until there is enough to judge it. */
    val correctedLeftS: Long?,
    /** Average speed over the recent stretch, km/h; null until there is enough to judge it. */
    val recentSpeedKmh: Double?,
    val ascentLeftM: Double?,
    val descentLeftM: Double?,
    /** Elevation every 20 m from here to the finish, for the profile ahead. */
    val elevationAhead: List<Double>,
    val arrived: Boolean,
)

class LiveViewModel(
    private val appContext: Context,
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
    private val routeId: Long,
    private val speedKmh: Double,
    requestedPacing: PacingMode,
    private val cropRangeM: ClosedFloatingPointRange<Double>,
) : ViewModel() {

    /** The live time model has no forecast to take the wind from: wind mode rides at the realistic still-air pace. */
    val windIgnored = requestedPacing == PacingMode.REALISTIC_WIND
    private val pacing = if (windIgnored) PacingMode.REALISTIC else requestedPacing

    var route by mutableStateOf<Route?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var mapStyle by mutableStateOf(MapStyle.OSM_STANDARD)
        private set

    /** Where the phone is (raw GPS), and how well it knows. */
    var gpsPosition by mutableStateOf<RoutePoint?>(null)
        private set
    var accuracyM by mutableStateOf<Float?>(null)
        private set

    /** The rider's place on the route; null until the first fix is placed. */
    var progress by mutableStateOf<LiveProgress?>(null)
        private set
    var matchedPoint by mutableStateOf<RoutePoint?>(null)
        private set

    /** More than one place of the route lies under the first fix (out and back, a loop): the rider picks. */
    var pendingPasses by mutableStateOf<List<TrackMatcher.Match>?>(null)
        private set

    var permissionGranted by mutableStateOf(false)
        private set
    var gpsEnabled by mutableStateOf(true)
        private set
    val totalKm: Double get() = (matcher?.totalM ?: 0.0) / 1000.0

    private var matcher: TrackMatcher? = null
    private var timeProfile: TimeProfile? = null
    private var elevation: ElevationProfile? = null
    private var lastDistanceM: Double? = null
    private var trackingJob: Job? = null

    /** (time ms, distance m) of recent fixes on the route, to read the pace actually ridden. */
    private val recent = ArrayDeque<Pair<Long, Double>>()

    init {
        viewModelScope.launch {
            runCatching {
                val settings = settingsRepository.current()
                mapStyle = settings.mapStyle
                val full = routeRepository.getRoute(routeId) ?: error("Route not found")
                val cropped = full.cropped(cropRangeM.start, cropRangeM.endInclusive)
                val samples = thin(cropped.points)
                val offsets = withContext(Dispatchers.Default) {
                    ArrivalTimeCalculator.ridingOffsets(cropped, samples, speedKmh, pacing, settings.riderProfile, cropped.activity, settings.caiProfile)
                }
                timeProfile = TimeProfile(
                    DoubleArray(samples.size) { samples[it].distanceFromStartM },
                    DoubleArray(samples.size) { offsets[it].toDouble() },
                    cropped.stops,
                )
                elevation = ElevationProfile(cropped.points)
                matcher = TrackMatcher(cropped.points)
                route = cropped
            }.onFailure { loadError = it.message ?: "Couldn't load the route" }
        }
    }

    /** Called when the location permission is known to be granted (or refused). */
    fun onPermission(granted: Boolean) {
        permissionGranted = granted
        if (granted) start() else stop()
    }

    /** Follows the GPS; call again after a pause (it never doubles up). */
    fun start() {
        if (!permissionGranted || trackingJob?.isActive == true) return
        gpsEnabled = DeviceLocation.isGpsEnabled(appContext)
        trackingJob = viewModelScope.launch {
            DeviceLocation.updates(appContext).collect { location ->
                gpsEnabled = true
                val heading = if (location.hasBearing() && location.hasSpeed() && location.speed > MIN_HEADING_SPEED_MS) location.bearing.toDouble() else null
                onFix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null, heading, location.time)
            }
        }
    }

    /** Stops the GPS: it runs only while the page is on screen. */
    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
    }

    override fun onCleared() = stop()

    private fun onFix(lat: Double, lon: Double, accuracy: Float?, headingDeg: Double?, timeMs: Long) {
        val matcher = matcher ?: return
        gpsPosition = RoutePoint(lat, lon)
        accuracyM = accuracy
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return
        if (pendingPasses != null) return

        val previous = lastDistanceM
        if (previous == null) {
            val candidates = matcher.passes(lat, lon)
            val onRoute = candidates.filter { it.offTrackM <= PASS_CHOICE_RADIUS_M }
            when {
                onRoute.size > 1 -> pendingPasses = onRoute
                else -> candidates.minByOrNull { it.offTrackM }?.let { place(it, timeMs) }
            }
        } else {
            matcher.match(lat, lon, previous, headingDeg)?.let { place(it, timeMs) }
        }
    }

    /** The rider's answer to "which pass?". */
    fun choosePass(match: TrackMatcher.Match) {
        pendingPasses = null
        place(match, System.currentTimeMillis())
    }

    private fun place(match: TrackMatcher.Match, timeMs: Long) {
        val time = timeProfile ?: return
        val elev = elevation ?: return
        val total = matcher?.totalM ?: return
        val d = match.distanceM.coerceIn(0.0, total)
        lastDistanceM = d
        matchedPoint = RoutePoint(match.lat, match.lon)

        // Pace actually ridden over the last few minutes, against what the plan gives for the same stretch.
        var factor: Double? = null
        var recentKmh: Double? = null
        if (match.offTrackM <= ON_ROUTE_M) {
            recent.addLast(timeMs to d)
            while (recent.size > 1 && timeMs - recent.first().first > PACE_WINDOW_MS) recent.removeFirst()
        }
        recent.firstOrNull()?.let { (t0, d0) ->
            val seconds = (timeMs - t0) / 1000.0
            val planned = time.ridingAt(d) - time.ridingAt(d0)
            if (seconds >= MIN_PACE_SECONDS && d - d0 >= MIN_PACE_M && planned >= MIN_PACE_SECONDS / 2) {
                factor = (seconds / planned).coerceIn(0.4, 4.0)
                recentKmh = (d - d0) / seconds * 3.6
            }
        }

        val ridingLeft = time.ridingLeft(d)
        val stopsLeft = time.stopsLeft(d)
        val climb = if (elev.hasElevation) elev.remainingClimb(d) else null
        progress = LiveProgress(
            distanceM = d,
            offTrackM = match.offTrackM,
            remainingKm = (total - d) / 1000.0,
            plannedLeftS = (ridingLeft + stopsLeft).toLong(),
            correctedLeftS = factor?.let { (ridingLeft * it + stopsLeft).toLong() },
            recentSpeedKmh = recentKmh,
            ascentLeftM = climb?.first,
            descentLeftM = climb?.second,
            elevationAhead = if (elev.hasElevation) elev.ahead(d) else emptyList(),
            arrived = total - d < ARRIVED_M,
        )
    }

    /** About one point per 50 m is plenty for the time model. */
    private fun thin(points: List<RoutePoint>): List<RoutePoint> {
        val out = ArrayList<RoutePoint>()
        var last = Double.NEGATIVE_INFINITY
        for (p in points) {
            if (p.distanceFromStartM - last >= SAMPLE_M) {
                out += p
                last = p.distanceFromStartM
            }
        }
        if (points.isNotEmpty() && out.last() !== points.last()) out += points.last()
        return out
    }

    companion object {
        const val ON_ROUTE_M = 50.0
        private const val MAX_ACCURACY_M = 100f
        private const val PASS_CHOICE_RADIUS_M = 120.0
        private const val MIN_HEADING_SPEED_MS = 1.5f
        private const val PACE_WINDOW_MS = 10 * 60_000L
        private const val MIN_PACE_SECONDS = 180.0
        private const val MIN_PACE_M = 400.0
        private const val ARRIVED_M = 30.0
        private const val SAMPLE_M = 50.0
    }
}
