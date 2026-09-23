package com.windrm.app.remote.openmeteo

import kotlinx.serialization.Serializable

/**
 * Open-Meteo returns a single JSON object when one location is requested, but a JSON *array*
 * of these objects when multiple comma-separated locations are requested. WeatherRepository
 * always requests >= 2 locations so responses are consistently arrays.
 */
@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val hourly: HourlyBlock? = null,
    val daily: DailyBlock? = null,
)

/**
 * Shared shape for both the weather-forecast and air-quality hourly blocks; fields not
 * requested/returned by a given endpoint simply stay null.
 */
@Serializable
data class HourlyBlock(
    val time: List<String> = emptyList(),
    val temperature_2m: List<Double?>? = null,
    val apparent_temperature: List<Double?>? = null,
    val precipitation_probability: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    val cloud_cover: List<Double?>? = null,
    val wind_speed_10m: List<Double?>? = null,
    val wind_gusts_10m: List<Double?>? = null,
    val wind_direction_10m: List<Double?>? = null,
    val uv_index: List<Double?>? = null,
    val relative_humidity_2m: List<Double?>? = null,
    val dew_point_2m: List<Double?>? = null,
    val is_day: List<Int?>? = null,
    val pm2_5: List<Double?>? = null,
    val pm10: List<Double?>? = null,
    val ozone: List<Double?>? = null,
    val european_aqi: List<Double?>? = null,
)

@Serializable
data class DailyBlock(
    val time: List<String> = emptyList(),
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
)
