package com.karelisio.arpege.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Palette de repli chaude : ambre et bois.
private val LightWarm = lightColorScheme(
    primary = Color(0xFF8B5000), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE), onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF735943), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC3), onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF5C6237), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E8B1), onTertiaryContainer = Color(0xFF191E00),
    background = Color(0xFFFFF8F4), onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F4), onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD1), onSurfaceVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1E7),
    surfaceContainer = Color(0xFFF9EBE1), surfaceContainerHigh = Color(0xFFF3E5DB),
    surfaceContainerHighest = Color(0xFFEDE0D6),
    outline = Color(0xFF857468), outlineVariant = Color(0xFFD7C3B5),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val DarkWarm = darkColorScheme(
    primary = Color(0xFFFFB870), onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00), onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE2C0A5), onSecondary = Color(0xFF412C19),
    secondaryContainer = Color(0xFF5A422D), onSecondaryContainer = Color(0xFFFFDCC3),
    tertiary = Color(0xFFC5CC97), onTertiary = Color(0xFF2E330D),
    tertiaryContainer = Color(0xFF444A22), onTertiaryContainer = Color(0xFFE1E8B1),
    background = Color(0xFF1A120C), onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF1A120C), onSurface = Color(0xFFEFE0D5),
    surfaceVariant = Color(0xFF52443A), onSurfaceVariant = Color(0xFFD7C3B5),
    surfaceContainerLowest = Color(0xFF140D08), surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF271E17), surfaceContainerHigh = Color(0xFF322821),
    surfaceContainerHighest = Color(0xFF3D332B),
    outline = Color(0xFFA08D81), outlineVariant = Color(0xFF52443A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val ArpegeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Vrai quand l'utilisateur a désactivé les animations (Réglages > Accessibilité). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ArpegeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkWarm
        else -> LightWarm
    }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            shapes = ArpegeShapes,
            typography = ArpegeTypography,
            content = content,
        )
    }
}
