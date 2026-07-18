package com.gpscamera.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpscamera.camera.VideoStampOverlay
import com.gpscamera.model.GeoFix
import com.gpscamera.util.GpsFormat
import com.gpscamera.util.SlippyMap
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onOpenGallery: () -> Unit,
    onToggleTheme: () -> Unit,
    isDark: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val fix by viewModel.fix.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val mapBitmap by viewModel.mapBitmap.collectAsStateWithLifecycle()
    val stamp by viewModel.stamp.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordSeconds by viewModel.recordSeconds.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            )
            .build()
        VideoCapture.withOutput(recorder)
    }
    // Burns the GPS stamp into every recorded video frame (same panel photos get).
    val videoOverlay = remember { VideoStampOverlay() }
    // The overlay renders off the main thread so dragging/resizing never janks the UI.
    val overlayThread = remember { HandlerThread("gps-video-overlay").apply { start() } }
    val overlayEffect = remember {
        OverlayEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            5,
            Handler(overlayThread.looper),
            Consumer<Throwable> { /* overlay failure must never crash capture */ }
        ).apply {
            setOnDrawListener { frame ->
                try {
                    val canvas = frame.overlayCanvas
                    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    videoOverlay.bitmapFor(frame.size.width, frame.size.height)?.let { bmp ->
                        canvas.drawBitmap(bmp, 0f, 0f, null)
                    }
                } catch (t: Throwable) {
                    // Never let an overlay hiccup break the recording pipeline.
                }
                true
            }
        }
    }
    DisposableEffect(overlayEffect) {
        onDispose {
            overlayEffect.close()
            videoOverlay.clear()
            overlayThread.quitSafely()
        }
    }
    // Keep the overlay content + placement in sync with the on-screen draggable block.
    LaunchedEffect(fix, mapBitmap, stamp) {
        videoOverlay.update(
            fix?.let { GpsFormat.buildStampDetails(it) },
            mapBitmap,
            stamp.anchorX,
            stamp.anchorY,
            stamp.scale
        )
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cameraProvider = future.get() }, ContextCompat.getMainExecutor(context))
    }

    // (Re)bind use cases whenever the provider is ready, the capture mode, or the lens changes.
    LaunchedEffect(cameraProvider, mode, useFrontCamera) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val selector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        camera = try {
            if (mode == CaptureMode.PHOTO) {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } else {
                try {
                    val group = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(videoCapture)
                        .addEffect(overlayEffect)
                        .build()
                    provider.bindToLifecycle(lifecycleOwner, selector, group)
                } catch (effectError: Throwable) {
                    // If the device can't apply the overlay effect, still record (without the
                    // burned stamp) rather than breaking video entirely. GPS stays in metadata.
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                }
            }
        } catch (t: Throwable) {
            null
        }
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    // Keep captures upright regardless of the device's physical rotation (landscape too).
    DisposableEffect(imageCapture, videoCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = rotation
                videoCapture.targetRotation = rotation
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Preview + pinch-to-zoom (gesture lives on the preview, behind the info block).
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val zs = cam.cameraInfo.zoomState.value
                        val min = zs?.minZoomRatio ?: 1f
                        val max = zs?.maxZoomRatio ?: 1f
                        zoomRatio = (zoomRatio * zoom).coerceIn(min, max)
                        cam.cameraControl.setZoomRatio(zoomRatio)
                    }
                }
        )

        // The draggable card places & sizes the block. Photos burn it in post-capture; videos
        // burn the same panel (at this position/scale) into the frames via OverlayEffect — the
        // card is drawn on top of (and covers) its burned twin in the preview.
        DraggableStampBlock(viewModel, fix, mapBitmap, stamp) { fix?.let { openInMaps(context, it) } }

        // Top controls (fixed): theme · accuracy/zoom · open-in-maps.
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Toggle day/night", onClick = onToggleTheme)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isRecording) RecordingChip(recordSeconds) else AccuracyChip(fix)
                if (zoomRatio > 1.05f) {
                    Spacer(Modifier.height(6.dp))
                    ZoomChip(zoomRatio)
                }
            }
            RoundIconButton(
                Icons.Filled.Map, "Open in Maps",
                tint = if (fix != null) MaterialTheme.colorScheme.primary else Color.Gray,
                onClick = { fix?.let { openInMaps(context, it) } }
            )
        }

        // Capture controls — bottom bar in portrait, right-side rail in landscape.
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val onShutter: () -> Unit = {
            val exec = ContextCompat.getMainExecutor(context)
            if (mode == CaptureMode.PHOTO) viewModel.capture(imageCapture, exec)
            else viewModel.startOrStopRecording(videoCapture, exec)
        }
        if (isLandscape) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isRecording) ModeSwitch(mode) { if (it != mode) viewModel.toggleMode() }
                ShutterButton(mode, isSaving, isRecording, onShutter)
                GalleryButton(onClick = onOpenGallery, enabled = !isRecording)
                SwitchCameraButton(enabled = !isRecording) { zoomRatio = 1f; viewModel.toggleLens() }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Drag the info block · pinch preview to zoom",
                    color = Color(0xCCFFFFFF), fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                if (!isRecording) {
                    ModeSwitch(mode = mode, onSelect = { if (it != mode) viewModel.toggleMode() })
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwitchCameraButton(enabled = !isRecording) { zoomRatio = 1f; viewModel.toggleLens() }
                    ShutterButton(mode, isSaving, isRecording, onShutter)
                    GalleryButton(onClick = onOpenGallery, enabled = !isRecording)
                }
            }
        }
    }
}

