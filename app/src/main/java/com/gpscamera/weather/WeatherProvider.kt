package com.gpscamera.weather

import android.util.Log
import com.gpscamera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class WeatherProvider {

    private var cachedKey: String? = null
    private var cachedAtMs: Long = 0L
    private var cachedTemperatureC: Double? = null

    suspend fun fetchTemperatureC(latitude: Double, longitude: Double): Double? {
        val key = String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
        val now = System.currentTimeMillis()
        if (key == cachedKey && now - cachedAtMs < CACHE_TTL_MS) return cachedTemperatureC

        val temperature = downloadTemperature(latitude, longitude)
        if (temperature != null) {
            cachedKey = key
            cachedAtMs = now
            cachedTemperatureC = temperature
        }
        return temperature
    }

    private suspend fun downloadTemperature(
        latitude: Double,
        longitude: Double
    ): Double? = runInterruptible(Dispatchers.IO) {
        val query = String.format(
            Locale.US,
            "latitude=%.6f&longitude=%.6f&current=temperature_2m",
            latitude,
            longitude
        )
        val url = "$ENDPOINT?${URLEncoder.encode(query, Charsets.UTF_8.name()).replace("%3D", "=").replace("%26", "&")}"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = true
                setRequestProperty(
                    "User-Agent",
                    "GpsCamera/${BuildConfig.VERSION_NAME} (Android; +https://github.com/naveenneog/GpsCamera)"
                )
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Open-Meteo request failed: HTTP $status")
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optJSONObject("current")
                    ?.takeIf { it.has("temperature_2m") }
                    ?.getDouble("temperature_2m")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Open-Meteo network failure: ${e.message}")
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Open-Meteo response parse failure", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "WeatherProvider"
        private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val TIMEOUT_MS = 3_000
        private const val CACHE_TTL_MS = 15L * 60L * 1_000L
    }
}
