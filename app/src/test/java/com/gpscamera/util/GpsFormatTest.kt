package com.gpscamera.util

import com.google.common.truth.Truth.assertThat
import com.gpscamera.model.GeoFix
import org.junit.Test
import java.util.TimeZone
import kotlin.math.abs

class GpsFormatTest {

    /** Parse an EXIF "d/den,m/den,s/den" + hemisphere ref back to signed decimal degrees. */
    private fun rationalToDecimal(rational: String, ref: String): Double {
        val (d, m, s) = rational.split(",").map { part ->
            val (num, den) = part.split("/").map { it.toDouble() }
            num / den
        }
        val magnitude = d + m / 60.0 + s / 3600.0
        return if (ref == "S" || ref == "W") -magnitude else magnitude
    }

    @Test
    fun hemisphereRefs_areCorrect() {
        assertThat(GpsFormat.latitudeRef(12.9)).isEqualTo("N")
        assertThat(GpsFormat.latitudeRef(-33.8)).isEqualTo("S")
        assertThat(GpsFormat.latitudeRef(0.0)).isEqualTo("N")
        assertThat(GpsFormat.longitudeRef(77.5)).isEqualTo("E")
        assertThat(GpsFormat.longitudeRef(-122.4)).isEqualTo("W")
    }

    @Test
    fun toDms_splitsDegreesMinutesSeconds() {
        val dms = GpsFormat.toDms(12.978361)
        assertThat(dms.degrees).isEqualTo(12)
        assertThat(dms.minutes).isEqualTo(58)
        assertThat(dms.seconds).isWithin(0.01).of(42.0996)
    }

    @Test
    fun toDms_usesAbsoluteValue() {
        val dms = GpsFormat.toDms(-33.868800)
        assertThat(dms.degrees).isEqualTo(33)
        assertThat(dms.minutes).isEqualTo(52)
        assertThat(dms.seconds).isWithin(0.01).of(7.68)
    }

    @Test
    fun formatDms_hasDegreeMinuteSecondAndRef() {
        assertThat(GpsFormat.formatDms(12.978361, isLatitude = true)).isEqualTo("12°58'42.10\"N")
        assertThat(GpsFormat.formatDms(-122.084000, isLatitude = false)).isEqualTo("122°05'02.40\"W")
    }

    @Test
    fun exifRational_roundTripsWithinTolerance() {
        val samples = listOf(
            12.978361 to 77.599380,   // Bengaluru
            -33.868800 to 151.209300, // Sydney
            40.712800 to -74.006000,  // New York
            -0.000500 to -0.000500    // near equator/meridian, southern/western
        )
        for ((lat, lon) in samples) {
            val latBack = rationalToDecimal(
                GpsFormat.toExifRational(lat), GpsFormat.latitudeRef(lat)
            )
            val lonBack = rationalToDecimal(
                GpsFormat.toExifRational(lon), GpsFormat.longitudeRef(lon)
            )
            assertThat(abs(latBack - lat)).isLessThan(1e-4)
            assertThat(abs(lonBack - lon)).isLessThan(1e-4)
        }
    }

    @Test
    fun exifRational_hasThreeCommaSeparatedRationals() {
        val parts = GpsFormat.toExifRational(12.978361).split(",")
        assertThat(parts).hasSize(3)
        parts.forEach { assertThat(it).contains("/") }
        assertThat(parts[0]).isEqualTo("12/1")
        assertThat(parts[1]).isEqualTo("58/1")
    }

    @Test
    fun formatDecimalPair_hasSixDecimals() {
        assertThat(GpsFormat.formatDecimalPair(12.978361, 77.599380))
            .isEqualTo("Lat 12.978361 , Long 77.599380")
    }

    @Test
    fun formatAltitudeAndAccuracy_handleNulls() {
        assertThat(GpsFormat.formatAltitude(null)).isEqualTo("Alt --")
        assertThat(GpsFormat.formatAltitude(842.4)).isEqualTo("Alt 842 m")
        assertThat(GpsFormat.formatAccuracy(null)).isEmpty()
        assertThat(GpsFormat.formatAccuracy(4.6f)).isEqualTo("±5 m")
    }

    @Test
    fun buildStampLines_includesAddressWhenPresent() {
        val fix = GeoFix(
            latitude = 12.978361,
            longitude = 77.599380,
            altitude = 842.0,
            accuracyM = 5f,
            timestampMs = 0L,
            address = "Ramanagara, Bengaluru South, Karnataka, India, 562109",
            locality = "Bengaluru South",
            adminArea = "Karnataka",
            countryName = "India",
            countryCode = "IN",
            temperatureC = 26.2
        )
        val lines = GpsFormat.buildStampLines(fix, TimeZone.getTimeZone("UTC"))
        assertThat(lines).hasSize(5)
        assertThat(lines[0]).isEqualTo("Bengaluru South, Karnataka, India")
        assertThat(lines[1]).isEqualTo(
            "Ramanagara, Bengaluru South, Karnataka, India, 562109"
        )
        assertThat(lines[2]).isEqualTo("Lat 12.978361 , Long 77.599380")
        assertThat(lines[3]).isEqualTo("01/01/70 12:00 AM")
        assertThat(lines[4]).isEqualTo("Note : Capture by GPS Camera")
    }

    @Test
    fun buildStampLines_omitsAddressWhenAbsent() {
        val fix = GeoFix(latitude = 1.0, longitude = 2.0, timestampMs = 0L)
        val lines = GpsFormat.buildStampLines(fix, TimeZone.getTimeZone("UTC"))
        assertThat(lines).hasSize(4)
        assertThat(lines[0]).isEqualTo("Location unavailable")
        assertThat(lines[1]).isEqualTo("Lat 1.000000 , Long 2.000000")
    }

    @Test
    fun formatTimestamp_isDeterministicForGivenZone() {
        // 2026-07-04T17:00:42Z rendered in IST (+05:30).
        val text = GpsFormat.formatTimestamp(
            1_783_184_442_000L, TimeZone.getTimeZone("GMT+05:30")
        )
        assertThat(text).isEqualTo("07/04/26 10:30 PM")
    }

    @Test
    fun formatTemperature_roundsAndHandlesMissingValue() {
        assertThat(GpsFormat.formatTemperature(26.4)).isEqualTo("26°C")
        assertThat(GpsFormat.formatTemperature(26.6)).isEqualTo("27°C")
        assertThat(GpsFormat.formatTemperature(null)).isEqualTo("--°C")
    }
}
