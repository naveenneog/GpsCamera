package com.gpscamera.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.gpscamera.model.GeoFix
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Precise GPS + reverse-geocoding.
 *
 * Location is sourced from BOTH Google Play Services fused location AND the platform
 * [LocationManager] (GPS + network). Merging the two means the app keeps working on
 * devices without Play Services and reflects raw GPS fixes immediately.
 */
class LocationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** A hot stream of high-accuracy fixes from every available provider. */
    fun locationUpdates(): Flow<GeoFix> = merge(fusedUpdates(), managerUpdates())

    @SuppressLint("MissingPermission")
    private fun fusedUpdates(): Flow<GeoFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGeoFix()) }
            }
        }

        try {
            fused.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it.toGeoFix()) } }
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (t: Throwable) {
            // Play Services missing/unavailable — the LocationManager stream still supplies fixes.
        }
        awaitClose { runCatching { fused.removeLocationUpdates(callback) } }
    }

    @SuppressLint("MissingPermission")
    private fun managerUpdates(): Flow<GeoFix> = callbackFlow {
        val listener = LocationListener { location -> trySend(location.toGeoFix()) }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.getLastKnownLocation(provider)?.let { trySend(it.toGeoFix()) }
                    locationManager.requestLocationUpdates(
                        provider, 1000L, 0f, listener, Looper.getMainLooper()
                    )
                }
            }
        }
        awaitClose { runCatching { locationManager.removeUpdates(listener) } }
    }

    /** One-shot best-effort current fix (used at capture time to guarantee freshness). */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(): GeoFix? {
        val fromFused = try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()?.toGeoFix()
                ?: fused.lastLocation.await()?.toGeoFix()
        } catch (t: Throwable) {
            null
        }
        val fromManager = runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p ->
                    if (locationManager.isProviderEnabled(p)) {
                        locationManager.getLastKnownLocation(p)
                    } else null
                }
                .maxByOrNull { it.time }
                ?.toGeoFix()
        }.getOrNull()

        return mostRecent(fromFused, fromManager)
    }

    private fun mostRecent(a: GeoFix?, b: GeoFix?): GeoFix? = when {
        a == null -> b
        b == null -> a
        else -> if (b.timestampMs >= a.timestampMs) b else a
    }

    /** Best-effort reverse geocode. Returns null when no backend/network is available. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = try {
        if (!Geocoder.isPresent()) null
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                Geocoder(appContext, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1) { results ->
                        cont.resume(results.firstOrNull()?.let(::formatAddress))
                    }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                Geocoder(appContext, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()?.let(::formatAddress)
            }
        }
    } catch (t: Throwable) {
        null
    }

    private fun formatAddress(a: android.location.Address): String {
        val parts = (0..a.maxAddressLineIndex).map { a.getAddressLine(it) }
        return if (parts.isNotEmpty()) parts.joinToString(", ")
        else listOfNotNull(a.locality, a.adminArea, a.countryName).joinToString(", ")
    }
}

private fun Location.toGeoFix(): GeoFix = GeoFix(
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else null,
    accuracyM = if (hasAccuracy()) accuracy else null,
    bearingDeg = if (hasBearing()) bearing else null,
    speedMps = if (hasSpeed()) speed else null,
    timestampMs = if (time > 0) time else System.currentTimeMillis()
)
