package com.karelisio.arpege.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val TOOLS = "tools"
    const val THEORY = "theory"
    const val TRAINING = "training"
    const val JOURNAL = "journal"
    const val TUNER = "tuner"
    const val METRONOME = "metronome"
    const val TABS = "tabs"
    const val TAB = "tab"
    const val CHORDS = "chords"
    const val SCALES = "scales"
    const val CIRCLE = "circle"
    const val PROGRESSIONS = "progressions"
    const val CHORD_FINDER = "chordfinder"
    const val EAR = "ear"
    const val RHYTHM = "rhythm"
    const val SOLFEGE = "solfege"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"

    fun metronome(bpm: Int? = null) = if (bpm == null) METRONOME else "$METRONOME?bpm=$bpm"
    fun chords(name: String? = null) = if (name == null) CHORDS else "$CHORDS?name=${android.net.Uri.encode(name)}"
    fun tab(id: Long) = "$TAB/$id"
}

/** Un outil affiché dans une section (carte du hub). */
data class ToolEntry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

enum class Section(val route: String, val label: String, val icon: ImageVector, val tools: List<ToolEntry>) {
    TOOLS(
        Routes.TOOLS, "Outils", Icons.Rounded.Build,
        listOf(
            ToolEntry(Routes.TUNER, "Accordeur", "Détection précise, tous accordages", Icons.Rounded.Tune),
            ToolEntry(Routes.METRONOME, "Métronome", "20–300 BPM, mesures irrégulières", Icons.Rounded.Speed),
            ToolEntry(Routes.TABS, "Tablatures", "ASCII, ChordPro, Guitar Pro", Icons.Rounded.LibraryMusic),
        ),
    ),
    THEORY(
        Routes.THEORY, "Théorie", Icons.Rounded.AutoStories,
        listOf(
            ToolEntry(Routes.CHORDS, "Accords", "Positions générées, tous instruments", Icons.Rounded.Piano),
            ToolEntry(Routes.SCALES, "Gammes & modes", "Sur le manche ou le clavier", Icons.AutoMirrored.Rounded.QueueMusic),
            ToolEntry(Routes.CIRCLE, "Cercle des quintes", "Armures, relatives, voisines", Icons.Rounded.DonutLarge),
            ToolEntry(Routes.PROGRESSIONS, "Grilles d'accords", "Progressions transposables, en boucle", Icons.Rounded.GridView),
            ToolEntry(Routes.CHORD_FINDER, "Accord inverse", "Quel est cet accord ?", Icons.Rounded.Search),
        ),
    ),
    TRAINING(
        Routes.TRAINING, "Entraînement", Icons.Rounded.School,
        listOf(
            ToolEntry(Routes.EAR, "Oreille", "Intervalles, accords, gammes, cadences", Icons.Rounded.Hearing),
            ToolEntry(Routes.RHYTHM, "Rythme", "Lis et tape la mesure", Icons.Rounded.TouchApp),
            ToolEntry(Routes.SOLFEGE, "Solfège", "Lecture en clés de sol, fa, ut", Icons.Rounded.MusicNote),
        ),
    ),
    JOURNAL(Routes.JOURNAL, "Journal", Icons.Rounded.CalendarMonth, emptyList());

    companion object {
        private val byRoute: Map<String, Section> = buildMap {
            Section.entries.forEach { s -> put(s.route, s); s.tools.forEach { t -> put(t.route, s) } }
            put(Routes.TAB, TOOLS)
        }

        fun of(route: String?): Section? = route?.substringBefore('?')?.substringBefore('/')?.let { byRoute[it] }
    }
}
