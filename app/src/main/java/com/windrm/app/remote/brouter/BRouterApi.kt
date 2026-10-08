package com.windrm.app.remote.brouter

import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** BRouter's public server: one routing request per call, answered as GeoJSON (track with elevation, plus per-way "messages"). */
interface BRouterApi {

    @GET("brouter")
    suspend fun route(
        /** "lon,lat|lon,lat" -- BRouter wants longitude first. */
        @Query("lonlats") lonLats: String,
        @Query("profile") profile: String,
        /** 0 = the best route, 1..3 = the alternatives BRouter can offer for the same two points. */
        @Query("alternativeidx") alternativeIdx: Int = 0,
        @Query("format") format: String = "geojson",
    ): JsonObject

    /**
     * Uploads the text of a custom profile; the answer carries "profileid" (to pass as [route]'s profile) or "error".
     * The server keeps the profile, so one upload serves every later request.
     */
    @POST("brouter/profile")
    suspend fun uploadProfile(@Body profile: RequestBody): JsonObject

    companion object {
        const val BASE_URL = "https://brouter.de/"
    }
}
