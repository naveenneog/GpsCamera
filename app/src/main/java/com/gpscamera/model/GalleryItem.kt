package com.gpscamera.model

import android.net.Uri

/**
 * A single entry in the in-app gallery — either a stamped photo (Pictures/GPSCamera)
 * or a recorded video (Movies/GPSCamera). [isVideo] drives the thumbnail badge, the
 * viewer (still image vs. player), and how the location is read back (EXIF vs. ISO-6709).
 */
data class GalleryItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAddedSec: Long = 0L
)
