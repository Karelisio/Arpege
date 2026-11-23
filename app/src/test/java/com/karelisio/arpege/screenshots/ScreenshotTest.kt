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
import com.karelisio.arpege.ui.tuner.CentsTraceChart
import com.karelisio.arpege.ui.tuner.StrobeBand
import com.karelisio.arpege.ui.training.SingPanel
import com.karelisio.arpege.ui.training.SingPhase
import com.karelisio.arpege.ui.training.SingScreen
import com.karelisio.arpege.ui.settings.BackupSettings
import com.karelisio.arpege.ui.settings.ReminderSettings
import com.karelisio.arpege.data.AppSettings
import com.karelisio.arpege.theory.Notation
import com.karelisio.arpege.theory.dsp.CentsTrace
import com.karelisio.arpege.theory.training.EarItem
import com.karelisio.arpege.theory.training.HoldDetector
import com.karelisio.arpege.theory.training.SingQuestion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import com.karelisio.arpege.data.container
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
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
    @Test fun sing() = shot("14_sing") { SingScreen {} }
    private val singQuestion = SingQuestion(EarItem("i7+", "Quinte juste ↑"), 48, 55)
    @Test fun singActive() = shot("14_sing_active") {
        Column(Modifier.padding(20.dp)) {
            SingPanel(singQuestion, SingPhase.SINGING, HoldDetector.State(54.9, -10.0, true, 0.6f, false), Notation.LATIN, octaveFree = true)
        }
    }
    @Test fun singDone() = shot("14_sing_done", dark = true) {
        Column(Modifier.padding(20.dp)) {
            SingPanel(singQuestion, SingPhase.SUCCESS, HoldDetector.State(55.0, 0.0, true, 1f, true), Notation.ENGLISH, octaveFree = false)
        }
    }
    @Test fun tunerStrobeMode() {
        val settings = RuntimeEnvironment.getApplication().container.settings
        runBlocking { settings.setTunerStrobe(true) }
        try {
            shot("02_tuner_strobe") { TunerScreen {} }
        } finally {
            runBlocking { settings.setTunerStrobe(false) }
        }
    }
    @Test fun tunerVisuals() = shot("02_tuner_strobe_trace", dark = true) {
        val trace = CentsTrace()
        for (t in 0L..4_000L step 23) trace.add(t, if (t in 1_800L..2_100L) null else 14 * sin(t / 600.0) - t / 400.0 + 4)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StrobeBand({ 0.3 }, inTune = false, active = true, modifier = Modifier.fillMaxWidth().height(120.dp))
            StrobeBand({ 0.0 }, inTune = true, active = true, modifier = Modifier.fillMaxWidth().height(120.dp))
            CentsTraceChart({ trace.segments() }, { 4_200L }, trace.windowMs)
        }
    }
    @Test fun reminderBackup() = shot("13_settings_reminder") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ReminderSettings(AppSettings(reminderEnabled = true, reminderMinutes = 18 * 60 + 30))
            BackupSettings(SnackbarHostState())
        }
    }
}
