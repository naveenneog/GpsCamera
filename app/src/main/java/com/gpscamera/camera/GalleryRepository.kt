package com.gpscamera.camera

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.gpscamera.model.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads back everything the app has captured for the in-app gallery: stamped photos
 * in Pictures/GPSCamera AND recorded videos in Movies/GPSCamera. Both live under a
 * "GPSCamera" folder, so one RELATIVE_PATH filter matches each collection.
 */
class GalleryRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun load(limit: Int = 300): List<GalleryItem> = withContext(Dispatchers.IO) {
        val items = ArrayList<GalleryItem>()
        items += query(video = false)
        items += query(video = true)
        items.sortByDescending { it.dateAddedSec }
        if (items.size > limit) ArrayList(items.subList(0, limit)) else items
    }

    private fun query(video: Boolean): List<GalleryItem> {
        val out = ArrayList<GalleryItem>()
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val idCol = MediaStore.MediaColumns._ID
        val dateCol = MediaStore.MediaColumns.DATE_ADDED
        val projection = arrayOf(idCol, dateCol)
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?" to arrayOf("%${PhotoSaver.FOLDER}%")
        } else {
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA + " LIKE ?" to arrayOf("%/${PhotoSaver.FOLDER}/%")
        }
        val sort = "$dateCol DESC"

        appContext.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(idCol)
            val dateIdx = c.getColumnIndexOrThrow(dateCol)
            while (c.moveToNext()) {
                out.add(
                    GalleryItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idIdx)),
                        isVideo = video,
                        dateAddedSec = c.getLong(dateIdx)
                    )
                )
            }
        }
        return out
    }
}
