package com.windrm.app.di

import android.content.Context
import com.windrm.app.BuildConfig
import com.windrm.app.db.AppDatabase
import com.windrm.app.remote.openmeteo.OpenMeteoApi
import com.windrm.app.remote.strava.StravaApi
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaTokenStore
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.StravaRepository
import com.windrm.app.repository.WeatherRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Simple hand-rolled DI container: one instance per app process, no framework needed for this app's size. */
class AppContainer(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val weatherApi: OpenMeteoApi = retrofit(OpenMeteoApi.WEATHER_BASE_URL).create(OpenMeteoApi::class.java)
    private val airQualityApi: OpenMeteoApi = retrofit(OpenMeteoApi.AIR_QUALITY_BASE_URL).create(OpenMeteoApi::class.java)
    private val stravaApi: StravaApi = retrofit(StravaApi.API_BASE_URL).create(StravaApi::class.java)

    private val database = AppDatabase.getInstance(context)
    private val stravaTokenStore = StravaTokenStore(context)

    val routeRepository = RouteRepository(database.routeDao())
    val weatherRepository = WeatherRepository(weatherApi, airQualityApi)
    val stravaAuthManager = StravaAuthManager(context, stravaApi, stravaTokenStore)
    val stravaRepository = StravaRepository(stravaApi, stravaAuthManager)
}
