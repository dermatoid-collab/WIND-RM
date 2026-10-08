package com.windrm.app.repository

import com.windrm.app.domain.ActivityType
import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.LatLon
import com.windrm.app.remote.brouter.BRouterApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** What a stretch of a routed track is made of. */
enum class SurfaceClass { PAVED, UNPAVED, TRAIL }

data class RoutedPoint(val lat: Double, val lon: Double, val eleM: Double?)

/** One routed stretch between two points, with how much of it is paved, unpaved or trail. */
data class RoutedSegment(
    val points: List<RoutedPoint>,
    val pavedM: Double,
    val unpavedM: Double,
    val trailM: Double,
    /** False when BRouter returned no way tags: the track is fine, but its surface can't be judged. */
    val surfaceKnown: Boolean,
    /** Where the unpaved or trail ways lie: (from, to) in metres from the start of this stretch, in BRouter's own distances. */
    val roughSpans: List<Pair<Double, Double>> = emptyList(),
) {
    val roughM: Double get() = unpavedM + trailM

    /** Cumulative length along [points] at each of them, metres. */
    private val cumulative: DoubleArray by lazy {
        DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) {
                c[i] = c[i - 1] + haversineMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
            }
        }
    }

    val lengthM: Double get() = cumulative.lastOrNull() ?: 0.0

    /** BRouter's distances against the length of the drawn line (they differ a little): multiply line metres by this. */
    private val spanScale: Double
        get() {
            val surface = pavedM + unpavedM + trailM
            return if (surfaceKnown && lengthM > 0.0 && surface > 0.0) surface / lengthM else 1.0
        }

    /** The unpaved and trail stretches as lines of their own, to draw them differently. */
    fun roughRuns(): List<List<RoutedPoint>> {
        if (roughSpans.isEmpty() || points.size < 2) return emptyList()
        val scale = spanScale
        return roughSpans.mapNotNull { (from, to) ->
            val a = from / scale
            val b = to / scale
            val run = ArrayList<RoutedPoint>()
            run += pointAt(a)
            for (i in points.indices) if (cumulative[i] > a && cumulative[i] < b) run += points[i]
            run += pointAt(b)
            if (run.size >= 2 && b > a) run else null
        }
    }

    private fun pointAt(distanceM: Double): RoutedPoint {
        val d = distanceM.coerceIn(0.0, lengthM)
        var i = java.util.Arrays.binarySearch(cumulative, d).let { if (it >= 0) it else -it - 2 }
        i = i.coerceIn(0, points.size - 2)
        val span = cumulative[i + 1] - cumulative[i]
        val t = if (span <= 0.0) 0.0 else ((d - cumulative[i]) / span).coerceIn(0.0, 1.0)
        return interpolate(points[i], points[i + 1], t)
    }

    /**
     * Cuts the stretch at the place ([lat], [lon]) lying on its line segment [index] (between points
     * [index] and [index] + 1): the part up to it, and the part from it. Surface totals follow the cut.
     */
    fun splitAt(index: Int, lat: Double, lon: Double): Pair<RoutedSegment, RoutedSegment> {
        val j = index.coerceIn(0, points.size - 2)
        val a = points[j]
        val b = points[j + 1]
        val segmentM = cumulative[j + 1] - cumulative[j]
        val t = if (segmentM <= 0.0) 0.0 else (haversineMeters(a.lat, a.lon, lat, lon) / segmentM).coerceIn(0.0, 1.0)
        val cut = RoutedPoint(lat, lon, if (a.eleM != null && b.eleM != null) a.eleM + (b.eleM - a.eleM) * t else a.eleM ?: b.eleM)
        val cutLineM = cumulative[j] + haversineMeters(a.lat, a.lon, lat, lon)
        val scale = spanScale
        val cutM = cutLineM * scale
        val totalM = cutM + (lengthM - cutLineM) * scale

        fun part(points: List<RoutedPoint>, spans: List<Pair<Double, Double>>, lengthM: Double) = RoutedSegment(
            points = points,
            pavedM = if (surfaceKnown) (lengthM - spans.sumOf { it.second - it.first }).coerceAtLeast(0.0) else 0.0,
            unpavedM = spans.sumOf { it.second - it.first },
            trailM = 0.0,
            surfaceKnown = surfaceKnown,
            roughSpans = spans,
        )
        val before = roughSpans.mapNotNull { (f, t2) -> if (f < cutM) f to minOf(t2, cutM) else null }
        val after = roughSpans.mapNotNull { (f, t2) -> if (t2 > cutM) (maxOf(f, cutM) - cutM) to (t2 - cutM) else null }
        return part(points.subList(0, j + 1) + cut, before, cutM) to part(listOf(cut) + points.subList(j + 1, points.size), after, totalM - cutM)
    }
}

