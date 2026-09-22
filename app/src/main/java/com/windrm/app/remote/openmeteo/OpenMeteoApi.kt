package com.windrm.app.remote.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = WEATHER_HOURLY_PARAMS,
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("timezone") timezone: String = "UTC",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
    ): List<OpenMeteoResponse>

    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = AIR_QUALITY_HOURLY_PARAMS,
        @Query("timezone") timezone: String = "UTC",
    ): List<OpenMeteoResponse>

    companion object {
        const val WEATHER_BASE_URL = "https://api.open-meteo.com/"
        const val AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"

        const val WEATHER_HOURLY_PARAMS = "temperature_2m,apparent_temperature,precipitation_probability," +
            "precipitation,cloud_cover,wind_speed_10m,wind_gusts_10m,wind_direction_10m,uv_index," +
            "relative_humidity_2m,dew_point_2m,is_day"
        const val AIR_QUALITY_HOURLY_PARAMS = "pm2_5,pm10,ozone,european_aqi"
    }
}
