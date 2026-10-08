package com.windrm.app.di

import android.content.Context
import com.windrm.app.BuildConfig
import com.windrm.app.R
import com.windrm.app.db.AppDatabase
import com.windrm.app.remote.brouter.BRouterApi
import com.windrm.app.remote.openmeteo.OpenMeteoApi
import com.windrm.app.remote.strava.StravaApi
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaTokenStore
import com.windrm.app.repository.ProfileIdStore
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.RoutingRepository
import com.windrm.app.repository.StravaRepository
import com.windrm.app.repository.WeatherRepository
import com.windrm.app.settings.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

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

    private fun retrofit(baseUrl: String, client: OkHttpClient = okHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val weatherApi: OpenMeteoApi = retrofit(OpenMeteoApi.WEATHER_BASE_URL).create(OpenMeteoApi::class.java)
    private val airQualityApi: OpenMeteoApi = retrofit(OpenMeteoApi.AIR_QUALITY_BASE_URL).create(OpenMeteoApi::class.java)
    // BRouter's public server can take a while on a long stretch: more room than the 10 s default.
    private val bRouterApi: BRouterApi = retrofit(
        BRouterApi.BASE_URL,
        okHttpClient.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build(),
    ).create(BRouterApi::class.java)
    private val stravaApi: StravaApi = retrofit(StravaApi.API_BASE_URL).create(StravaApi::class.java)

    private val database = AppDatabase.getInstance(context)
    private val stravaTokenStore = StravaTokenStore(context)

    val routeRepository = RouteRepository(database.routeDao())
    val weatherRepository = WeatherRepository(weatherApi, airQualityApi)
    val settingsRepository = SettingsRepository(context)
    val routingRepository = RoutingRepository(
        bRouterApi,
        profileText = { key ->
            val resource = when (key) {
                "ride_paved" -> R.raw.windrm_ride_paved
                "ride_any" -> R.raw.windrm_ride_any
                else -> error("No routing profile $key")
            }
            context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
        },
        elevations = { places ->
            // Open-Meteo answers for up to 100 points at a time, in order; no answer is not an error.
            runCatching {
                weatherApi.elevation(places.joinToString(",") { "%.6f".format(java.util.Locale.US, it.lat) }, places.joinToString(",") { "%.6f".format(java.util.Locale.US, it.lon) }).elevation
            }.getOrNull()
        },
        idStore = object : ProfileIdStore {
            override suspend fun get(key: String) = settingsRepository.brouterProfileId(key)
            override suspend fun put(key: String, id: String) = settingsRepository.setBrouterProfileId(key, id)
            override suspend fun note(text: String) = settingsRepository.setRoutingProfileNote(text)
        },
    )
    val stravaAuthManager = StravaAuthManager(context, stravaApi, stravaTokenStore)
    val stravaRepository = StravaRepository(stravaApi, stravaAuthManager)
}
