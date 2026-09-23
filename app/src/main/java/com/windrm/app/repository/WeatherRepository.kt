package com.windrm.app.repository

import com.windrm.app.domain.ArrivalTimeCalculator
import com.windrm.app.domain.HourlySeries
import com.windrm.app.domain.RouteSampler
import com.windrm.app.domain.parseOpenMeteoInstant
import com.windrm.app.model.AirQualityPoint
import com.windrm.app.model.CurrentWeatherSnapshot
import com.windrm.app.model.DaylightInfo
import com.windrm.app.model.Route
import com.windrm.app.model.RouteForecastPoint
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.model.WeatherPoint
import com.windrm.app.remote.openmeteo.OpenMeteoApi
import com.windrm.app.remote.openmeteo.OpenMeteoResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class WeatherRepository(
    private val weatherApi: OpenMeteoApi,
    private val airQualityApi: OpenMeteoApi,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun forecastRoute(route: Route, startTime: Instant, avgSpeedKmh: Double): RouteForecastResult = coroutineScope {
        val samples = RouteSampler.sample(route)
        val arrivalTimes = ArrivalTimeCalculator.arrivalTimes(route, samples, startTime, avgSpeedKmh)

        val startDate = dateFormatter.withZone(ZoneOffset.UTC).format(arrivalTimes.first().minusSeconds(86_400))
        val endDate = dateFormatter.withZone(ZoneOffset.UTC).format(arrivalTimes.last().plusSeconds(86_400))

        val latitudeParam = samples.joinToString(",") { it.lat.toString() }
        val longitudeParam = samples.joinToString(",") { it.lon.toString() }

        val weatherDeferred = async {
            weatherApi.forecast(latitudeParam, longitudeParam, startDate, endDate)
        }
        val airQualityDeferred = async {
            runCatching { airQualityApi.airQuality(latitudeParam, longitudeParam, startDate, endDate) }.getOrNull()
        }

        val weatherResponses = weatherDeferred.await()
        val airQualityResponses = airQualityDeferred.await()

        val forecastPoints = samples.mapIndexed { index, point ->
            val arrival = arrivalTimes[index]
            val weatherResponse = weatherResponses.getOrNull(index)
            val airResponse = airQualityResponses?.getOrNull(index)
            RouteForecastPoint(
                point = point,
                arrivalTime = arrival,
                weather = buildWeatherPoint(arrival, weatherResponse),
                airQuality = airResponse?.let { buildAirQualityPoint(arrival, it) },
            )
        }

        RouteForecastResult(
            route = route,
            startTime = startTime,
            avgSpeedKmh = avgSpeedKmh,
            points = forecastPoints,
            daylight = buildDaylightInfo(weatherResponses.firstOrNull(), startTime),
        )
    }

    /** Current conditions + a short hourly glance at [lat]/[lon], for the home screen. */
    suspend fun currentWeather(lat: Double, lon: Double, locationLabel: String, hoursAhead: Int = 6): CurrentWeatherSnapshot {
        val now = Instant.now()
        val startDate = dateFormatter.withZone(ZoneOffset.UTC).format(now)
        val endDate = dateFormatter.withZone(ZoneOffset.UTC).format(now.plusSeconds(86_400))

        // Open-Meteo returns a single JSON object (not an array) for a single location; sending
        // the same point twice keeps the response shape consistent with the rest of this class.
        val latitudeParam = "$lat,$lat"
        val longitudeParam = "$lon,$lon"

        val response = weatherApi.forecast(
            latitude = latitudeParam,
            longitude = longitudeParam,
            startDate = startDate,
            endDate = endDate,
            daily = "sunrise,sunset,temperature_2m_max,temperature_2m_min",
        ).firstOrNull()

        val daily = response?.daily
        val current = buildWeatherPoint(now, response)
        // The strip shows round clock hours (15:00, 16:00, ...) rather than "now plus N hours",
        // which would carry the exact minute/second the request happened to fire at.
        val hourlyStart = now.truncatedTo(ChronoUnit.HOURS)
        return CurrentWeatherSnapshot(
            locationLabel = locationLabel,
            current = current,
            highC = daily?.temperature_2m_max?.firstOrNull() ?: current.temperatureC,
            lowC = daily?.temperature_2m_min?.firstOrNull() ?: current.temperatureC,
            hourly = (0..hoursAhead).map { buildWeatherPoint(hourlyStart.plusSeconds(it * 3600L), response) },
        )
    }

    private fun buildWeatherPoint(arrival: Instant, response: OpenMeteoResponse?): WeatherPoint {
        val hourly = response?.hourly
        val series = HourlySeries(hourly?.time.orEmpty())
        return WeatherPoint(
            time = arrival,
            temperatureC = series.valueAt(arrival, hourly?.temperature_2m),
            feelsLikeC = series.valueAt(arrival, hourly?.apparent_temperature),
            precipitationProbabilityPct = series.valueAt(arrival, hourly?.precipitation_probability),
            precipitationMm = series.valueAt(arrival, hourly?.precipitation),
            cloudCoverPct = series.valueAt(arrival, hourly?.cloud_cover),
            windSpeedKmh = series.valueAt(arrival, hourly?.wind_speed_10m),
            windGustKmh = series.valueAt(arrival, hourly?.wind_gusts_10m),
            windDirectionDeg = series.nearestAt(arrival, hourly?.wind_direction_10m),
            uvIndex = series.valueAt(arrival, hourly?.uv_index),
            humidityPct = series.valueAt(arrival, hourly?.relative_humidity_2m),
            dewPointC = series.valueAt(arrival, hourly?.dew_point_2m),
            isDay = series.nearestIntAt(arrival, hourly?.is_day) == 1,
        )
    }

    private fun buildAirQualityPoint(arrival: Instant, response: OpenMeteoResponse): AirQualityPoint {
        val hourly = response.hourly
        val series = HourlySeries(hourly?.time.orEmpty())
        return AirQualityPoint(
            time = arrival,
            europeanAqi = series.valueAt(arrival, hourly?.european_aqi),
            pm2_5 = series.valueAt(arrival, hourly?.pm2_5),
            pm10 = series.valueAt(arrival, hourly?.pm10),
            ozone = series.valueAt(arrival, hourly?.ozone),
        )
    }

    /** Approximates civil twilight as 30 minutes before sunrise / after sunset (Open-Meteo doesn't expose it directly). */
    private fun buildDaylightInfo(response: OpenMeteoResponse?, startTime: Instant): DaylightInfo {
        val daily = response?.daily ?: return DaylightInfo(null, null, null, null)
        val startDateStr = dateFormatter.withZone(ZoneOffset.UTC).format(startTime)
        val dayIndex = daily.time.indexOf(startDateStr).let { if (it >= 0) it else 0 }
        val sunrise = daily.sunrise?.getOrNull(dayIndex)?.let(::parseOpenMeteoInstant)
        val sunset = daily.sunset?.getOrNull(dayIndex)?.let(::parseOpenMeteoInstant)
        return DaylightInfo(
            sunrise = sunrise,
            sunset = sunset,
            civilSunrise = sunrise?.minusSeconds(1800),
            civilSunset = sunset?.plusSeconds(1800),
        )
    }
}
