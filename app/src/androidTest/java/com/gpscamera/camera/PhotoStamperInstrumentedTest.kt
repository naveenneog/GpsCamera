package com.gpscamera.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.gpscamera.util.GpsFormat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoStamperInstrumentedTest {

    private val referenceDetails = GpsFormat.StampDetails(
        localityLine = "Bengaluru South, Karnataka, India",
        fullAddress = "Ramanagara, Bengaluru South, Karnataka, India, 562109",
        coordinateLine = "Lat 12.854907 , Long 77.353252",
        dateTimeLine = "07/14/26 10:04 AM",
        countryCode = "IN",
        temperatureC = 26.0
    )

    @Test
    fun stamp_preservesDimensionsAndDrawsPanel() {
        val src = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        // Fill with a known opaque colour so we can detect where the stamp panel overlays.
        src.eraseColor(Color.rgb(10, 120, 90))

        val out = PhotoStamper.stamp(
            src,
            listOf("GPS Camera", "12°58'42.10\"N  77°35'57.77\"E", "Alt 842 m   ±5 m")
        )

        assertThat(out.width).isEqualTo(600)
        assertThat(out.height).isEqualTo(800)
        assertThat(out.isMutable).isTrue()

        // Top of the image is untouched (still the fill colour).
        assertThat(out.getPixel(300, 20)).isEqualTo(Color.rgb(10, 120, 90))
        // Bottom-centre sits under the translucent panel, so the colour must differ.
        assertThat(out.getPixel(300, 770)).isNotEqualTo(Color.rgb(10, 120, 90))
    }

    @Test
    fun stamp_honoursAnchorPosition() {
        val fill = Color.rgb(10, 120, 90)
        val src = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        src.eraseColor(fill)

        // Anchor the panel near the TOP of the frame.
        val out = PhotoStamper.stamp(
            src,
            listOf("GPS Camera", "12.9784°, 77.5994°"),
            anchorX = 0.5f,
            anchorY = 0.14f
        )
        // Now the top region is covered by the panel and the bottom is clean.
        assertThat(out.getPixel(300, 60)).isNotEqualTo(fill)
        assertThat(out.getPixel(300, 780)).isEqualTo(fill)
    }

    @Test
    fun stamp_portraitPlacesMapOnLeft() {
        val base = Bitmap.createBitmap(900, 1400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 120, 90))
        }
        val mapColor = Color.rgb(190, 40, 170)
        val map = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888).apply {
            eraseColor(mapColor)
        }

        val out = PhotoStamper.stamp(base, referenceDetails, mapBitmap = map)

        assertThat(out.getPixel(160, 1270)).isEqualTo(mapColor)
    }

    @Test
    fun stamp_landscapePlacesMapOnLeft() {
        val base = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 120, 90))
        }
        val mapColor = Color.rgb(190, 40, 170)
        val map = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888).apply {
            eraseColor(mapColor)
        }

        val out = PhotoStamper.stamp(base, referenceDetails, mapBitmap = map)

        assertThat(out.getPixel(435, 770)).isEqualTo(mapColor)
    }
}