private fun interpolate(a: RoutedPoint, b: RoutedPoint, t: Double) = RoutedPoint(
    a.lat + (b.lat - a.lat) * t,
    a.lon + (b.lon - a.lon) * t,
    if (a.eleM != null && b.eleM != null) a.eleM + (b.eleM - a.eleM) * t else a.eleM ?: b.eleM,
)

/**
 * BRouter profiles. All of them route by cost (distance and climb), never by popularity, so the
 * result is the most direct way. Profile names are the ones BRouter's public server ships; they
 * live here so a rename is a one-line change.
 */
enum class BuilderProfile(val brouterName: String) {
    /** Ride, paved only: the fast road-bike profile, then checked against the way tags. */
    ROAD_PAVED("fastbike"),

    /** Ride with unpaved roads allowed by the user: the touring profile accepts tracks. */
    ROAD_ANY("trekking"),

    /** Trekking: mountain hiking paths and trails. */
    TREK("hiking-mountain"),
    ;

    companion object {
        fun of(activity: ActivityType, allowUnpaved: Boolean): BuilderProfile = when {
            activity == ActivityType.TREK -> TREK
            allowUnpaved -> ROAD_ANY
            else -> ROAD_PAVED
        }
    }
}

enum class RoutingError { NO_ROUTE, NO_CONNECTION, FAILED }

class RoutingException(val error: RoutingError, cause: Throwable? = null) : Exception(error.name, cause)

/** Routes between points along real roads and trails, and says what surface each stretch has. */
class RoutingRepository(private val api: BRouterApi) {

    /**
     * The most direct route from [from] to [to]: BRouter's best one. With [BuilderProfile.ROAD_PAVED] a
     * route that has unpaved or trail ways is swapped for one of BRouter's alternatives only if that one is
     * fully paved (or at least less rough) and not much longer; otherwise the direct route stays and its
     * rough stretches are shown to the user, who can move a point or allow unpaved roads.
     */
    suspend fun route(from: LatLon, to: LatLon, profile: BuilderProfile): RoutedSegment {
        val best = fetch(from, to, profile, 0)
        if (profile != BuilderProfile.ROAD_PAVED || !best.surfaceKnown || best.roughM <= PAVED_EPSILON_M) return best
        val longest = best.lengthM * MAX_DETOUR_FACTOR + MAX_DETOUR_EXTRA_M
        var candidate: RoutedSegment? = null
        for (index in 1..MAX_ALTERNATIVE_INDEX) {
            val alternative = try {
                fetch(from, to, profile, index)
            } catch (e: RoutingException) {
                // The first request failing is the real error; later ones just mean there are no more alternatives.
                break
            }
            if (!alternative.surfaceKnown || alternative.lengthM > longest) continue
            if (alternative.roughM <= PAVED_EPSILON_M) return alternative
            if (alternative.roughM < (candidate?.roughM ?: best.roughM)) candidate = alternative
        }
        return candidate ?: best
    }

    private suspend fun fetch(from: LatLon, to: LatLon, profile: BuilderProfile, alternative: Int): RoutedSegment {
        val lonLats = "%.6f,%.6f|%.6f,%.6f".format(java.util.Locale.US, from.lon, from.lat, to.lon, to.lat)
        val json = try {
            api.route(lonLats, profile.brouterName, alternative)
        } catch (e: HttpException) {
            // BRouter answers 4xx/5xx with a plain-text reason (no way near the point, outside the data...).
            throw RoutingException(RoutingError.NO_ROUTE, e)
        } catch (e: IOException) {
            throw RoutingException(RoutingError.NO_CONNECTION, e)
        } catch (e: kotlinx.serialization.SerializationException) {
            // A plain-text error body that arrived with a success status.
            throw RoutingException(RoutingError.NO_ROUTE, e)
        }
        return parseRoute(json)
    }

