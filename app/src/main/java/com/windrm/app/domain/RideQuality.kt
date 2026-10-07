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

/**
 * Limits for the ride traffic light, editable in Settings. A value reaches yellow at its yellow limit and red
 * once past its red limit; cold limits work downwards (yellow below, red below).
 */
data class RideThresholds(
    val feelsColdYellowC: Double = 10.0,
    val feelsColdRedC: Double = 4.0,
    val feelsHotYellowC: Double = 28.0,
    val feelsHotRedC: Double = 33.0,
    val rainProbYellowPct: Double = 30.0,
    val rainProbRedPct: Double = 60.0,
    val rainYellowMmH: Double = 0.2,
    val rainRedMmH: Double = 1.0,
    val windYellowKmh: Double = 20.0,
    val windRedKmh: Double = 35.0,
    val gustYellowKmh: Double = 35.0,
    val gustRedKmh: Double = 50.0,
    val aqiYellow: Double = 40.0,
    val aqiRed: Double = 60.0,
    val uvYellow: Double = 6.0,
    val uvRed: Double = 8.0,
    val dewPointYellowC: Double = 18.0,
    val dewPointRedC: Double = 22.0,
    val iceMaxTempC: Double = 2.0,
)

/** Traffic light for the whole ride: the worst level among all metrics, with what triggered it. */
data class RideQuality(val level: RideLevel, val issues: List<RideIssue>)

/**
 * Checks every forecast point against the cycling [RideThresholds] (green / yellow / red) for feels-like
 * temperature, rain probability and intensity, wind, gusts, air quality, UV, daylight, ice risk and
 * mugginess. Each metric reports only its worst point.
 */
object RideQualityEvaluator {

    fun evaluate(result: RouteForecastResult, limits: RideThresholds = RideThresholds()): RideQuality {
        val issues = listOfNotNull(
            worst(result.points) { p ->
                val t = p.weather.feelsLikeC
                val level = when {
                    t < limits.feelsColdRedC || t > limits.feelsHotRedC -> RideLevel.RED
                    t < limits.feelsColdYellowC || t > limits.feelsHotYellowC -> RideLevel.YELLOW
                    else -> RideLevel.GREEN
                }
                Rated(level, maxOf(limits.feelsColdYellowC - t, t - limits.feelsHotYellowC)) { "Feels like ${t.roundToInt()}°C" }
            },
            worst(result.points) { p ->
                val v = p.weather.precipitationProbabilityPct
                Rated(level(v, limits.rainProbYellowPct, limits.rainProbRedPct), v) { "Rain probability ${v.roundToInt()}%" }
            },
            worst(result.points) { p ->
                val v = p.weather.precipitationMm
                Rated(level(v, limits.rainYellowMmH, limits.rainRedMmH), v) { "Rain %.1f mm/h".format(v) }
            },
            worst(result.points) { p ->
                val v = p.weather.windSpeedKmh
                Rated(level(v, limits.windYellowKmh, limits.windRedKmh), v) { "Wind ${v.roundToInt()} km/h" }
            },
            worst(result.points) { p ->
                val v = p.weather.windGustKmh
                Rated(level(v, limits.gustYellowKmh, limits.gustRedKmh), v) { "Gusts up to ${v.roundToInt()} km/h" }
            },
            worst(result.points) { p ->
                val v = p.airQuality?.europeanAqi ?: 0.0
                val level = when {
                    v > limits.aqiRed -> RideLevel.RED
                    v > limits.aqiYellow -> RideLevel.YELLOW
                    else -> RideLevel.GREEN
                }
                Rated(level, v) { "AQI ${v.roundToInt()} (${aqiLabel(v)})" }
            },
            worst(result.points) { p ->
                val v = p.weather.uvIndex
                Rated(level(v, limits.uvYellow, limits.uvRed), v) { "UV index %.1f".format(v) }
            },
            worst(result.points) { p ->
                val level = light(p, result.daylight)
                Rated(level, 0.0) { if (level == RideLevel.RED) "Riding in the dark" else "Riding in civil twilight" }
            },
            worst(result.points) { p ->
                val w = p.weather
                val wet = w.precipitationMm > 0.0 || (w.humidityPct >= 90 && w.temperatureC - w.dewPointC <= 1.0)
                val level = if (w.temperatureC <= limits.iceMaxTempC && wet) RideLevel.RED else RideLevel.GREEN
                Rated(level, -w.temperatureC) { "Ice risk: ${w.temperatureC.roundToInt()}°C and wet" }
            },
            worst(result.points) { p ->
                val v = p.weather.dewPointC
                Rated(level(v, limits.dewPointYellowC, limits.dewPointRedC), v) { "Muggy: dew point ${v.roundToInt()}°C" }
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
