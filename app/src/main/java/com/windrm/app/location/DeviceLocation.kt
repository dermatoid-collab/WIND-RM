package com.windrm.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    /**
     * GPS fixes while collected (and only then): a fix every [intervalMs] or [minDistanceM], whichever comes first.
     * Needs the fine-location permission, which the caller has already checked.
     */
    @SuppressLint("MissingPermission")
    fun updates(context: Context, intervalMs: Long = 2_000L, minDistanceM: Float = 3f): Flow<Location> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, minDistanceM, listener, Looper.getMainLooper())
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }

    fun isGpsEnabled(context: Context): Boolean =
        runCatching { (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)

    @Suppress("DEPRECATION") // Geocoder's synchronous API; called off the main thread, kept simple for minSdk 26
    fun reverseGeocode(context: Context, lat: Double, lon: Double): String =
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: "Current location"
}
