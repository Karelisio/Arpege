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
import com.karelisio.arpege.ui.theory.ProgressionsScreen
import com.karelisio.arpege.ui.theory.ChordFinderScreen
import com.karelisio.arpege.ui.tabs.TabViewer
import com.karelisio.arpege.data.db.TabFile
import java.io.ByteArrayOutputStream
import java.util.Base64
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

    private fun shot(name: String, dark: Boolean = false, settleMs: Long = 0, content: @Composable () -> Unit) {
        rule.setContent {
            ArpegeTheme(darkTheme = dark, dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        if (settleMs > 0) {
            // Calculs en arrière-plan (positions d'accords) : on leur laisse le temps d'aboutir.
            Thread.sleep(settleMs)
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
        }
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

    @Test fun progressions() = shot("14_progressions", settleMs = 1500) { ProgressionsScreen({}, {}) }
    @Test fun progressionsDark() = shot("14_progressions_dark", dark = true, settleMs = 1500) { ProgressionsScreen({}, {}) }
    @Test fun chordFinder() = shot("15_chord_finder") { ChordFinderScreen({}, initialFrets = "x32010") {} }
    @Test fun tabGuitarPro() = shot("16_tab_guitar_pro") {
        val file = TabFile(id = 1, title = "Essai", format = "GUITAR_PRO", content = Base64.getEncoder().encodeToString(gp3Riff()), sourceName = "essai.gp3")
        TabViewer(file, {}, previewBeat = 11)
    }

    /** Petit GP3 : 4 mesures de croches (riff en Mi), accord sur chaque mesure. */
    private fun gp3Riff(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun bss(s: String, size: Int) { u8(s.length); out.write(s.toByteArray()); repeat(size - s.length) { u8(0) } }
        fun ibss(s: String) { i32(s.length + 1); bss(s, s.length) }
        bss("FICHIER GUITAR PRO v3.00", 30)
        listOf("Riff d'essai", "", "Demo", "", "", "", "", "").forEach { ibss(it) } // ASCII : 1 octet par caractère
        i32(0); u8(0); i32(110); i32(0)
        repeat(64) { i32(25); repeat(8) { u8(0) } }
        i32(4); i32(1)
        repeat(4) { if (it == 0) { u8(3); u8(4); u8(4) } else u8(0) }
        u8(0); bss("Guitare", 40); i32(6)
        listOf(64, 59, 55, 50, 45, 40, 0).forEach { i32(it) }
        i32(1); i32(1); i32(2); i32(24); i32(0); repeat(4) { u8(0) }
        val chords = listOf("Em", "G", "D", "C")
        val riff = listOf(6 to 0, 4 to 2, 3 to 0, 2 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 2)
        for (m in 0 until 4) {
            i32(riff.size)
            riff.forEachIndexed { i, (string, fret) ->
                u8(if (i == 0) 2 else 0); u8(1)
                if (i == 0) { u8(0); ibss(chords[m]); i32(0) }
                u8(1 shl (7 - string)); u8(32); u8(1); u8(fret + if (string == 6 || i == 0) 0 else m % 2)
            }
        }
        return out.toByteArray()
    }
}
