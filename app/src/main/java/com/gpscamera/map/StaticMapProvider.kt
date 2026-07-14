package com.gpscamera.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.util.LruCache
import com.gpscamera.BuildConfig
import com.gpscamera.util.SlippyMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Builds a small square map thumbnail centered on a coordinate by fetching and stitching
 * OpenStreetMap raster tiles. Tiles are fetched in parallel, retried, and cached on disk so
 * transient mobile-network failures do not silently remove the map from captured photos.
 */
class StaticMapProvider(context: Context) {

    private val tileCache = object : LruCache<String, Bitmap>(64) {}
    private val tileLocks = ConcurrentHashMap<String, Mutex>()
    private val diskCacheDir = File(context.applicationContext.cacheDir, CACHE_DIRECTORY).apply {
        mkdirs()
    }

    suspend fun fetch(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
        sizePx: Int = DEFAULT_SIZE_PX
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

            val placements = buildList {
                for (tx in firstTx..lastTx) {
                    for (ty in firstTy..lastTy) {
                        add(
                            TilePlacement(
                                x = ((tx % n) + n) % n,
                                y = ty.coerceIn(0, n - 1),
                                drawX = (tx * tileMin - left).toFloat(),
                                drawY = (ty * tileMin - top).toFloat()
                            )
                        )
                    }
                }
            }
            val fetchedTiles = coroutineScope {
                placements.map { placement ->
                    async { placement to tile(zoom, placement.x, placement.y) }
                }.awaitAll()
            }

            var drewAny = false
            fetchedTiles.forEach { (placement, tile) ->
                if (tile != null) {
                    canvas.drawBitmap(tile, placement.drawX, placement.drawY, null)
                    drewAny = true
                }
            }
            if (!drewAny) {
                Log.w(TAG, "No map tiles available for $latitude,$longitude at zoom $zoom")
                return@withContext null
            }

            drawPin(canvas, sizePx / 2f, sizePx / 2f, sizePx)
            drawAttribution(canvas, sizePx)
            out
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to build map for $latitude,$longitude", e)
            null
        }
    }

    private suspend fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        tileCache.get(key)?.let { return it }

        val lock = tileLocks.getOrPut(key) { Mutex() }
        return lock.withLock {
            tileCache.get(key)?.let { return@withLock it }
            val cacheFile = File(diskCacheDir, TileFetchPolicy.cacheFileName(z, x, y))
            val metadataFile = metadataFileFor(cacheFile)
            readCachedTile(cacheFile, freshOnly = true)?.let {
                tileCache.put(key, it)
                return@withLock it
            }
            var metadata = if (cacheFile.isFile) {
                readMetadata(metadataFile)
            } else {
                metadataFile.delete()
                null
            }

            for (attempt in 0 until TileFetchPolicy.MAX_ATTEMPTS) {
                var result = downloadTile(z, x, y, metadata)
                if (result.notModified) {
                    val cached = readCachedTile(cacheFile, freshOnly = false)
                    if (cached != null) {
                        cacheFile.setLastModified(System.currentTimeMillis())
                        metadataFile.setLastModified(System.currentTimeMillis())
                        tileCache.put(key, cached)
                        return@withLock cached
                    }
                    metadataFile.delete()
                    metadata = null
                    result = downloadTile(z, x, y, metadata = null)
                }
                if (result.bitmap != null) {
                    if (writeCachedTile(cacheFile, result.bytes!!)) {
                        writeMetadata(metadataFile, result.metadata)
                    }
                    tileCache.put(key, result.bitmap)
                    trimDiskCache()
                    return@withLock result.bitmap
                }
                if (!result.retryable || attempt == TileFetchPolicy.MAX_ATTEMPTS - 1) break
                delay(TileFetchPolicy.retryDelayMs(attempt))
            }

            readCachedTile(cacheFile, freshOnly = false)?.also {
                Log.w(TAG, "Using stale cached tile $key after network failure")
                tileCache.put(key, it)
            }
        }
    }

    private suspend fun downloadTile(
        z: Int,
        x: Int,
        y: Int,
        metadata: TileMetadata?
    ): TileDownload = runInterruptible(Dispatchers.IO) {
        val url = TILE_URL_TEMPLATE
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("X-Requested-With", BuildConfig.APPLICATION_ID)
                setRequestProperty("Accept", "image/png,image/*;q=0.8")
                metadata?.etag?.let { setRequestProperty("If-None-Match", it) }
                metadata?.lastModified?.let { setRequestProperty("If-Modified-Since", it) }
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_OK) {
                val bytes = connection.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    Log.w(TAG, "Tile $z/$x/$y returned undecodable image data")
                    TileDownload(retryable = true)
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Fetched tile $z/$x/$y (${bytes.size} bytes, HTTP $status)")
                    }
                    TileDownload(
                        bitmap = bitmap,
                        bytes = bytes,
                        metadata = TileMetadata(
                            etag = connection.getHeaderField("ETag"),
                            lastModified = connection.getHeaderField("Last-Modified")
                        )
                    )
                }
            } else if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Revalidated cached tile $z/$x/$y (HTTP 304)")
                TileDownload(notModified = true)
            } else {
                val retryable = TileFetchPolicy.shouldRetryHttpStatus(status)
                Log.w(
                    TAG,
                    "Tile $z/$x/$y failed: HTTP $status ${connection.responseMessage}; " +
                        "retryable=$retryable"
                )
                TileDownload(retryable = retryable)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Tile $z/$x/$y network failure: ${e.message}")
            TileDownload(retryable = true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Tile $z/$x/$y decode failure", e)
            TileDownload(retryable = false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readCachedTile(file: File, freshOnly: Boolean): Bitmap? {
        if (!file.isFile) return null
        if (freshOnly && !TileFetchPolicy.isFresh(file.lastModified(), System.currentTimeMillis())) {
            return null
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            Log.w(TAG, "Deleting unreadable cached tile ${file.name}")
            file.delete()
            metadataFileFor(file).delete()
        }
        return bitmap
    }

    private fun writeCachedTile(file: File, bytes: ByteArray): Boolean {
        val pending = File(file.parentFile, "${file.name}.pending")
        return try {
            pending.outputStream().buffered().use { it.write(bytes) }
            if (!pending.renameTo(file)) {
                pending.copyTo(file, overwrite = true)
                pending.delete()
            }
            file.isFile
        } catch (e: IOException) {
            pending.delete()
            Log.w(TAG, "Unable to cache tile ${file.name}", e)
            false
        }
    }

    private fun readMetadata(file: File): TileMetadata? {
        if (!file.isFile) return null
        return try {
            val properties = Properties().apply {
                file.inputStream().buffered().use { load(it) }
            }
            TileMetadata(
                etag = properties.getProperty(METADATA_ETAG)?.takeIf { it.isNotBlank() },
                lastModified = properties.getProperty(METADATA_LAST_MODIFIED)
                    ?.takeIf { it.isNotBlank() }
            )
        } catch (e: IOException) {
            Log.w(TAG, "Unable to read tile metadata ${file.name}", e)
            null
        }
    }

    private fun writeMetadata(file: File, metadata: TileMetadata?) {
        if (metadata?.etag == null && metadata?.lastModified == null) {
            file.delete()
            return
        }
        try {
            val properties = Properties().apply {
                metadata.etag?.let { setProperty(METADATA_ETAG, it) }
                metadata.lastModified?.let { setProperty(METADATA_LAST_MODIFIED, it) }
            }
            file.outputStream().buffered().use { properties.store(it, null) }
        } catch (e: IOException) {
            Log.w(TAG, "Unable to cache tile metadata ${file.name}", e)
        }
    }

    private fun metadataFileFor(cacheFile: File): File =
        File(cacheFile.parentFile, "${cacheFile.name}.meta")

    private fun trimDiskCache() {
        val files = diskCacheDir.listFiles { file -> file.extension == "png" } ?: return
        if (files.size <= MAX_DISK_CACHE_TILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_CACHE_TILES)
            .forEach {
                metadataFileFor(it).delete()
                it.delete()
            }
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, sizePx: Int) {
        val scale = sizePx / DEFAULT_SIZE_PX.toFloat()
        val r = PIN_RADIUS_PX * scale
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
        val text = ATTRIBUTION
        val w = paint.measureText(text)
        val bg = Paint().apply { color = Color.argb(120, 255, 255, 255) }
        val pad = sizePx / 60f
        canvas.drawRect(
            sizePx - w - pad * 2, sizePx - paint.textSize - pad * 2,
            sizePx.toFloat(), sizePx.toFloat(), bg
        )
        canvas.drawText(text, sizePx - w - pad, sizePx - pad, paint)
    }

    private data class TilePlacement(
        val x: Int,
        val y: Int,
        val drawX: Float,
        val drawY: Float
    )

    private data class TileDownload(
        val bitmap: Bitmap? = null,
        val bytes: ByteArray? = null,
        val metadata: TileMetadata? = null,
        val retryable: Boolean = false,
        val notModified: Boolean = false
    )

    private data class TileMetadata(
        val etag: String?,
        val lastModified: String?
    )

    companion object {
        const val DEFAULT_ZOOM = 15
        const val DEFAULT_SIZE_PX = 220
        const val PIN_RADIUS_PX = 11f
        const val ATTRIBUTION = "© OpenStreetMap contributors"

        private const val TAG = "StaticMapProvider"
        private const val CACHE_DIRECTORY = "osm_tiles"
        private const val MAX_DISK_CACHE_TILES = 256
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val TILE_URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        private const val METADATA_ETAG = "etag"
        private const val METADATA_LAST_MODIFIED = "lastModified"
        private val USER_AGENT =
            "GpsCamera/${BuildConfig.VERSION_NAME} (Android; +https://github.com/naveenneog/GpsCamera)"
    }
}
