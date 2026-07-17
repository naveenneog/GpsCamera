package com.gpscamera.util

import com.gpscamera.model.GeoFix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure GPS formatting helpers. Everything here is deterministic and side-effect free
 * so it can be exhaustively unit-tested without an Android device.
 */
object GpsFormat {

    data class Dms(val degrees: Int, val minutes: Int, val seconds: Double)
    data class StampDetails(
        val localityLine: String,
        val fullAddress: String?,
        val coordinateLine: String,
        val dateTimeLine: String,
        val noteLine: String = NOTE_LINE,
        val countryCode: String?,
        val temperatureC: Double?
    )

    /** EXIF latitude reference: "N" for the northern hemisphere, "S" otherwise. */
    fun latitudeRef(latitude: Double): String = if (latitude >= 0) "N" else "S"

    /** EXIF longitude reference: "E" for the eastern hemisphere, "W" otherwise. */
    fun longitudeRef(longitude: Double): String = if (longitude >= 0) "E" else "W"

    /** Convert a signed decimal coordinate into positive degrees/minutes/seconds. */
    fun toDms(coordinate: Double): Dms {
        val absolute = abs(coordinate)
        val degrees = absolute.toInt()
        val minutesFull = (absolute - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return Dms(degrees, minutes, seconds)
    }

    /** Human readable DMS, e.g. 12°58'42.10"N. */
    fun formatDms(coordinate: Double, isLatitude: Boolean): String {
        val dms = toDms(coordinate)
        val ref = if (isLatitude) latitudeRef(coordinate) else longitudeRef(coordinate)
        return String.format(
            Locale.US,
            "%d°%02d'%05.2f\"%s",
            dms.degrees, dms.minutes, dms.seconds, ref
        )
    }

    /**
     * EXIF rational form expected by [androidx.exifinterface.media.ExifInterface]:
     * "deg/1,min/1,secThousandths/1000". Sign is dropped (carried by the ref tag).
     */
    fun toExifRational(coordinate: Double): String {
        val dms = toDms(coordinate)
        val secondsThousandths = (dms.seconds * 1000.0).roundToLong()
        return "${dms.degrees}/1,${dms.minutes}/1,$secondsThousandths/1000"
    }

    /** "Lat 12.978361 , Long 77.599380" — the reference-style coordinate row. */
    fun formatDecimalPair(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "Lat %.6f , Long %.6f", latitude, longitude)

    fun formatAltitude(altitude: Double?): String =
        if (altitude == null) "Alt --" else String.format(Locale.US, "Alt %.0f m", altitude)

    fun formatAccuracy(accuracyM: Float?): String =
        if (accuracyM == null) "" else String.format(Locale.US, "±%.0f m", accuracyM)

    /** Timestamp formatted like the reference stamp, e.g. "07/14/26 10:04 AM". */
    fun formatTimestamp(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("MM/dd/yy hh:mm a", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(timestampMs))
    }

    fun formatTemperature(temperatureC: Double?): String =
        temperatureC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "--°C"

    /**
     * Parse an ISO-6709 location string (how CameraX stores GPS in a recorded MP4,
     * e.g. "+12.9784+077.5994/" or "+12.9784+077.5994+842.000/") into decimal
     * latitude/longitude. Returns null when absent or out of range.
     */
    fun parseIso6709(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val numbers = Regex("[+-]\\d+(?:\\.\\d+)?").findAll(value).map { it.value }.toList()
        if (numbers.size < 2) return null
        val lat = numbers[0].toDoubleOrNull() ?: return null
        val lon = numbers[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    fun localityLine(fix: GeoFix): String {
        val components = listOf(fix.locality, fix.adminArea, fix.countryName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        return components.joinToString(", ").ifBlank {
            fix.address?.substringBefore(",")?.trim().takeUnless { it.isNullOrBlank() }
                ?: "Location unavailable"
        }
    }

    fun buildStampDetails(
        fix: GeoFix,
        timeZone: TimeZone = TimeZone.getDefault()
    ): StampDetails = StampDetails(
        localityLine = localityLine(fix),
        fullAddress = fix.address?.trim()?.takeIf(String::isNotBlank),
        coordinateLine = formatDecimalPair(fix.latitude, fix.longitude),
        dateTimeLine = formatTimestamp(fix.timestampMs, timeZone),
        countryCode = fix.countryCode,
        temperatureC = fix.temperatureC
    )

    /**
     * The multi-line text block burned onto each photo. Order (top→bottom):
     *   1. Locality/admin area/country
     *   2. Full address (when available)
     *   3. Lat/Long in decimal degrees
     *   4. Timestamp
     *   5. Capture note
     */
    fun buildStampLines(fix: GeoFix, timeZone: TimeZone = TimeZone.getDefault()): List<String> =
        buildStampDetails(fix, timeZone).let { details ->
            buildList {
                add(details.localityLine)
                details.fullAddress?.let(::add)
                add(details.coordinateLine)
                add(details.dateTimeLine)
                add(details.noteLine)
            }
        }

    const val NOTE_LINE = "Note : Capture by GPS Camera"
}
