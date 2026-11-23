package com.karelisio.arpege

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.karelisio.arpege.data.container
import com.karelisio.arpege.ui.ArpegeRoot
import com.karelisio.arpege.ui.theme.ArpegeTheme

class MainActivity : ComponentActivity() {
    private var nav: NavHostController? = null
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        enableEdgeToEdge()
        setContent {
            ArpegeTheme {
                val settings by container.settings.settings.collectAsStateWithLifecycle(null)
                val s = settings ?: return@ArpegeTheme
                ready = true
                val controller = rememberNavController()
                nav = controller
                ArpegeRoot(controller, s.onboardingDone)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nav?.handleDeepLink(intent)
    }
}