@Composable
private fun DraggableStampBlock(
    viewModel: MainViewModel,
    fix: GeoFix?,
    mapBitmap: android.graphics.Bitmap?,
    stamp: StampTransform,
    onOpenMap: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var blockSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
        val cw = containerSize.width.coerceAtLeast(1)
        val ch = containerSize.height.coerceAtLeast(1)
        val offX = scaledBlockOffset(
            desired = stamp.anchorX * cw - blockSize.width / 2f,
            unscaledSize = blockSize.width.toFloat(),
            scale = stamp.scale,
            containerSize = cw.toFloat()
        )
        val offY = scaledBlockOffset(
            desired = stamp.anchorY * ch - blockSize.height / 2f,
            unscaledSize = blockSize.height.toFloat(),
            scale = stamp.scale,
            containerSize = ch.toFloat()
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .onSizeChanged { blockSize = it }
                .graphicsLayer { scaleX = stamp.scale; scaleY = stamp.scale }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) viewModel.scaleStampBy(zoom)
                        val cur = viewModel.stamp.value
                        viewModel.setStampAnchor(cur.anchorX + pan.x / cw, cur.anchorY + pan.y / ch)
                    }
                }
        ) {
            GpsOverlayCard(fix = fix, mapBitmap = mapBitmap, onOpenMap = onOpenMap)
        }
    }
}

private fun scaledBlockOffset(
    desired: Float,
    unscaledSize: Float,
    scale: Float,
    containerSize: Float
): Float {
    val scaledSize = unscaledSize * scale
    if (scaledSize >= containerSize) return (containerSize - unscaledSize) / 2f
    val extra = (scaledSize - unscaledSize) / 2f
    return desired.coerceIn(extra, containerSize - unscaledSize - extra)
}

private fun openInMaps(context: Context, fix: GeoFix) {
    val geo = Uri.parse(SlippyMap.geoUri(fix.latitude, fix.longitude, fix.address ?: "Photo location"))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (t: Throwable) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SlippyMap.mapsUrl(fix.latitude, fix.longitude))))
    }
}

