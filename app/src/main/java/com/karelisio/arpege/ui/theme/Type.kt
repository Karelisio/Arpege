package com.karelisio.arpege.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.karelisio.arpege.R

/** Police musicale SMuFL (OFL). */
val Bravura = FontFamily(Font(R.font.bravura))

private val base = Typography()

/** Typographie expressive : titres très gras et resserrés, corps lisible. */
val ArpegeTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val NumberStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 64.sp, letterSpacing = (-0.04).em)
