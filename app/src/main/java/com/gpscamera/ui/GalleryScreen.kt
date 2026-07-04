package com.gpscamera.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gpscamera.util.SlippyMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val photos by viewModel.gallery.collectAsStateWithLifecycle()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("GPS Camera — Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No photos yet.\nCapture your first geotagged shot!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
            ) {
                items(photos.size, key = { photos[it].toString() }) { index ->
                    AsyncImage(
                        model = photos[index],
                        contentDescription = "Open photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewerIndex = index }
                    )
                }
            }
        }
    }

    viewerIndex?.let { start ->
        PhotoViewer(
            photos = photos,
            startIndex = start,
            onClose = { viewerIndex = null }
        )
    }
}

@Composable
private fun PhotoViewer(photos: List<Uri>, startIndex: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })

    BackHandler(enabled = true) { onClose() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pagerState.currentPage) { scale = 1f; offsetX = 0f; offsetY = 0f }

    Box(Modifier.fillMaxSize().background(Color(0xFF000000))) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val isCurrent = page == pagerState.currentPage
            AsyncImage(
                model = photos[page],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isCurrent) Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f; offsetY = 0f
                                }
                            }
                        } else Modifier
                    )
                    .graphicsLayer(
                        scaleX = if (isCurrent) scale else 1f,
                        scaleY = if (isCurrent) scale else 1f,
                        translationX = if (isCurrent) offsetX else 0f,
                        translationY = if (isCurrent) offsetY else 0f
                    )
            )
        }

        // Top action bar over the image.
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).align(Alignment.TopCenter)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
            }
            androidx.compose.foundation.layout.Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = {
                    scope.launch {
                        val ll = readLatLng(context, photos[pagerState.currentPage])
                        if (ll != null) openInMaps(context, ll.first, ll.second)
                    }
                }) { Icon(Icons.Filled.Map, "Open in Maps", tint = Color.White) }
                IconButton(onClick = { sharePhoto(context, photos[pagerState.currentPage]) }) {
                    Icon(Icons.Filled.Share, "Share", tint = Color.White)
                }
            }
        }
    }
}

private suspend fun readLatLng(context: Context, uri: Uri): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        try {
            val src = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(uri)
            } else uri
            context.contentResolver.openInputStream(src)?.use { stream ->
                ExifInterface(stream).latLong?.let { it[0] to it[1] }
            }
        } catch (t: Throwable) {
            null
        }
    }

private fun openInMaps(context: Context, lat: Double, lon: Double) {
    val geo = Uri.parse(SlippyMap.geoUri(lat, lon, "Photo location"))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (t: Throwable) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SlippyMap.mapsUrl(lat, lon))))
    }
}

private fun sharePhoto(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}
