package com.windrm.app.repository

import com.windrm.app.domain.ActivityType
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
) {
    val roughM: Double get() = unpavedM + trailM
}

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
     * The most direct route from [from] to [to]. With [BuilderProfile.ROAD_PAVED] the first of
     * BRouter's alternatives without unpaved or trail ways wins; if none is fully paved, the one
     * with the least unpaved is returned and the caller decides whether to accept it.
     */
    suspend fun route(from: LatLon, to: LatLon, profile: BuilderProfile): RoutedSegment {
        if (profile != BuilderProfile.ROAD_PAVED) return fetch(from, to, profile, 0)
        var best: RoutedSegment? = null
        for (index in 0..MAX_ALTERNATIVE_INDEX) {
            val segment = try {
                fetch(from, to, profile, index)
            } catch (e: RoutingException) {
                // The first request failing is the real error; later ones just mean there are no more alternatives.
                if (index == 0) throw e else break
            }
            if (!segment.surfaceKnown || segment.roughM <= PAVED_EPSILON_M) return segment
            if (best == null || segment.roughM < best.roughM) best = segment
        }
        return best ?: throw RoutingException(RoutingError.NO_ROUTE)
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
            when (classifyWayTags(row.getOrNull(tagsColumn)?.jsonPrimitive?.content.orEmpty())) {
                SurfaceClass.PAVED -> paved += distance
                SurfaceClass.UNPAVED -> unpaved += distance
                SurfaceClass.TRAIL -> trail += distance
            }
        }
    }
    return RoutedSegment(points, paved, unpaved, trail, surfaceKnown = paved + unpaved + trail > 0.0)
}

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
