package com.gpscamera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpscamera.camera.GalleryRepository
import com.gpscamera.camera.PhotoSaver
import com.gpscamera.camera.PhotoStamper
import com.gpscamera.camera.toUprightBitmap
import com.gpscamera.location.LocationRepository
import com.gpscamera.map.StaticMapProvider
import com.gpscamera.model.GeoFix
import com.gpscamera.util.GpsFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Position (0..1 of image) and size of the burned-in GPS block, controlled by the user. */
data class StampTransform(
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.68f,
    val scale: Float = 1f
)

enum class CaptureMode { PHOTO, VIDEO }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val locationRepo = LocationRepository(app)
    private val photoSaver = PhotoSaver(app)
    private val galleryRepo = GalleryRepository(app)
    private val mapProvider = StaticMapProvider()

    private val _fix = MutableStateFlow<GeoFix?>(null)
    val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private val _mapBitmap = MutableStateFlow<Bitmap?>(null)
    val mapBitmap: StateFlow<Bitmap?> = _mapBitmap.asStateFlow()

    private val _stamp = MutableStateFlow(StampTransform())
    val stamp: StateFlow<StampTransform> = _stamp.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _lastSaved = MutableStateFlow<Uri?>(null)
    val lastSaved: StateFlow<Uri?> = _lastSaved.asStateFlow()

    private val _gallery = MutableStateFlow<List<Uri>>(emptyList())
    val gallery: StateFlow<List<Uri>> = _gallery.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** null = follow the system setting; true/false = explicit user choice. */
    private val _darkTheme = MutableStateFlow<Boolean?>(null)
    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()

    private val _mode = MutableStateFlow(CaptureMode.PHOTO)
    val mode: StateFlow<CaptureMode> = _mode.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordSeconds = MutableStateFlow(0)
    val recordSeconds: StateFlow<Int> = _recordSeconds.asStateFlow()

    private var locationJob: Job? = null
    private var lastMapKey: String? = null
    private var activeRecording: Recording? = null
    private var recordTimerJob: Job? = null

    fun toggleMode() {
        if (_isRecording.value) return
        _mode.value = if (_mode.value == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
    }

    /** Begin streaming location updates. Safe to call repeatedly (idempotent). */
    fun startLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationRepo.locationUpdates().collect { fix ->
                _fix.value = fix
                maybeUpdateMap(fix)
            }
        }
    }

    private suspend fun maybeUpdateMap(fix: GeoFix) {
        val key = String.format(Locale.US, "%.4f,%.4f", fix.latitude, fix.longitude)
        if (key == lastMapKey) return
        lastMapKey = key
        mapProvider.fetch(fix.latitude, fix.longitude)?.let { _mapBitmap.value = it }
    }

    /** Map thumbnail for a capture: reuse the cached one if it matches, else fetch now. */
    private suspend fun mapForCapture(fix: GeoFix): Bitmap? {
        val key = String.format(Locale.US, "%.4f,%.4f", fix.latitude, fix.longitude)
        if (key == lastMapKey && _mapBitmap.value != null) return _mapBitmap.value
        val fetched = withTimeoutOrNull(6000L) { mapProvider.fetch(fix.latitude, fix.longitude) }
        if (fetched != null) {
            _mapBitmap.value = fetched
            lastMapKey = key
        }
        return fetched ?: _mapBitmap.value
    }

    fun refreshGallery() {
        viewModelScope.launch { _gallery.value = galleryRepo.load() }
    }

    fun consumeMessage() { _message.value = null }

    // ---- Stamp position / size, driven by drag + pinch on the preview ----

    fun setStampAnchor(anchorX: Float, anchorY: Float) {
        _stamp.value = _stamp.value.copy(
            anchorX = anchorX.coerceIn(0.12f, 0.88f),
            anchorY = anchorY.coerceIn(0.12f, 0.94f)
        )
    }

    fun scaleStampBy(factor: Float) {
        _stamp.value = _stamp.value.copy(
            scale = (_stamp.value.scale * factor).coerceIn(0.6f, 2.0f)
        )
    }

    fun resetStamp() { _stamp.value = StampTransform() }

    // ---- Theme ----

    fun toggleTheme(currentlyDark: Boolean) { _darkTheme.value = !currentlyDark }

    // ---- Video recording ----

    /** Start a new recording, or stop the active one. Video is saved to Movies/GPSCamera. */
    @SuppressLint("MissingPermission")
    fun startOrStopRecording(videoCapture: VideoCapture<Recorder>, executor: Executor) {
        val ctx = getApplication<Application>()
        if (_isRecording.value) {
            activeRecording?.stop()
            activeRecording = null
            return
        }
        val name = "GPS_VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/" + PhotoSaver.FOLDER
            )
        }
        val output = MediaStoreOutputOptions
            .Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        val hasAudio = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        try {
            var pending = videoCapture.output.prepareRecording(ctx, output)
            if (hasAudio) pending = pending.withAudioEnabled()
            activeRecording = pending.start(executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        startRecordTimer()
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        stopRecordTimer()
                        if (event.hasError()) {
                            _message.value = "Recording failed (code ${event.error})"
                        } else {
                            _message.value = "Video saved to Movies/GPSCamera"
                            refreshGallery()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            _isRecording.value = false
            _message.value = "Couldn't start recording: ${t.message}"
        }
    }

    private fun startRecordTimer() {
        _recordSeconds.value = 0
        recordTimerJob?.cancel()
        recordTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordSeconds.value = _recordSeconds.value + 1
            }
        }
    }

    private fun stopRecordTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = null
        _recordSeconds.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        activeRecording?.stop()
        recordTimerJob?.cancel()
    }

    /**
     * Take a photo end-to-end. A 20s timeout guards against camera HALs that never
     * deliver a frame (e.g. software-GPU emulators) so the shutter never hangs silently.
     */
    fun capture(imageCapture: ImageCapture, executor: Executor) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val proxy = withTimeoutOrNull(20_000L) { awaitCapture(imageCapture, executor) }
                if (proxy == null) {
                    _message.value = "Camera didn't return a frame — try again"
                    return@launch
                }
                processCapture(proxy)
            } catch (t: Throwable) {
                _message.value = "Capture failed: ${t.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun awaitCapture(
        imageCapture: ImageCapture,
        executor: Executor
    ): ImageProxy = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (cont.isActive) cont.resume(image) else image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWith(Result.failure(exception))
                }
            }
        )
    }

    /**
     * Convert → resolve a current fix (with address) → burn the stamp (at the user's
     * chosen position/size, with the map) → persist with GPS EXIF → refresh the gallery.
     */
    private suspend fun processCapture(proxy: ImageProxy) {
        val bitmap = withContext(Dispatchers.Default) {
            proxy.use { it.toUprightBitmap() }
        }
        val baseFix = locationRepo.currentFix() ?: _fix.value
        val enriched = baseFix?.let { f ->
            val address = locationRepo.reverseGeocode(f.latitude, f.longitude)
            f.copy(address = address, timestampMs = System.currentTimeMillis())
        }
        val lines = enriched?.let { GpsFormat.buildStampLines(it) }
            ?: listOf("GPS Camera", "Location unavailable")
        val transform = _stamp.value
        // Guarantee the map is on the photo: use the cached one, otherwise fetch it now
        // (best-effort with a timeout) for the exact captured coordinates.
        val map = enriched?.let { mapForCapture(it) } ?: _mapBitmap.value
        val stamped = withContext(Dispatchers.Default) {
            PhotoStamper.stamp(
                src = bitmap,
                lines = lines,
                mapBitmap = map,
                anchorX = transform.anchorX,
                anchorY = transform.anchorY,
                userScale = transform.scale
            )
        }
        val uri = photoSaver.save(stamped, enriched)
        _lastSaved.value = uri
        _message.value = if (enriched != null) "Saved with GPS to Pictures/GPSCamera"
        else "Saved (no GPS fix) to Pictures/GPSCamera"
        refreshGallery()
    }
}
