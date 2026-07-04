package com.gpscamera.model

/**
 * An immutable GPS fix used across the app. All optional fields are null when the
 * underlying sensor did not report them, so the UI/stamp can degrade gracefully.
 */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyM: Float? = null,
    val bearingDeg: Float? = null,
    val speedMps: Float? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val address: String? = null
)
