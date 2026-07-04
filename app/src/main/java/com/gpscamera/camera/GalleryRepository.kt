package com.gpscamera.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads back the captures stored in Pictures/GPSCamera for the in-app gallery. */
class GalleryRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun load(limit: Int = 200): List<Uri> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Uri>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?" to arrayOf("%${PhotoSaver.FOLDER}%")
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA + " LIKE ?" to arrayOf("%/${PhotoSaver.FOLDER}/%")
        }
        val sort = MediaStore.Images.Media.DATE_ADDED + " DESC"

        appContext.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && result.size < limit) {
                result.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            }
        }
        result
    }
}
