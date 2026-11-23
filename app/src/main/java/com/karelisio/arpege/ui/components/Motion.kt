package com.karelisio.arpege.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.karelisio.arpege.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Entrée en cascade d'un élément de liste (décalage par index). */
@Composable
fun Modifier.cascadeIn(index: Int, stepMs: Long = 45): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(index * stepMs)
            progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 60f
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
    }
}

/** Retours haptiques subtils. */
class Haptics(private val view: View) {
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun click() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
    fun reject() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )
    fun segment() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
