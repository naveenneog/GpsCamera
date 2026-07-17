package com.gpscamera.camera

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the in-app gallery surfaces BOTH stamped photos (Pictures/GPSCamera) and
 * recorded videos (Movies/GPSCamera), each flagged with the correct media type.
 */
@RunWith(AndroidJUnit4::class)
class GalleryRepositoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver

    @Test
    fun load_includesBothPhotosAndVideos() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val tag = System.currentTimeMillis()
        val photoUri = insert(video = false, name = "GAL_TEST_${tag}.jpg")
        val videoUri = insert(video = true, name = "GAL_TEST_${tag}.mp4")
        try {
            val items = GalleryRepository(context).load()

            // Match by media ID: load() builds URIs on the VOLUME_EXTERNAL authority while
            // the inserts use VOLUME_EXTERNAL_PRIMARY, so compare the row id, not the full URI.
            val photoId = ContentUris.parseId(photoUri)
            val videoId = ContentUris.parseId(videoUri)
            val photo = items.firstOrNull { ContentUris.parseId(it.uri) == photoId && !it.isVideo }
            val video = items.firstOrNull { ContentUris.parseId(it.uri) == videoId && it.isVideo }

            assertThat(photo).isNotNull()
            assertThat(video).isNotNull()
        } finally {
            resolver.delete(photoUri, null, null)
            resolver.delete(videoUri, null, null)
        }
    }

    private fun insert(video: Boolean, name: String): Uri {
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relative = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/${PhotoSaver.FOLDER}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("insert returned null")
        resolver.openOutputStream(uri)?.use { it.write(ByteArray(16)) }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
