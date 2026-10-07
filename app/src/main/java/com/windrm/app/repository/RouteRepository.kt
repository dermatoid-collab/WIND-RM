package com.windrm.app.repository

import com.windrm.app.db.RouteDao
import com.windrm.app.db.RouteEntity
import com.windrm.app.domain.ActivityType
import com.windrm.app.model.Route
import com.windrm.app.model.RouteStop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouteRepository(private val dao: RouteDao) {

    fun observeRoutes(): Flow<List<Route>> =
        dao.observeAll().map { entities -> entities.map { it.toRoute() } }

    suspend fun getRoute(id: Long): Route? = dao.getById(id)?.toRoute()

    suspend fun saveRoute(route: Route): Long = dao.insert(RouteEntity.fromRoute(route))

    /** Avoids duplicate imports when the same Strava route is imported twice. */
    suspend fun findByStravaRouteId(stravaRouteId: Long): Route? =
        dao.getByStravaRouteId(stravaRouteId)?.toRoute()

    suspend fun setFavorite(routeId: Long, favorite: Boolean) = dao.setFavorite(routeId, favorite)

    suspend fun setActivity(routeId: Long, activity: ActivityType) = dao.setActivity(routeId, activity.name)

    suspend fun setStops(routeId: Long, stops: List<RouteStop>) =
        dao.setStops(routeId, RouteEntity.encodeStops(stops.sortedBy { it.distanceM }))

    suspend fun deleteRoute(route: Route) {
        dao.deleteById(route.id)
    }
}
