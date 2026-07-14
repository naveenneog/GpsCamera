package com.gpscamera.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SlippyMapTest {

    @Test
    fun tileXFraction_anchorsAtEdgesAndCenter() {
        assertThat(SlippyMap.tileXFraction(-180.0, 3)).isWithin(1e-9).of(0.0)
        assertThat(SlippyMap.tileXFraction(180.0, 3)).isWithin(1e-9).of(8.0) // 2^3
        assertThat(SlippyMap.tileXFraction(0.0, 1)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun tileYFraction_equatorIsMiddle() {
        // At the equator the Y fraction is exactly half the tile range (2^z / 2).
        assertThat(SlippyMap.tileYFraction(0.0, 1)).isWithin(1e-9).of(1.0)
        assertThat(SlippyMap.tileYFraction(0.0, 4)).isWithin(1e-6).of(8.0)
    }

    @Test
    fun tileYFraction_northIsSmallerThanSouth() {
        val north = SlippyMap.tileYFraction(45.0, 5)
        val south = SlippyMap.tileYFraction(-45.0, 5)
        assertThat(north).isLessThan(south)
    }

    @Test
    fun worldPixel_isTileFractionTimesTileSize() {
        val (px, py) = SlippyMap.worldPixel(12.978361, 77.599380, 15)
        assertThat(px).isWithin(1e-6).of(SlippyMap.tileXFraction(77.599380, 15) * 256)
        assertThat(py).isWithin(1e-6).of(SlippyMap.tileYFraction(12.978361, 15) * 256)
    }

    @Test
    fun distanceMeters_handlesNearbyAndIdenticalCoordinates() {
        assertThat(
            SlippyMap.distanceMeters(12.978361, 77.599380, 12.978361, 77.599380)
        ).isWithin(1e-6).of(0.0)
        assertThat(
            SlippyMap.distanceMeters(12.978361, 77.599380, 12.979261, 77.599380)
        ).isWithin(2.0).of(100.0)
    }

    @Test
    fun mapsUrl_isWellFormed() {
        val url = SlippyMap.mapsUrl(12.978361, 77.599380)
        assertThat(url).startsWith("https://www.google.com/maps/search/?api=1&query=")
        assertThat(url).endsWith("12.978361,77.599380")
    }

    @Test
    fun geoUri_carriesCoordinatesAndLabel() {
        val uri = SlippyMap.geoUri(12.978361, 77.599380, "MG Road (Bengaluru)")
        assertThat(uri).startsWith("geo:12.978361,77.599380?q=12.978361,77.599380")
        assertThat(uri).contains("(MG Road Bengaluru)")
    }
}
