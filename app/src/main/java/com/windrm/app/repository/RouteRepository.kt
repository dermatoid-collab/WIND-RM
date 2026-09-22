package com.windrm.app.repository

import com.windrm.app.db.RouteDao
import com.windrm.app.db.RouteEntity
import com.windrm.app.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouteRepository(private val dao: RouteDao) {

    fun observeRoutes(): Flow<List<Route>> =
        dao.observeAll().map { entities -> entities.map { it.toRoute() } }

    suspend fun getRoute(id: Long): Route? = dao.getById(id)?.toRoute()

    suspend fun saveRoute(route: Route): Long = dao.insert(RouteEntity.fromRoute(route))

    /** Avoids duplicate imports when the same Strava activity is imported twice. */
    suspend fun findByStravaActivityId(activityId: Long): Route? =
        dao.getByStravaActivityId(activityId)?.toRoute()

    suspend fun deleteRoute(route: Route) {
        dao.deleteById(route.id)
    }
}
