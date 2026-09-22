package com.windrm.app.remote.strava

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface StravaApi {

    @GET("oauth/token")
    suspend fun exchangeCodeForToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("code") code: String,
        @Query("grant_type") grantType: String = "authorization_code",
    ): StravaTokenResponse

    @GET("oauth/token")
    suspend fun refreshToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("refresh_token") refreshToken: String,
        @Query("grant_type") grantType: String = "refresh_token",
    ): StravaTokenResponse

    @GET("api/v3/athlete/activities")
    suspend fun listActivities(
        @Header("Authorization") bearerToken: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<StravaActivitySummary>

    @GET("api/v3/activities/{id}/streams")
    suspend fun getActivityStreams(
        @Header("Authorization") bearerToken: String,
        @Path("id") activityId: Long,
        @Query("keys") keys: String = "latlng,altitude,time",
        @Query("key_by_type") keyByType: Boolean = true,
    ): StravaStreamSet

    companion object {
        const val API_BASE_URL = "https://www.strava.com/"
        const val AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
        const val SCOPE = "activity:read_all"
    }
}