    companion object {
        private const val MAX_ALTERNATIVE_INDEX = 3

        /** A paved alternative is taken over the direct route only if it is at most 10% (plus 200 m) longer. */
        private const val MAX_DETOUR_FACTOR = 1.10
        private const val MAX_DETOUR_EXTRA_M = 200.0

        /** Float noise and rounding, not a tolerance: any real unpaved stretch is longer than this. */
        private const val PAVED_EPSILON_M = 1.0
    }
}

/** Reads BRouter's GeoJSON: the track from the first feature, the surface split from its "messages" table. */
internal fun parseRoute(json: JsonObject): RoutedSegment {
    val feature = json["features"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw RoutingException(RoutingError.NO_ROUTE)
    val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
        ?: throw RoutingException(RoutingError.NO_ROUTE)
    val points = try {
        coordinates.map { c ->
            val a = c.jsonArray
            RoutedPoint(a[1].jsonPrimitive.content.toDouble(), a[0].jsonPrimitive.content.toDouble(), a.getOrNull(2)?.jsonPrimitive?.doubleOrNull)
        }
    } catch (e: Exception) {
        throw RoutingException(RoutingError.FAILED, e)
    }
    if (points.size < 2) throw RoutingException(RoutingError.NO_ROUTE)

    var paved = 0.0
    var unpaved = 0.0
    var trail = 0.0
    val spans = ArrayList<Pair<Double, Double>>()
    var cursor = 0.0
    // messages: a header row (Longitude, Latitude, Elevation, Distance, ..., WayTags, ...), then one row per way.
    runCatching {
        val rows = feature["properties"]?.jsonObject?.get("messages")?.jsonArray ?: return@runCatching
        val header = rows.first().jsonArray.map { it.jsonPrimitive.content }
        val distanceColumn = header.indexOf("Distance")
        val tagsColumn = header.indexOf("WayTags")
        if (distanceColumn < 0 || tagsColumn < 0) return@runCatching
        for (i in 1 until rows.size) {
            val row = rows[i].jsonArray
            val distance = row.getOrNull(distanceColumn)?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
            val surface = classifyWayTags(row.getOrNull(tagsColumn)?.jsonPrimitive?.content.orEmpty())
            when (surface) {
                SurfaceClass.PAVED -> paved += distance
                SurfaceClass.UNPAVED -> unpaved += distance
                SurfaceClass.TRAIL -> trail += distance
            }
            if (surface != SurfaceClass.PAVED) {
                // Ways that follow each other join into one stretch.
                val last = spans.lastOrNull()
                if (last != null && last.second >= cursor - SPAN_JOIN_M) spans[spans.lastIndex] = last.first to cursor + distance
                else spans += cursor to cursor + distance
            }
            cursor += distance
        }
    }
    return RoutedSegment(points, paved, unpaved, trail, surfaceKnown = paved + unpaved + trail > 0.0, roughSpans = spans)
}

private const val SPAN_JOIN_M = 1.0

private val PAVED_SURFACES = setOf(
    "asphalt", "paved", "concrete", "concrete:plates", "concrete:lanes", "paving_stones", "sett", "cobblestone", "chipseal", "metal",
)
private val TRAIL_HIGHWAYS = setOf("path", "bridleway", "steps")

/**
 * Surface of one way from its OSM tags ("highway=residential surface=asphalt ..."). An explicit
 * surface wins; without one the road class decides: tracks are unpaved, paths and steps are trail,
 * every other road is taken as paved, as OSM's own default assumption has it.
 */
internal fun classifyWayTags(tags: String): SurfaceClass {
    val values = HashMap<String, String>()
    for (token in tags.split(' ')) {
        val eq = token.indexOf('=')
        if (eq > 0) values[token.substring(0, eq)] = token.substring(eq + 1)
    }
    val highway = values["highway"]
    val surface = values["surface"]
    return when {
        surface != null && surface in PAVED_SURFACES -> SurfaceClass.PAVED
        surface != null -> if (highway in TRAIL_HIGHWAYS) SurfaceClass.TRAIL else SurfaceClass.UNPAVED
        highway in TRAIL_HIGHWAYS -> SurfaceClass.TRAIL
        highway == "track" -> if (values["tracktype"] == "grade1") SurfaceClass.PAVED else SurfaceClass.UNPAVED
        else -> SurfaceClass.PAVED
    }
}
