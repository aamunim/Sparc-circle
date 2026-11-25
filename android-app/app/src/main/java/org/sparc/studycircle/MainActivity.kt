package org.sparc.studycircle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.sparc.studycircle.core.theme.SparcTheme
import org.sparc.studycircle.ui.SparcMainScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SparcApp).repository
        setContent {
            val state by repository.state.collectAsState()
            SparcTheme(visualTheme = state.visualTheme) {
                SparcMainScaffold(repository = repository)
            }
        }
    }
}
