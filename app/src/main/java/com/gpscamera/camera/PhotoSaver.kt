package com.gpscamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.gpscamera.model.GeoFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists stamped captures into the public Pictures/GPSCamera album with GPS EXIF. */
class PhotoSaver(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        const val FOLDER = "GPSCamera"
        const val JPEG_QUALITY = 95

        fun fileName(timestampMs: Long): String {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMs))
            return "GPS_$ts.jpg"
        }
    }

    suspend fun save(bitmap: Bitmap, fix: GeoFix?): Uri = withContext(Dispatchers.IO) {
        val timestamp = fix?.timestampMs ?: System.currentTimeMillis()
        val name = fileName(timestamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bitmap, fix, name, timestamp)
        } else {
            saveViaLegacyFile(bitmap, fix, name, timestamp)
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, fix: GeoFix?, name: String, timestamp: Long): Uri {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + FOLDER
            )
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert returned null")

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        } ?: throw IllegalStateException("Unable to open output stream for $uri")

        if (fix != null) {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                ExifWriter.write(exif, fix)
                exif.saveAttributes()
            }
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveViaLegacyFile(bitmap: Bitmap, fix: GeoFix?, name: String, timestamp: Long): Uri {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FOLDER
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (fix != null) {
            val exif = ExifInterface(file.absolutePath)
            ExifWriter.write(exif, fix)
            exif.saveAttributes()
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        }
        return appContext.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: Uri.fromFile(file)
    }
}
