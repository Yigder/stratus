package com.weatherclone.app

import android.Manifest
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.weatherclone.app.background.Notifier
import com.weatherclone.app.data.Units
import com.weatherclone.app.ui.AppTypography
import com.weatherclone.app.ui.LocalUnits
import com.weatherclone.app.ui.WeatherApp
import com.weatherclone.app.ui.WeatherViewModel

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                vm.onPermissionResult(res.values.any { it })
                // Then offer notifications (severe weather, rain starting soon), as Apple does on first launch.
                if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val request = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            LaunchedEffect(Unit) {
                if (!vm.locationEnabled && !vm.askedLocation) request()
            }
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF1C1C1E)), typography = AppTypography) {
                CompositionLocalProvider(LocalUnits provides Units(vm.settings, DateFormat.is24HourFormat(ctx))) {
                    WeatherApp(vm, request)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAll()
    }
}
