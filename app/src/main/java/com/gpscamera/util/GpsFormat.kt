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

    /** "12.978361°, 77.599380°" — the compact decimal pair shown on the stamp. */
    fun formatDecimalPair(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude)

    fun formatAltitude(altitude: Double?): String =
        if (altitude == null) "Alt --" else String.format(Locale.US, "Alt %.0f m", altitude)

    fun formatAccuracy(accuracyM: Float?): String =
        if (accuracyM == null) "" else String.format(Locale.US, "±%.0f m", accuracyM)

    /** Timestamp formatted with the device time zone, e.g. "04 Jul 2026, 10:32:07 PM GMT+05:30". */
    fun formatTimestamp(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'GMT'XXX", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(timestampMs))
    }

    /**
     * The multi-line text block burned onto each photo. Order (top→bottom):
     *   1. Address (reverse-geocoded; omitted only when unavailable)
     *   2. Lat/Long in decimal degrees
     *   3. Altitude + accuracy
     *   4. Timestamp
     */
    fun buildStampLines(fix: GeoFix, timeZone: TimeZone = TimeZone.getDefault()): List<String> {
        val lines = mutableListOf<String>()
        fix.address?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        lines.add(formatDecimalPair(fix.latitude, fix.longitude))
        val altAcc = listOf(formatAltitude(fix.altitude), formatAccuracy(fix.accuracyM))
            .filter { it.isNotBlank() }
            .joinToString("   ")
        lines.add(altAcc)
        lines.add(formatTimestamp(fix.timestampMs, timeZone))
        return lines
    }
}
