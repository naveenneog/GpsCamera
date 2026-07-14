package com.gpscamera.util

import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Web-Mercator "slippy map" helpers (OpenStreetMap tile scheme) plus link builders.
 * Pure and deterministic so they can be unit-tested without a device or network.
 */
object SlippyMap {

    const val TILE_SIZE = 256

    /** Fractional tile X (0 .. 2^z) for a longitude. */
    fun tileXFraction(longitude: Double, zoom: Int): Double =
        (longitude + 180.0) / 360.0 * (1 shl zoom)

    /** Fractional tile Y (0 .. 2^z) for a latitude. */
    fun tileYFraction(latitude: Double, zoom: Int): Double {
        val latRad = Math.toRadians(latitude)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    /** Global pixel coordinate of a lat/lon at [zoom] (tile fraction * TILE_SIZE). */
    fun worldPixel(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> =
        tileXFraction(longitude, zoom) * TILE_SIZE to tileYFraction(latitude, zoom) * TILE_SIZE

    fun distanceMeters(
        latitudeA: Double,
        longitudeA: Double,
        latitudeB: Double,
        longitudeB: Double
    ): Double {
        val latA = Math.toRadians(latitudeA)
        val latB = Math.toRadians(latitudeB)
        val deltaLat = latB - latA
        val deltaLon = Math.toRadians(longitudeB - longitudeA)
        val haversine = sin(deltaLat / 2).let { it * it } +
            cos(latA) * cos(latB) * sin(deltaLon / 2).let { it * it }
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    /** A universal https link that opens the point in Google Maps (web or app). */
    fun mapsUrl(latitude: Double, longitude: Double): String =
        String.format(
            Locale.US,
            "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f",
            latitude, longitude
        )

    /** A geo: URI understood by any Android maps app, with a visible label. */
    fun geoUri(latitude: Double, longitude: Double, label: String? = null): String {
        val base = String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", latitude, longitude, latitude, longitude)
        return if (label.isNullOrBlank()) base else "$base(${Uri.encodeLabel(label)})"
    }

    private object Uri {
        // Minimal label encoder — keeps the geo: query readable while escaping parentheses.
        fun encodeLabel(label: String): String =
            label.replace("(", "").replace(")", "")
    }

    private const val EARTH_RADIUS_M = 6_371_000.0
}
