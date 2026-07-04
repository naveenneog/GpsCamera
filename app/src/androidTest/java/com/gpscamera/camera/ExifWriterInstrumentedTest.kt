package com.gpscamera.camera

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.gpscamera.model.GeoFix
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ExifWriterInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tempJpeg(): File {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "exif_${System.nanoTime()}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    @Test
    fun writesReadableGpsCoordinates() {
        val fix = GeoFix(
            latitude = 12.978361,
            longitude = 77.599380,
            altitude = 842.0,
            accuracyM = 5f,
            timestampMs = 1_783_184_442_000L,
            address = "MG Road, Bengaluru"
        )
        val file = tempJpeg()
        try {
            ExifInterface(file.absolutePath).also {
                ExifWriter.write(it, fix)
                it.saveAttributes()
            }
            val read = ExifInterface(file.absolutePath)
            val latLong = read.latLong
            assertThat(latLong).isNotNull()
            assertThat(latLong!![0]).isWithin(1e-4).of(12.978361)
            assertThat(latLong[1]).isWithin(1e-4).of(77.599380)
            assertThat(read.getAltitude(-1.0)).isWithin(0.5).of(842.0)
            assertThat(read.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)).isNotNull()
            assertThat(read.getAttribute(ExifInterface.TAG_SOFTWARE)).isEqualTo("GPS Camera")
            assertThat(read.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("MG Road, Bengaluru")
        } finally {
            file.delete()
        }
    }

    @Test
    fun writesSouthernAndWesternHemisphere() {
        val fix = GeoFix(latitude = -33.868800, longitude = -74.006000)
        val file = tempJpeg()
        try {
            ExifInterface(file.absolutePath).also {
                ExifWriter.write(it, fix)
                it.saveAttributes()
            }
            val read = ExifInterface(file.absolutePath)
            assertThat(read.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)).isEqualTo("S")
            assertThat(read.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)).isEqualTo("W")
            val latLong = read.latLong!!
            assertThat(latLong[0]).isWithin(1e-4).of(-33.868800)
            assertThat(latLong[1]).isWithin(1e-4).of(-74.006000)
        } finally {
            file.delete()
        }
    }
}
