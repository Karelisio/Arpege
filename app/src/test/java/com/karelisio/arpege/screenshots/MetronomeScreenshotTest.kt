package com.karelisio.arpege.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.robolectric.RuntimeEnvironment
import com.karelisio.arpege.data.container
import com.karelisio.arpege.theory.dsp.Meter
import com.karelisio.arpege.theory.dsp.MetronomeConfig
import com.karelisio.arpege.theory.dsp.MetronomeFavorite
import com.karelisio.arpege.ui.metronome.MetronomeScreen
import com.karelisio.arpege.ui.nav.Section
import com.karelisio.arpege.ui.theme.ArpegeTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Métronome dans l'appli réelle (barres système simulées + barre de navigation) et version déroulée. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MetronomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            ArpegeTheme(darkTheme = dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Réglages de démonstration : favoris, accélération et minuteur activés. */
    private fun withExtras() {
        val c = RuntimeEnvironment.getApplication().container
        runBlocking {
            c.settings.updateMetronomeFavorites {
                listOf(
                    MetronomeFavorite.of("Gammes lentes", MetronomeConfig(bpm = 72, subdivision = 2)),
                    MetronomeFavorite.of("Valse", MetronomeConfig(bpm = 100, meter = Meter(3, 4))),
                    MetronomeFavorite.of("Funk", MetronomeConfig(bpm = 96, subdivision = 4)),
                )
            }
            c.settings.setTrainer(true, com.karelisio.arpege.theory.dsp.SpeedTrainer(5, 4, 140))
            c.settings.setTimer(true, 20)
            c.metronome.ready()
        }
    }

    /** Écran tel qu'affiché sur un téléphone 411×891 dp : barre d'état, barre d'app, navigation. */
    @Test fun metronomeInApp() = shot("03_metronome_app") {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(40.dp)) // barre d'état
            Box(Modifier.weight(1f)) {
                NavigationSuiteScaffold(navigationSuiteItems = {
                    Section.entries.forEach { s ->
                        item(selected = s == Section.TOOLS, onClick = {}, icon = { Icon(s.icon, null) }, label = { Text(s.label) })
                    }
                }) { MetronomeScreen({}, null) }
            }
            Spacer(Modifier.height(24.dp)) // barre gestuelle
        }
    }

    @Test fun metronomeExtrasDark() {
        withExtras()
        shot("03_metronome_extras_dark", dark = true) { MetronomeScreen({}, null) }
    }

    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    @Test fun metronomeFull() {
        withExtras()
        shot("03_metronome_full") { MetronomeScreen({}, null) }
    }
}
