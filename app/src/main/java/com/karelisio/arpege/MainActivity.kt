package com.karelisio.arpege

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.karelisio.arpege.ui.ArpegeRoot
import com.karelisio.arpege.ui.theme.ArpegeTheme

class MainActivity : ComponentActivity() {
    private var nav: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArpegeTheme {
                val controller = rememberNavController()
                nav = controller
                ArpegeRoot(controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nav?.handleDeepLink(intent)
    }
}
