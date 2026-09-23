package com.windrm.app.ui.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")

    data object RoutesList : Destination("routes/{initialTab}") {
        const val ARG_INITIAL_TAB = "initialTab"
        fun path(initialTab: Int = 0) = "routes/$initialTab"
    }

    data object RouteDetail : Destination("route/{routeId}") {
        const val ARG_ROUTE_ID = "routeId"
        fun path(routeId: Long) = "route/$routeId"
    }

    data object Forecast : Destination("forecast/{routeId}/{startEpoch}/{speed}") {
        const val ARG_ROUTE_ID = "routeId"
        const val ARG_START_EPOCH = "startEpoch"
        const val ARG_SPEED = "speed"
        fun path(routeId: Long, startEpochS: Long, speedKmh: Double) = "forecast/$routeId/$startEpochS/$speedKmh"
    }
}
