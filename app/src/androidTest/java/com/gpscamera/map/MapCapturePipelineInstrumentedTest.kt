package com.gpscamera.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.gpscamera.camera.PhotoSaver
import com.gpscamera.camera.PhotoStamper
import com.gpscamera.model.GeoFix
import com.gpscamera.util.GpsFormat
import com.gpscamera.weather.WeatherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MapCapturePipelineInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun onlineMap_isFetchedBurnedIntoPhotoAndSaved() {
        runBlocking {
            assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

            val temperature = withTimeout(10_000L) {
                WeatherProvider().fetchTemperatureC(12.978361, 77.599380)
            }
            Log.i(TAG, "Open-Meteo temperature=$temperature")
            val capturedAt = System.currentTimeMillis()
            val fix = GeoFix(
                latitude = 12.978361,
                longitude = 77.599380,
                altitude = 842.0,
                accuracyM = 5f,
                address = "MG Road, Bengaluru South, Karnataka, India, 560001",
                locality = "Bengaluru South",
                adminArea = "Karnataka",
                countryName = "India",
                countryCode = "IN",
                postalCode = "560001",
                temperatureC = temperature ?: 26.0,
                timestampMs = capturedAt
            )
            File(context.cacheDir, "osm_tiles").deleteRecursively()
            val map = withTimeout(60_000L) {
                StaticMapProvider(context).fetch(fix.latitude, fix.longitude)
            }
            assertThat(map).isNotNull()
            val fetchedMap = requireNotNull(map)

            saveAndVerify(
                width = 1600,
                height = 1200,
                fix = fix,
                map = fetchedMap,
                mapX = 330 until 560,
                mapY = 910 until 1140,
                textX = 620 until 1280,
                textY = 900 until 1140,
                label = "LANDSCAPE"
            )
            saveAndVerify(
                width = 1200,
                height = 1600,
                fix = fix.copy(timestampMs = capturedAt + 2_000L),
                map = fetchedMap,
                mapX = 100 until 320,
                mapY = 1310 until 1530,
                textX = 380 until 1050,
                textY = 1290 until 1530,
                label = "PORTRAIT"
            )
        }
    }

    private suspend fun saveAndVerify(
        width: Int,
        height: Int,
        fix: GeoFix,
        map: Bitmap,
        mapX: IntRange,
        mapY: IntRange,
        textX: IntRange,
        textY: IntRange,
        label: String
    ) {
        val base = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(210, 215, 220))
        }
        val stamped = PhotoStamper.stamp(
            src = base,
            details = GpsFormat.buildStampDetails(fix),
            mapBitmap = map
        )
        val uri = PhotoSaver(context).save(stamped, fix)
        val decoded = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it)
        }
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isEqualTo(width)
        assertThat(decoded.height).isEqualTo(height)
        assertMapRegionIsNonUniform(decoded, mapX, mapY)
        assertTextRegionIsVisible(decoded, textX, textY)
        Log.i(TAG, "$label=${PhotoSaver.fileName(fix.timestampMs)} uri=$uri")
    }

    private fun assertMapRegionIsNonUniform(
        bitmap: Bitmap,
        xRange: IntRange,
        yRange: IntRange
    ) {
        val colors = HashSet<Int>()
        for (y in yRange step 8) {
            for (x in xRange step 8) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertThat(colors.size).isGreaterThan(75)
    }

    private fun assertTextRegionIsVisible(
        bitmap: Bitmap,
        xRange: IntRange,
        yRange: IntRange
    ) {
        var brightPixels = 0
        for (y in yRange step 3) {
            for (x in xRange step 3) {
                val color = bitmap.getPixel(x, y)
                if (Color.red(color) > 210 && Color.green(color) > 210 && Color.blue(color) > 210) {
                    brightPixels++
                }
            }
        }
        assertThat(brightPixels).isGreaterThan(100)
    }

    companion object {
        private const val TAG = "MapPipelineTest"
    }
}
