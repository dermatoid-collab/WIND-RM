package com.windrm.app.domain

import com.windrm.app.model.DaylightInfo
import com.windrm.app.model.RouteForecastPoint
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.model.aqiLabel
import java.time.Instant
import kotlin.math.roundToInt

enum class RideLevel { GREEN, YELLOW, RED }

/** One reason the ride isn't green: the worst point for that metric, where and when it happens. */
data class RideIssue(val level: RideLevel, val text: String, val distanceKm: Double, val time: Instant)

/** Traffic light for the whole ride: the worst level among all metrics, with what triggered it. */
data class RideQuality(val level: RideLevel, val issues: List<RideIssue>)

/**
 * Checks every forecast point against fixed cycling thresholds (green / yellow / red) for feels-like
 * temperature, rain probability and intensity, wind, gusts, air quality, UV, daylight, ice risk and
 * mugginess. Each metric reports only its worst point.
 */
object RideQualityEvaluator {

    fun evaluate(result: RouteForecastResult): RideQuality {
        val issues = listOfNotNull(
            worst(result.points) { p ->
                val t = p.weather.feelsLikeC
                val level = when {
                    t < 4 || t > 33 -> RideLevel.RED
                    t < 10 || t > 28 -> RideLevel.YELLOW
                    else -> RideLevel.GREEN
                }
                Rated(level, maxOf(10 - t, t - 28)) { "Feels like ${t.roundToInt()}°C" }
            },
            worst(result.points) { p ->
                val v = p.weather.precipitationProbabilityPct
                Rated(level(v, yellowFrom = 30.0, redAbove = 60.0), v) { "Rain probability ${v.roundToInt()}%" }
            },
            worst(result.points) { p ->
                val v = p.weather.precipitationMm
                Rated(level(v, yellowFrom = 0.2, redAbove = 1.0), v) { "Rain %.1f mm/h".format(v) }
            },
            worst(result.points) { p ->
                val v = p.weather.windSpeedKmh
                Rated(level(v, yellowFrom = 20.0, redAbove = 35.0), v) { "Wind ${v.roundToInt()} km/h" }
            },
            worst(result.points) { p ->
                val v = p.weather.windGustKmh
                Rated(level(v, yellowFrom = 35.0, redAbove = 50.0), v) { "Gusts up to ${v.roundToInt()} km/h" }
            },
            worst(result.points) { p ->
                val v = p.airQuality?.europeanAqi ?: 0.0
                val level = when {
                    v > 60 -> RideLevel.RED
                    v > 40 -> RideLevel.YELLOW
                    else -> RideLevel.GREEN
                }
                Rated(level, v) { "AQI ${v.roundToInt()} (${aqiLabel(v)})" }
            },
            worst(result.points) { p ->
                val v = p.weather.uvIndex
                Rated(level(v, yellowFrom = 6.0, redAbove = 8.0), v) { "UV index %.1f".format(v) }
            },
            worst(result.points) { p ->
                val level = light(p, result.daylight)
                Rated(level, 0.0) { if (level == RideLevel.RED) "Riding in the dark" else "Riding in civil twilight" }
            },
            worst(result.points) { p ->
                val w = p.weather
                val wet = w.precipitationMm > 0.0 || (w.humidityPct >= 90 && w.temperatureC - w.dewPointC <= 1.0)
                val level = if (w.temperatureC <= 2.0 && wet) RideLevel.RED else RideLevel.GREEN
                Rated(level, -w.temperatureC) { "Ice risk: ${w.temperatureC.roundToInt()}°C and wet" }
            },
            worst(result.points) { p ->
                val v = p.weather.dewPointC
                Rated(level(v, yellowFrom = 18.0, redAbove = 22.0), v) { "Muggy: dew point ${v.roundToInt()}°C" }
            },
        ).sortedByDescending { it.level }
        return RideQuality(issues.maxOfOrNull { it.level } ?: RideLevel.GREEN, issues)
    }

    private fun level(value: Double, yellowFrom: Double, redAbove: Double): RideLevel = when {
        value > redAbove -> RideLevel.RED
        value >= yellowFrom -> RideLevel.YELLOW
        else -> RideLevel.GREEN
    }

    /** Daylight at the moment the rider passes: civil twilight = yellow, darker = red. */
    private fun light(point: RouteForecastPoint, d: DaylightInfo): RideLevel {
        val t = point.arrivalTime
        val sunrise = d.sunrise
        val sunset = d.sunset
        if (sunrise == null || sunset == null) return if (point.weather.isDay) RideLevel.GREEN else RideLevel.RED
        val dawn = d.civilSunrise ?: sunrise.minusSeconds(1800)
        val dusk = d.civilSunset ?: sunset.plusSeconds(1800)
        return when {
            t.isBefore(dawn) || t.isAfter(dusk) -> RideLevel.RED
            t.isBefore(sunrise) || t.isAfter(sunset) -> RideLevel.YELLOW
            else -> RideLevel.GREEN
        }
    }

    /** A point's level for one metric; [score] breaks ties between points at the same level (higher = worse). */
    private class Rated(val level: RideLevel, val score: Double, val text: () -> String)

    /** The worst point for one metric (highest level, then most extreme value), or null if it stays green. */
    private fun worst(points: List<RouteForecastPoint>, rate: (RouteForecastPoint) -> Rated): RideIssue? {
        var worstPoint: RouteForecastPoint? = null
        var worstRated: Rated? = null
        for (p in points) {
            val rated = rate(p)
            if (rated.level == RideLevel.GREEN) continue
            val current = worstRated
            if (current == null || rated.level > current.level || (rated.level == current.level && rated.score > current.score)) {
                worstPoint = p
                worstRated = rated
            }
        }
        val point = worstPoint ?: return null
        val rated = worstRated ?: return null
        return RideIssue(rated.level, rated.text(), point.point.distanceFromStartM / 1000.0, point.arrivalTime)
    }
}
