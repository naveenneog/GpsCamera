package com.gpscamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpscamera.ui.AppRoot
import com.gpscamera.ui.MainViewModel
import com.gpscamera.ui.theme.GpsCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val themeChoice by vm.darkTheme.collectAsStateWithLifecycle()
            val isDark = themeChoice ?: isSystemInDarkTheme()
            GpsCameraTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(vm, isDark = isDark, onToggleTheme = { vm.toggleTheme(isDark) })
                }
            }
        }
    }
}

