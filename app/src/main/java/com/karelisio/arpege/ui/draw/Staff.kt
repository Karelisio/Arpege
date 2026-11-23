package com.karelisio.arpege.ui.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.karelisio.arpege.theory.Clef
import com.karelisio.arpege.ui.theme.Bravura

/** Glyphes SMuFL (police Bravura). */
object Smufl {
    const val G_CLEF = ""
    const val F_CLEF = ""
    const val C_CLEF = ""
    const val SHARP = ""
    const val FLAT = ""
    const val NATURAL = ""
    const val NOTEHEAD_BLACK = ""
    const val NOTEHEAD_WHOLE = ""
    const val TIME_4_4 = ""
    fun timeDigit(d: Int) = (0xE080 + d).toChar().toString()
    // Notes précomposées (hampe en haut).
    const val NOTE_WHOLE = ""
    const val NOTE_HALF = ""
    const val NOTE_QUARTER = ""
    const val NOTE_8TH = ""
    const val NOTE_16TH = ""
    const val AUG_DOT = ""
    const val REST_WHOLE = ""
    const val REST_HALF = ""
    const val REST_QUARTER = ""
    const val REST_8TH = ""
    const val REST_16TH = ""
    const val TUPLET_3 = ""
}

/** Glyphe SMuFL d'une clé et pas de portée de sa ligne de référence. */
val Clef.glyph: String get() = when (this) {
    Clef.TREBLE -> Smufl.G_CLEF
    Clef.BASS -> Smufl.F_CLEF
    Clef.ALTO, Clef.TENOR -> Smufl.C_CLEF
}
val Clef.glyphStep: Int get() = refStep

/** Géométrie d'une portée : [top] = y de la ligne du haut, [space] = interligne. */
class StaffGeometry(val left: Float, val right: Float, val top: Float, val space: Float) {
    val bottom: Float get() = top + 4 * space
    fun y(step: Int): Float = bottom - step * space / 2
}

fun DrawScope.drawStaffLines(g: StaffGeometry, color: Color) {
    for (i in 0..4) drawLine(color, Offset(g.left, g.top + i * g.space), Offset(g.right, g.top + i * g.space), g.space * 0.1f)
}

/** Dessine un glyphe SMuFL avec sa ligne de base sur [baselineY] (taille = 4 interlignes). */
fun DrawScope.drawGlyph(measurer: TextMeasurer, glyph: String, x: Float, baselineY: Float, space: Float, color: Color, centered: Boolean = false) {
    val style = TextStyle(fontFamily = Bravura, fontSize = (4 * space).toSp(), color = color)
    val layout = measurer.measure(glyph, style)
    val dx = if (centered) layout.size.width / 2f else 0f
    drawText(layout, topLeft = Offset(x - dx, baselineY - layout.firstBaseline))
}

fun DrawScope.drawLedgerLines(g: StaffGeometry, step: Int, x: Float, color: Color) {
    val w = g.space * 1.6f
    var s = if (step < 0) -2 else 10
    while ((step < 0 && s >= step) || (step > 8 && s <= step)) {
        drawLine(color, Offset(x - w / 2, g.y(s)), Offset(x + w / 2, g.y(s)), g.space * 0.12f)
        s += if (step < 0) -2 else 2
    }
}
