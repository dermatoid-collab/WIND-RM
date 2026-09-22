package com.windrm.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
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
        )
    }
}

class RouteConverters {
    @TypeConverter
    fun fromSource(source: RouteSource): String = source.name

    @TypeConverter
    fun toSource(value: String): RouteSource = RouteSource.valueOf(value)
}
