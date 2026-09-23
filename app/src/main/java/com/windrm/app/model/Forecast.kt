package com.windrm.app.model

import java.time.Instant

/** Hourly weather values interpolated to a specific point in time. */
data class WeatherPoint(
    val time: Instant,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val precipitationProbabilityPct: Double,
    val precipitationMm: Double,
    val cloudCoverPct: Double,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    /** Meteorological direction the wind is blowing FROM, degrees (0 = North, 90 = East). */
    val windDirectionDeg: Double,
    val uvIndex: Double,
    val humidityPct: Double,
    val dewPointC: Double,
    val isDay: Boolean,
)

/** Hourly air-quality values interpolated to a specific point in time. */
data class AirQualityPoint(
    val time: Instant,
    val europeanAqi: Double,
    val pm2_5: Double,
    val pm10: Double,
    val ozone: Double,
)

/** A route point enriched with its estimated arrival time and the forecast at that moment. */
data class RouteForecastPoint(
    val point: RoutePoint,
    val arrivalTime: Instant,
    val weather: WeatherPoint,
    val airQuality: AirQualityPoint?,
)

/** Sun/daylight timing for the day the ride takes place. */
data class DaylightInfo(
    val sunrise: Instant?,
    val sunset: Instant?,
    val civilSunrise: Instant?,
    val civilSunset: Instant?,
)

/** The full computed forecast for a route: per-point weather plus day-level daylight info. */
data class RouteForecastResult(
    val route: Route,
    val startTime: Instant,
    val avgSpeedKmh: Double,
    val points: List<RouteForecastPoint>,
    val daylight: DaylightInfo,
) {
    val endTime: Instant get() = points.lastOrNull()?.arrivalTime ?: startTime
    val peakAqi: AirQualityPoint? get() = points.mapNotNull { it.airQuality }.maxByOrNull { it.europeanAqi }
    val mainPollutant: String?
        get() {
            val peak = peakAqi ?: return null
            return listOf(
                "PM2.5" to peak.pm2_5,
                "PM10" to peak.pm10,
                "Ozone" to peak.ozone,
            ).maxByOrNull { it.second }?.first
        }
}

fun aqiLabel(aqi: Double): String = when {
    aqi <= 20 -> "Good"
    aqi <= 40 -> "Fair"
    aqi <= 60 -> "Moderate"
    aqi <= 80 -> "Poor"
    aqi <= 100 -> "Very Poor"
    else -> "Severe"
}
