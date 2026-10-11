package com.windrm.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.windrm.app.domain.ActivityType
import com.windrm.app.model.BuilderState
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import com.windrm.app.model.RouteStop
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Entity(tableName = "routes")
@TypeConverters(RouteConverters::class)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: RouteSource,
    val createdAtEpochMs: Long,
    val pointsJson: String,
    val distanceKm: Double,
    val elevationGainM: Double,
    val hasTimestamps: Boolean,
    val stravaRouteId: Long?,
    // Default matches the 1 -> 2 migration's DEFAULT 0, so Room's schema check agrees.
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    val originalDateEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "'[]'") val stopsJson: String = "[]",
    // ActivityType name; the default matches the 2 -> 3 migration.
    @ColumnInfo(defaultValue = "'RIDE'") val activity: String = "RIDE",
    // The route builder's waypoints and stretches (BuilderState as JSON); null for imported routes.
    val builderJson: String? = null,
) {
    fun toRoute(): Route = Route(
        id = id,
        name = name,
        source = source,
        createdAtEpochMs = createdAtEpochMs,
        points = json.decodeFromString(pointsJson),
        distanceKm = distanceKm,
        elevationGainM = elevationGainM,
        hasTimestamps = hasTimestamps,
        stravaRouteId = stravaRouteId,
        isFavorite = isFavorite,
        originalDateEpochMs = originalDateEpochMs,
        stops = json.decodeFromString(stopsJson),
        activity = runCatching { ActivityType.valueOf(activity) }.getOrDefault(ActivityType.RIDE),
        builderState = builderJson?.let { runCatching { json.decodeFromString<BuilderState>(it) }.getOrNull() },
    )

    companion object {
        fun fromRoute(route: Route): RouteEntity = RouteEntity(
            id = route.id,
            name = route.name,
            source = route.source,
            createdAtEpochMs = route.createdAtEpochMs,
            pointsJson = json.encodeToString(route.points),
            distanceKm = route.distanceKm,
            elevationGainM = route.elevationGainM,
            hasTimestamps = route.hasTimestamps,
            stravaRouteId = route.stravaRouteId,
            isFavorite = route.isFavorite,
            originalDateEpochMs = route.originalDateEpochMs,
            stopsJson = encodeStops(route.stops),
            activity = route.activity.name,
            builderJson = route.builderState?.let { json.encodeToString(it) },
        )

        fun encodeStops(stops: List<RouteStop>): String = json.encodeToString(stops)
    }
}

class RouteConverters {
    @TypeConverter
    fun fromSource(source: RouteSource): String = source.name

    @TypeConverter
    fun toSource(value: String): RouteSource = RouteSource.valueOf(value)
}