@Composable
private fun AccuracyChip(fix: GeoFix?) {
    M3Surface(shape = RoundedCornerShape(50), color = Color(0xCC0E1116)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Filled.LocationOn, null,
                tint = if (fix != null) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                when {
                    fix == null -> "Acquiring GPS fix…"
                    fix.accuracyM != null -> "GPS locked  ${GpsFormat.formatAccuracy(fix.accuracyM)}"
                    else -> "GPS locked"
                },
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecordingChip(seconds: Int) {
    val mm = seconds / 60
    val ss = seconds % 60
    M3Surface(shape = RoundedCornerShape(50), color = Color(0xCC0E1116)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFE53935)))
            Spacer(Modifier.size(8.dp))
            Text(String.format(Locale.US, "REC  %02d:%02d", mm, ss), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ZoomChip(zoom: Float) {
    M3Surface(shape = RoundedCornerShape(50), color = Color(0xCC0E1116)) {
        Text(
            String.format(Locale.US, "%.1fx", zoom),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ModeSwitch(mode: CaptureMode, onSelect: (CaptureMode) -> Unit) {
    M3Surface(shape = RoundedCornerShape(50), color = Color(0xCC161B22)) {
        Row(modifier = Modifier.padding(4.dp)) {
            ModePill("Photo", Icons.Filled.PhotoCamera, mode == CaptureMode.PHOTO) { onSelect(CaptureMode.PHOTO) }
            ModePill("Video", Icons.Filled.Videocam, mode == CaptureMode.VIDEO) { onSelect(CaptureMode.VIDEO) }
        }
    }
}

@Composable
private fun ModePill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) Color.Black else Color.White
    M3Surface(shape = RoundedCornerShape(50), color = bg, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GpsOverlayCard(fix: GeoFix?, mapBitmap: android.graphics.Bitmap?, onOpenMap: () -> Unit) {
    val configuration = LocalConfiguration.current
    val details = fix?.let(GpsFormat::buildStampDetails)
    val textWidth = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 350.dp else 250.dp
    M3Surface(shape = RoundedCornerShape(14.dp), color = Color(0xAD1E2022)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = mapBitmap != null) { onOpenMap() }
            ) {
                if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Open location in Maps",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE8E5DD)))
                }
                M3Surface(
                    color = Color(0xEFFFFFFF),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = Color(0xFF2E70DC), modifier = Modifier.size(12.dp))
                        Text("Maps", color = Color(0xFF303030), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.size(9.dp))
            Column(Modifier.width(textWidth)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        details?.localityLine ?: "Acquiring GPS fix…",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier.size(19.dp).clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF009688)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.size(4.dp))
                    CountryFlag(details?.countryCode)
                    Spacer(Modifier.size(4.dp))
                    M3Surface(color = Color(0x99464A4E), shape = RoundedCornerShape(50)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Filled.LightMode, null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(2.dp))
                            Text(
                                GpsFormat.formatTemperature(details?.temperatureC),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.size(3.dp))
                details?.fullAddress?.let {
                    Text(
                        it,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    details?.coordinateLine ?: "GPS fix unavailable",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(details?.dateTimeLine.orEmpty(), color = Color.White, fontSize = 11.sp)
                Text(
                    details?.noteLine ?: GpsFormat.NOTE_LINE,
                    color = Color(0xFFEEEEEE),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CountryFlag(countryCode: String?) {
    Canvas(Modifier.width(25.dp).height(16.dp).clip(RoundedCornerShape(2.dp))) {
        if (countryCode.equals("IN", ignoreCase = true)) {
            val stripe = size.height / 3f
            drawRect(Color(0xFFFF9933), size = androidx.compose.ui.geometry.Size(size.width, stripe))
            drawRect(
                Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(0f, stripe),
                size = androidx.compose.ui.geometry.Size(size.width, stripe)
            )
            drawRect(
                Color(0xFF138808),
                topLeft = androidx.compose.ui.geometry.Offset(0f, stripe * 2f),
                size = androidx.compose.ui.geometry.Size(size.width, stripe)
            )
            drawCircle(Color(0xFF000080), radius = 2.dp.toPx(), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        } else {
            drawRect(Color(0xFF4B525A))
        }
    }
}

@Composable
private fun ShutterButton(mode: CaptureMode, saving: Boolean, recording: Boolean, onClick: () -> Unit) {
    val ringEnabled = !saving
    M3Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(78.dp), onClick = { if (ringEnabled) onClick() }) {
        Box(contentAlignment = Alignment.Center) {
            when {
                saving -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                mode == CaptureMode.VIDEO && recording ->
                    M3Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE53935), modifier = Modifier.size(30.dp)) {}
                mode == CaptureMode.VIDEO ->
                    M3Surface(shape = CircleShape, color = Color(0xFFE53935), modifier = Modifier.size(60.dp)) {}
                else ->
                    M3Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp)) {}
            }
        }
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit, enabled: Boolean) {
    M3Surface(shape = CircleShape, color = Color(0xCC161B22), modifier = Modifier.size(56.dp), onClick = { if (enabled) onClick() }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PhotoLibrary, "Gallery", tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SwitchCameraButton(enabled: Boolean, onClick: () -> Unit) {
    M3Surface(shape = CircleShape, color = Color(0xCC161B22), modifier = Modifier.size(56.dp), onClick = { if (enabled) onClick() }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Cameraswitch, "Switch camera", tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    M3Surface(shape = CircleShape, color = Color(0xCC161B22), modifier = Modifier.size(44.dp), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
