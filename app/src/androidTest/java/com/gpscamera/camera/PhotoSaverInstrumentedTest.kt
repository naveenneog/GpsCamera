package com.gpscamera.camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.gpscamera.model.GeoFix
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoSaverInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun save_writesJpegIntoGpsCameraFolderWithGpsExif() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val saver = PhotoSaver(context)
        val fix = GeoFix(
            latitude = 12.978361,
            longitude = 77.599380,
            altitude = 842.0,
            accuracyM = 5f,
            timestampMs = System.currentTimeMillis()
        )
        val stamped = PhotoStamper.stamp(
            Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888),
            listOf("GPS Camera", "test capture")
        )

        val uri = saver.save(stamped, fix)
        assertThat(uri).isNotNull()

        try {
            // 1. Stored in the dedicated Pictures/GPSCamera album with a GPS_ name.
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                ),
                null, null, null
            )!!.use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).startsWith("GPS_")
                assertThat(c.getString(1)).contains(PhotoSaver.FOLDER)
            }

            // 2. A valid, decodable JPEG was written.
            context.contentResolver.openInputStream(uri)!!.use {
                assertThat(BitmapFactory.decodeStream(it)).isNotNull()
            }

            // 3. The embedded GPS EXIF survives and reads back correctly.
            val original = MediaStore.setRequireOriginal(uri)
            context.contentResolver.openInputStream(original)!!.use { stream ->
                val latLong = ExifInterface(stream).latLong
                assertThat(latLong).isNotNull()
                assertThat(latLong!![0]).isWithin(1e-3).of(12.978361)
                assertThat(latLong[1]).isWithin(1e-3).of(77.599380)
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
