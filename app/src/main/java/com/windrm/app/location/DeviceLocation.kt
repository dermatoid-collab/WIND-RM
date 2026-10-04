package com.windrm.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import java.util.Locale

/** Shared by every screen that needs "where is this phone right now" (Home's snapshot, Settings' home-location picker). */
object DeviceLocation {
    @SuppressLint("MissingPermission") // only called from paths already gated by a location-permission check
    fun lastKnown(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) locationManager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION") // Geocoder's synchronous API; called off the main thread, kept simple for minSdk 26
    fun reverseGeocode(context: Context, lat: Double, lon: Double): String =
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: "Current location"
}
