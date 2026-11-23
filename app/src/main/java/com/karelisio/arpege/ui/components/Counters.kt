package com.karelisio.arpege.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Row
import com.karelisio.arpege.ui.theme.LocalReduceMotion

/** Compteur animé : chaque chiffre glisse verticalement quand il change. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    suffix: String = "",
) {
    val reduce = LocalReduceMotion.current
    val text = value.toString()
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    }
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
        if (suffix.isNotEmpty()) Text(suffix, style = style, color = color)
    }
}
