package com.karelisio.arpege.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.ShapedIcon
import com.karelisio.arpege.ui.components.SectionShapes
import com.karelisio.arpege.ui.components.cascadeIn
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.nav.Section
import com.karelisio.arpege.ui.nav.sharedBoundsKey

private val taglines = mapOf(
    Section.TOOLS to "Accorder, garder le tempo, jouer.",
    Section.THEORY to "Comprendre ce que tu joues.",
    Section.TRAINING to "L'oreille et l'œil, chaque jour.",
)

@Composable
fun HubScreen(section: Section, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val haptics = rememberHaptics()
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 16.dp)) {
                    Text(section.label, style = MaterialTheme.typography.displayMedium)
                    Text(
                        taglines[section].orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Réglages") }
            }
        }
        itemsIndexed(section.tools) { index, tool ->
            val shapes = SectionShapes
            Card(
                onClick = { haptics.click(); onOpen(tool.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .cascadeIn(index)
                    .sharedBoundsKey("tool-${tool.route}"),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = when (index % 3) {
                        0 -> MaterialTheme.colorScheme.primaryContainer
                        1 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                ),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShapedIcon(
                        tool.icon,
                        shape = shapes[(section.ordinal * 3 + index) % shapes.size],
                        size = 64.dp,
                        container = MaterialTheme.colorScheme.surface,
                        content = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tool.title, style = MaterialTheme.typography.headlineSmall)
                        Text(tool.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
