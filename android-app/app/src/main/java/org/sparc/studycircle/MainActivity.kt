package org.sparc.studycircle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import org.sparc.studycircle.core.theme.SparcTheme
import org.sparc.studycircle.ui.SparcMainScaffold
import org.sparc.studycircle.ui.auth.SparcAnimatedSplashScreen
import org.sparc.studycircle.ui.auth.SparcAuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SparcApp).repository
        setContent {
            val state by repository.state.collectAsState()
            var showSplash by rememberSaveable { mutableStateOf(true) }

            val notifPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(state.isAuthenticated) {
                if (state.isAuthenticated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            SparcTheme(
                visualTheme = state.visualTheme,
                accentPreset = state.uiCustomization.accentPreset,
                appLanguage = state.appLanguage
            ) {
                when {
                    showSplash -> SparcAnimatedSplashScreen(
                        appCustomization = state.appCustomization,
                        onFinished = { showSplash = false }
                    )
                    !state.isAuthenticated -> SparcAuthScreen(repository = repository)
                    else -> SparcMainScaffold(repository = repository)
                }
            }
        }
    }
}
