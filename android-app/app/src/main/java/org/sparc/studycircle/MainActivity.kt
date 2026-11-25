package org.sparc.studycircle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

            SparcTheme(visualTheme = state.visualTheme) {
                when {
                    showSplash -> SparcAnimatedSplashScreen(
                        onFinished = { showSplash = false }
                    )
                    !state.isAuthenticated -> SparcAuthScreen(repository = repository)
                    else -> SparcMainScaffold(repository = repository)
                }
            }
        }
    }
}
