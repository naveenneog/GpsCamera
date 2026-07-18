package com.gpscamera.camera

import android.graphics.Bitmap
import com.gpscamera.util.GpsFormat

/**
 * Produces the burned-in GPS stamp for [androidx.camera.effects.OverlayEffect] so the same
 * panel that photos get is baked into every recorded video frame.
 *
 * The stamp only changes when the location, address, temperature, map, or the (per-second)
 * timestamp changes, so the rendered bitmap is cached and reused across frames — the draw
 * callback runs at the video frame rate and must stay cheap.
 */
class VideoStampOverlay {

    @Volatile private var details: GpsFormat.StampDetails? = null
    @Volatile private var map: Bitmap? = null
    @Volatile private var anchorX: Float = 0.5f
    @Volatile private var anchorY: Float = 0.86f
    @Volatile private var userScale: Float = 1f

    private var cached: Bitmap? = null
    private var cachedKey: String? = null
    private var cachedW = 0
    private var cachedH = 0

    /** Feed the latest location/map and the on-screen block position + size. */
    fun update(
        details: GpsFormat.StampDetails?,
        map: Bitmap?,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.86f,
        userScale: Float = 1f
    ) {
        this.details = details
        this.map = map
        this.anchorX = anchorX
        this.anchorY = anchorY
        this.userScale = userScale
    }

    fun hasContent(): Boolean = details != null

    /**
     * A transparent [width]x[height] bitmap with the stamp panel drawn at the current
     * position/scale, rebuilt only when the content, placement, or frame size changes.
     */
    @Synchronized
    fun bitmapFor(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val d = details ?: return null
        val m = map
        val ax = anchorX
        val ay = anchorY
        val us = userScale
        val key = buildString {
            append(d.localityLine); append('|')
            append(d.fullAddress); append('|')
            append(d.coordinateLine); append('|')
            append(d.dateTimeLine); append('|')
            append(d.countryCode); append('|')
            append(d.temperatureC); append('|')
            append(System.identityHashCode(m)); append('|')
            append(ax); append('|'); append(ay); append('|'); append(us)
        }
        val current = cached
        if (current != null && !current.isRecycled &&
            key == cachedKey && width == cachedW && height == cachedH
        ) {
            return current
        }
        val base = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PhotoStamper.stamp(base, d, m, anchorX = ax, anchorY = ay, userScale = us)
        cached = base
        cachedKey = key
        cachedW = width
        cachedH = height
        return base
    }

    /** Drop the cached bitmap (e.g. when the effect is torn down). */
    @Synchronized
    fun clear() {
        cached = null
        cachedKey = null
        cachedW = 0
        cachedH = 0
    }
}
