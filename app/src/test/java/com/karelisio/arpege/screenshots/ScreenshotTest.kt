package com.karelisio.arpege.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.karelisio.arpege.ui.hub.HubScreen
import com.karelisio.arpege.ui.journal.JournalScreen
import com.karelisio.arpege.ui.metronome.MetronomeScreen
import com.karelisio.arpege.ui.nav.Section
import com.karelisio.arpege.ui.onboarding.OnboardingScreen
import com.karelisio.arpege.ui.settings.SettingsScreen
import com.karelisio.arpege.ui.tabs.TabsLibraryScreen
import com.karelisio.arpege.ui.theme.ArpegeTheme
import com.karelisio.arpege.ui.theory.ChordsScreen
import com.karelisio.arpege.ui.theory.CircleScreen
import com.karelisio.arpege.ui.theory.ScalesScreen
import com.karelisio.arpege.ui.training.EarScreen
import com.karelisio.arpege.ui.training.RhythmScreen
import com.karelisio.arpege.ui.training.SolfegeScreen
import com.karelisio.arpege.ui.tuner.TunerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu des écrans principaux en PNG (vérification visuelle, hors CI). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
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

    @Test fun hub() = shot("01_hub") { HubScreen(Section.TOOLS, {}, {}) }
    @Test fun hubDark() = shot("01_hub_dark", dark = true) { HubScreen(Section.THEORY, {}, {}) }
    @Test fun tuner() = shot("02_tuner") { TunerScreen {} }
    @Test fun metronome() = shot("03_metronome") { MetronomeScreen({}, null) }
    @Test fun chords() = shot("04_chords") { ChordsScreen({}, "Am7") }
    @Test fun scales() = shot("05_scales") { ScalesScreen({}, {}) }
    @Test fun circle() = shot("06_circle") { CircleScreen({}, {}) }
    @Test fun journal() = shot("07_journal") { JournalScreen {} }
    @Test fun ear() = shot("08_ear") { EarScreen {} }
    @Test fun rhythm() = shot("09_rhythm") { RhythmScreen {} }
    @Test fun solfege() = shot("10_solfege") { SolfegeScreen {} }
    @Test fun tabs() = shot("11_tabs") { TabsLibraryScreen({}, {}) }
    @Test fun onboarding() = shot("12_onboarding") { OnboardingScreen {} }
    @Test fun settings() = shot("13_settings") { SettingsScreen({}, {}) }
}
