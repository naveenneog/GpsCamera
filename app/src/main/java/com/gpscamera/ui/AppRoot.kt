package com.gpscamera.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppRoot(viewModel: MainViewModel, isDark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    // Camera + location are required; microphone (video audio) and photo access
    // (in-app gallery) are requested too but optional.
    val optionalMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        listOf(Manifest.permission.RECORD_AUDIO)
    }
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) + optionalMedia
    )
    val ready = permissions.permissions
        .filter {
            it.permission == Manifest.permission.CAMERA ||
                it.permission == Manifest.permission.ACCESS_FINE_LOCATION
        }
        .all { it.status.isGranted }

    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    if (ready) {
        LaunchedEffect(Unit) {
            viewModel.startLocation()
            viewModel.refreshGallery()
        }
        var screen by rememberSaveable { mutableStateOf(Screen.CAMERA) }
        when (screen) {
            Screen.CAMERA -> CameraScreen(
                viewModel,
                onOpenGallery = {
                    viewModel.refreshGallery()
                    screen = Screen.GALLERY
                },
                onToggleTheme = onToggleTheme,
                isDark = isDark
            )
            Screen.GALLERY -> GalleryScreen(viewModel, onBack = { screen = Screen.CAMERA })
        }
    } else {
        PermissionRequest(onRequest = { permissions.launchMultiplePermissionRequest() })
    }
}

private enum class Screen { CAMERA, GALLERY }

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(72.dp).padding(16.dp)
                )
            }
            Text(
                "Camera & Location needed",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "GPS Camera stamps each photo with your exact location, so it needs camera and location access.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = onRequest, modifier = Modifier.padding(top = 24.dp)) {
                Text("Grant permissions")
            }
        }
    }
}
