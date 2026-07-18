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

    private var cached: Bitmap? = null
    private var cachedKey: String? = null
    private var cachedW = 0
    private var cachedH = 0

    /** Feed the latest location/map. Cheap; called from the UI whenever the fix changes. */
    fun update(details: GpsFormat.StampDetails?, map: Bitmap?) {
        this.details = details
        this.map = map
    }

    fun hasContent(): Boolean = details != null

    /**
     * A transparent [width]x[height] bitmap with the stamp panel drawn near the bottom,
     * rebuilt only when the content or the frame size changes.
     */
    @Synchronized
    fun bitmapFor(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val d = details ?: return null
        val m = map
        val key = buildString {
            append(d.localityLine); append('|')
            append(d.fullAddress); append('|')
            append(d.coordinateLine); append('|')
            append(d.dateTimeLine); append('|')
            append(d.countryCode); append('|')
            append(d.temperatureC); append('|')
            append(System.identityHashCode(m))
        }
        val current = cached
        if (current != null && !current.isRecycled &&
            key == cachedKey && width == cachedW && height == cachedH
        ) {
            return current
        }
        val base = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PhotoStamper.stamp(base, d, m, anchorX = 0.5f, anchorY = 0.9f)
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
