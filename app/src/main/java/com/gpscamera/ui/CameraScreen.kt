package com.gpscamera.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cameraProvider = future.get() }, ContextCompat.getMainExecutor(context))
    }

    // (Re)bind use cases whenever the provider is ready or the capture mode changes.
    LaunchedEffect(cameraProvider, mode) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        camera = try {
            if (mode == CaptureMode.PHOTO) {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } else {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
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
                    Spacer(Modifier.size(56.dp))
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
        val offX = (stamp.anchorX * cw - blockSize.width / 2f)
            .coerceIn(0f, (cw - blockSize.width).coerceAtLeast(0).toFloat())
        val offY = (stamp.anchorY * ch - blockSize.height / 2f)
            .coerceIn(0f, (ch - blockSize.height).coerceAtLeast(0).toFloat())

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
    M3Surface(shape = RoundedCornerShape(16.dp), color = Color(0xC0000000)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DragIndicator, "Drag handle", tint = Color(0x99FFFFFF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier.width(4.dp).height(if (fix?.address != null) 96.dp else 78.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(Color(0xFFFFB300), CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text("GPS Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.size(6.dp))
                if (fix == null) {
                    Text("Acquiring GPS fix…", color = Color(0xFFB0B8C0), fontSize = 13.sp)
                } else {
                    for (line in GpsFormat.buildStampLines(fix)) {
                        Text(line, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (mapBitmap != null) {
                Spacer(Modifier.size(12.dp))
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpenMap() }) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Open location in Maps",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    M3Surface(color = Color(0xAA00BFA5), shape = RoundedCornerShape(topStart = 8.dp), modifier = Modifier.align(Alignment.BottomEnd)) {
                        Icon(Icons.Filled.Map, null, tint = Color.Black, modifier = Modifier.size(16.dp).padding(1.dp))
                    }
                }
            }
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
