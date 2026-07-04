package com.gpscamera.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Draggable, resizable GPS block (its screen position mirrors where it is burned in).
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var blockSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
        ) {
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
                            viewModel.setStampAnchor(
                                cur.anchorX + pan.x / cw,
                                cur.anchorY + pan.y / ch
                            )
                        }
                    }
            ) {
                GpsOverlayCard(
                    fix = fix,
                    mapBitmap = mapBitmap,
                    onOpenMap = { fix?.let { openInMaps(context, it) } }
                )
            }
        }

        // Top controls (fixed): theme toggle · accuracy chip · open-in-maps.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                icon = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                description = "Toggle day/night",
                onClick = onToggleTheme
            )
            AccuracyChip(fix = fix)
            RoundIconButton(
                icon = Icons.Filled.Map,
                description = "Open in Maps",
                tint = if (fix != null) MaterialTheme.colorScheme.primary else Color.Gray,
                onClick = { fix?.let { openInMaps(context, it) } }
            )
        }

        // Bottom controls (fixed).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Drag to move · pinch to resize the info block",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(56.dp))
                ShutterButton(
                    enabled = !isSaving,
                    saving = isSaving,
                    onClick = { viewModel.capture(imageCapture, ContextCompat.getMainExecutor(context)) }
                )
                GalleryButton(onClick = onOpenGallery)
            }
        }
    }
}

private fun openInMaps(context: Context, fix: GeoFix) {
    val geo = Uri.parse(SlippyMap.geoUri(fix.latitude, fix.longitude, fix.address ?: "Photo location"))
    val intent = Intent(Intent.ACTION_VIEW, geo)
    try {
        context.startActivity(intent)
    } catch (t: Throwable) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(SlippyMap.mapsUrl(fix.latitude, fix.longitude)))
        )
    }
}

@Composable
private fun AccuracyChip(fix: GeoFix?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = Color(0xCC0E1116)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            val locked = fix != null
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = if (locked) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = when {
                    fix == null -> "Acquiring GPS fix…"
                    fix.accuracyM != null -> "GPS locked  ${GpsFormat.formatAccuracy(fix.accuracyM)}"
                    else -> "GPS locked"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GpsOverlayCard(fix: GeoFix?, mapBitmap: android.graphics.Bitmap?, onOpenMap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xC0000000)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag handle",
                tint = Color(0x99FFFFFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier
                    .width(4.dp)
                    .height(if (fix?.address != null) 104.dp else 86.dp)
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
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenMap() }
                ) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Open location in Maps",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color(0xAA00BFA5),
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp).padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, saving: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(78.dp),
        onClick = { if (enabled) onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (saving) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp)) {}
            }
        }
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color(0xCC161B22), modifier = Modifier.size(56.dp), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(26.dp))
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
    Surface(shape = CircleShape, color = Color(0xCC161B22), modifier = Modifier.size(44.dp), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
