package com.gpscamera.camera

import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.gpscamera.model.GeoFix
import com.gpscamera.util.GpsFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/** Writes standards-compliant GPS EXIF metadata so any viewer can read the coordinates. */
object ExifWriter {

    fun write(exif: ExifInterface, fix: GeoFix, software: String = "GPS Camera") {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GpsFormat.toExifRational(fix.latitude))
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GpsFormat.latitudeRef(fix.latitude))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GpsFormat.toExifRational(fix.longitude))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GpsFormat.longitudeRef(fix.longitude))
        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")

        fix.altitude?.let { alt ->
            val thousandths = (abs(alt) * 1000.0).roundToLong()
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "$thousandths/1000")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt >= 0) "0" else "1")
        }

        val utc = TimeZone.getTimeZone("UTC")
        val dateStamp = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply { timeZone = utc }
        val timeStamp = SimpleDateFormat("HH/1,mm/1,ss/1", Locale.US).apply { timeZone = utc }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, dateStamp.format(Date(fix.timestampMs)))
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timeStamp.format(Date(fix.timestampMs)))

        val local = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        exif.setAttribute(ExifInterface.TAG_DATETIME, local.format(Date(fix.timestampMs)))

        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER ?: "")
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL ?: "")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, software)
        val description = fix.address?.takeIf { it.isNotBlank() }
            ?: GpsFormat.formatDecimalPair(fix.latitude, fix.longitude)
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
        // A tappable Google Maps link, embedded so any EXIF/metadata viewer can open the spot.
        exif.setAttribute(
            ExifInterface.TAG_USER_COMMENT,
            com.gpscamera.util.SlippyMap.mapsUrl(fix.latitude, fix.longitude)
        )
    }
}
