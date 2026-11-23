package com.karelisio.arpege.ui.components

import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/** Forme interpolée entre deux formes Material (ex. cercle → fleur). */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress)
        val m = Matrix()
        m.scale(size.width, size.height)
        path.transform(m)
        if (rotation != 0f) {
            val r = Matrix()
            r.translate(size.width / 2, size.height / 2)
            r.rotateZ(rotation)
            r.translate(-size.width / 2, -size.height / 2)
            path.transform(r)
        }
        return Outline.Generic(path)
    }
}
