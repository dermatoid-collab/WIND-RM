package com.windrm.app.ui.navigation

import com.windrm.app.domain.PacingMode

sealed class Destination(val route: String) {
    data object Home : Destination("home")

    data object Settings : Destination("settings")

    data object RouteBuilder : Destination("builder")

    data object RoutesList : Destination("routes/{initialTab}") {
        const val ARG_INITIAL_TAB = "initialTab"
        fun path(initialTab: Int = 0) = "routes/$initialTab"
    }

    data object RouteDetail : Destination("route/{routeId}") {
        const val ARG_ROUTE_ID = "routeId"
        fun path(routeId: Long) = "route/$routeId"
    }

    data object Forecast : Destination("forecast/{routeId}/{startEpoch}/{speed}/{pacing}/{cropStart}/{cropEnd}") {
        const val ARG_ROUTE_ID = "routeId"
        const val ARG_START_EPOCH = "startEpoch"
        const val ARG_SPEED = "speed"
        const val ARG_PACING = "pacing"
        const val ARG_CROP_START = "cropStart"
        const val ARG_CROP_END = "cropEnd"
        fun path(routeId: Long, startEpochS: Long, speedKmh: Double, pacing: PacingMode, cropStartM: Double, cropEndM: Double) =
            "forecast/$routeId/$startEpochS/$speedKmh/${pacing.name}/${cropStartM.toFloat()}/${cropEndM.toFloat()}"
    }
}
