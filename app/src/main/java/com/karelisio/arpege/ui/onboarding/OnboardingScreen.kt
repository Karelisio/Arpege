package com.karelisio.arpege.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.ArpeggioLogo
import com.karelisio.arpege.ui.components.SectionShapes
import com.karelisio.arpege.ui.components.ShapedIcon
import com.karelisio.arpege.ui.components.cascadeIn
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.nav.Section
import com.karelisio.arpege.ui.permissions.AppPermission
import com.karelisio.arpege.ui.permissions.PermissionPage
import com.karelisio.arpege.ui.permissions.rememberPermissionsController
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val sectionBlurbs = mapOf(
    Section.TOOLS to "Accordeur, métronome et tablatures.",
    Section.THEORY to "Accords, gammes et cercle des quintes.",
    Section.TRAINING to "Oreille, rythme et lecture de notes.",
    Section.JOURNAL to "Séances, objectif hebdo et séries.",
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val permissions = AppPermission.entries
    val pageCount = 2 + permissions.size
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val controller = rememberPermissionsController()
    val haptics = rememberHaptics()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Passer") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue
                    alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f
                    val s = 1f - offset.coerceIn(0f, 1f) * 0.12f
                    scaleX = s; scaleY = s
                },
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    0 -> WelcomePage(pager.currentPage == 0)
                    1 -> SectionsPage(pager.currentPage == 1)
                    else -> PermissionPage(permissions[page - 2], controller)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(pageCount) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotc",
                    )
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            val last = pager.currentPage == pageCount - 1
            AnimatedContent(last, label = "next") { isLast ->
                Button(onClick = {
                    haptics.click()
                    if (isLast) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) { Text(if (isLast) "Commencer" else "Suivant") }
            }
        }
    }
}

@Composable
private fun WelcomePage(visible: Boolean) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(180.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { ArpeggioLogo(150.dp, MaterialTheme.colorScheme.onPrimaryContainer, key = visible) }
        Text("Arpège", style = MaterialTheme.typography.displayLarge)
        Text(
            "Ton atelier musical de poche pour la guitare, le ukulélé, la basse et le piano.\nLocal, sans compte, hors ligne.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionsPage(visible: Boolean) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quatre espaces", style = MaterialTheme.typography.headlineLarge)
        if (visible) Section.entries.forEachIndexed { i, s ->
            Card(
                modifier = Modifier.fillMaxWidth().cascadeIn(i, 90),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapedIcon(s.icon, shape = SectionShapes[i * 2 % SectionShapes.size], size = 52.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(s.label, style = MaterialTheme.typography.titleLarge)
                        Text(sectionBlurbs[s].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
