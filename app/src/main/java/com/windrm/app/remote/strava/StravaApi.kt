package com.windrm.app.remote.strava

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface StravaApi {

    // Strava's OAuth token endpoint only accepts POST (a GET here returns HTTP 404).
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCodeForToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
    ): StravaTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
    ): StravaTokenResponse

    @GET("api/v3/athlete")
    suspend fun getAuthenticatedAthlete(
        @Header("Authorization") bearerToken: String,
    ): StravaAthlete

    /** Routes the athlete drew with Strava's Route Builder — distinct from recorded activities. */
    @GET("api/v3/athletes/{id}/routes")
    suspend fun listAthleteRoutes(
        @Header("Authorization") bearerToken: String,
        @Path("id") athleteId: Long,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<StravaRouteSummary>

    @Streaming
    @GET("api/v3/routes/{id}/export_gpx")
    suspend fun getRouteGpx(
        @Header("Authorization") bearerToken: String,
        @Path("id") routeId: Long,
    ): ResponseBody

    /** Recorded rides -- distinct from planned [StravaRouteSummary]s. */
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

    /** Strava only exposes segments the athlete has starred, not an arbitrary search. */
    @GET("api/v3/segments/starred")
    suspend fun listStarredSegments(
        @Header("Authorization") bearerToken: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<StravaSegmentSummary>

    @GET("api/v3/segments/{id}/streams")
    suspend fun getSegmentStreams(
        @Header("Authorization") bearerToken: String,
        @Path("id") segmentId: Long,
        @Query("keys") keys: String = "latlng,altitude",
        @Query("key_by_type") keyByType: Boolean = true,
    ): StravaStreamSet

    companion object {
        const val API_BASE_URL = "https://www.strava.com/"
        const val AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
        // read_all: routes/segments (general "read" family). activity:read_all: activities and
        // their streams (a separate scope family Strava gates independently).
        const val SCOPE = "read_all,activity:read_all"
    }
}
