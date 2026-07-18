package com.gpscamera.camera

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.gpscamera.model.GeoFix
import com.gpscamera.util.GpsFormat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the video overlay renderer bakes the GPS stamp into a frame-sized bitmap — the
 * content that [androidx.camera.effects.OverlayEffect] draws onto each recorded video frame.
 * Pure Canvas/Bitmap work, so it runs on the software renderer without a GPU.
 */
@RunWith(AndroidJUnit4::class)
class VideoStampOverlayInstrumentedTest {

    private val fix = GeoFix(
        latitude = 12.978361,
        longitude = 77.599380,
        altitude = 842.0,
        accuracyM = 5f,
        address = "MG Road, Bengaluru South, Karnataka, India, 560001",
        locality = "Bengaluru South",
        adminArea = "Karnataka",
        countryName = "India",
        countryCode = "IN",
        temperatureC = 26.0,
        timestampMs = 0L
    )

    @Test
    fun bitmapFor_drawsStampInBottomRegion() {
        val overlay = VideoStampOverlay()
        overlay.update(GpsFormat.buildStampDetails(fix), null)

        val w = 1280
        val h = 720
        val bmp = requireNotNull(overlay.bitmapFor(w, h)) { "overlay bitmap was null" }
        assertThat(bmp.width).isEqualTo(w)
        assertThat(bmp.height).isEqualTo(h)

        // The top strip must stay fully transparent (stamp is anchored near the bottom).
        var topOpaque = 0
        for (y in 0 until h / 3 step 8) {
            for (x in 0 until w step 8) {
                if (Color.alpha(bmp.getPixel(x, y)) != 0) topOpaque++
            }
        }
        assertThat(topOpaque).isEqualTo(0)

        // The bottom third must contain the opaque panel (many non-transparent pixels).
        var bottomOpaque = 0
        for (y in (h * 2 / 3) until h step 6) {
            for (x in 0 until w step 6) {
                if (Color.alpha(bmp.getPixel(x, y)) != 0) bottomOpaque++
            }
        }
        assertThat(bottomOpaque).isGreaterThan(200)
    }

    @Test
    fun bitmapFor_isCachedUntilContentChanges() {
        val overlay = VideoStampOverlay()
        overlay.update(GpsFormat.buildStampDetails(fix), null)
        val first = overlay.bitmapFor(640, 480)
        val same = overlay.bitmapFor(640, 480)
        assertThat(same).isSameInstanceAs(first)

        overlay.update(GpsFormat.buildStampDetails(fix.copy(timestampMs = 60_000L)), null)
        val afterChange = overlay.bitmapFor(640, 480)
        assertThat(afterChange).isNotSameInstanceAs(first)
    }

    @Test
    fun bitmapFor_returnsNullWithoutContent() {
        assertThat(VideoStampOverlay().bitmapFor(640, 480)).isNull()
    }
}
