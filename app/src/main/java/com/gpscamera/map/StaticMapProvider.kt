package com.gpscamera.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import com.gpscamera.util.SlippyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * Builds a small square map thumbnail centered on a coordinate by fetching and stitching
 * OpenStreetMap raster tiles. Entirely best-effort: any network failure yields null so the
 * capture pipeline simply omits the map. Tiles are cached in-memory to avoid refetching.
 */
class StaticMapProvider {

    private val tileCache = object : LruCache<String, Bitmap>(64) {}

    suspend fun fetch(
        latitude: Double,
        longitude: Double,
        zoom: Int = 15,
        sizePx: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val n = 1 shl zoom
            val (cx, cy) = SlippyMap.worldPixel(latitude, longitude, zoom)
            val left = cx - sizePx / 2.0
            val top = cy - sizePx / 2.0

            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.rgb(0xE8, 0xEC, 0xEF))

            val tileMin = SlippyMap.TILE_SIZE
            val firstTx = floor(left / tileMin).toInt()
            val lastTx = floor((left + sizePx - 1) / tileMin).toInt()
            val firstTy = floor(top / tileMin).toInt()
            val lastTy = floor((top + sizePx - 1) / tileMin).toInt()

            var drewAny = false
            for (tx in firstTx..lastTx) {
                for (ty in firstTy..lastTy) {
                    val wx = ((tx % n) + n) % n
                    val wy = ty.coerceIn(0, n - 1)
                    val tile = tile(zoom, wx, wy) ?: continue
                    val dx = (tx * tileMin - left).toFloat()
                    val dy = (ty * tileMin - top).toFloat()
                    canvas.drawBitmap(tile, dx, dy, null)
                    drewAny = true
                }
            }
            if (!drewAny) return@withContext null

            drawPin(canvas, sizePx / 2f, sizePx / 2f, sizePx)
            drawAttribution(canvas, sizePx)
            out
        } catch (t: Throwable) {
            null
        }
    }

    private fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        tileCache.get(key)?.let { return it }
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "GpsCamera/1.0 (Android; contact naveenneog@github)")
            }
            conn.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.also { tileCache.put(key, it) }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, sizePx: Int) {
        val scale = sizePx / 220f
        val r = 11f * scale
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB300") }
        // Teardrop body.
        val path = Path().apply {
            moveTo(x, y + r * 1.9f)
            cubicTo(x - r, y + r * 0.4f, x - r, y - r, x, y - r)
            cubicTo(x + r, y - r, x + r, y + r * 0.4f, x, y + r * 1.9f)
            close()
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1116") }
        canvas.drawCircle(x, y - r * 0.15f, r * 0.4f, hole)
    }

    private fun drawAttribution(canvas: Canvas, sizePx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
            textSize = sizePx / 22f
        }
        val text = "© OpenStreetMap"
        val w = paint.measureText(text)
        val bg = Paint().apply { color = Color.argb(120, 255, 255, 255) }
        val pad = sizePx / 60f
        canvas.drawRect(
            sizePx - w - pad * 2, sizePx - paint.textSize - pad * 2,
            sizePx.toFloat(), sizePx.toFloat(), bg
        )
        canvas.drawText(text, sizePx - w - pad, sizePx - pad, paint)
    }
}
