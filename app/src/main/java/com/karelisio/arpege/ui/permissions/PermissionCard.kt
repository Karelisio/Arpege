package com.karelisio.arpege.ui.permissions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.arpege.ui.components.ShapedIcon
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.theme.LocalReduceMotion

/** Icône d'autorisation : forme qui respire, remplacée par une coche animée une fois accordée. */
@Composable
fun PermissionBadge(permission: AppPermission, granted: Boolean, size: androidx.compose.ui.unit.Dp = 120.dp) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    var wasGranted by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) { if (granted && !wasGranted) haptics.confirm(); wasGranted = granted }
    val breathe = rememberInfiniteTransition(label = "breathe")
    val rot by breathe.animateFloat(0f, if (reduce) 0f else 360f, infiniteRepeatable(tween(14000)), label = "rot")
    val scale by breathe.animateFloat(1f, if (reduce) 1f else 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "scale")
    AnimatedContent(
        targetState = granted,
        transitionSpec = {
            (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.4f) + fadeIn()) togetherWith fadeOut()
        },
        label = "badge",
    ) { ok ->
        if (ok) {
            ShapedIcon(
                Icons.Rounded.Check, shape = MaterialShapes.Cookie9Sided, size = size,
                container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            ShapedIcon(
                permission.icon, shape = MaterialShapes.Cookie12Sided, size = size,
                modifier = Modifier.graphicsLayer { rotationZ = rot; scaleX = scale; scaleY = scale },
            )
        }
    }
}

/** Page d'autorisation (onboarding) : icône, explication, bouton Autoriser. */
@Composable
fun PermissionPage(permission: AppPermission, controller: PermissionsController) {
    val granted = controller.granted[permission] == true
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PermissionBadge(permission, granted, 140.dp)
        Text(permission.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            permission.rationale, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedContent(granted, label = "action") { ok ->
            if (ok) Text("Autorisation accordée", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium)
            else Button(onClick = { controller.request(permission) }, modifier = Modifier.size(width = 200.dp, height = 56.dp)) {
                Text("Autoriser")
            }
        }
        if (permission == AppPermission.BATTERY) {
            TextButton(onClick = controller::openVendorAutostart) { Text("Lancement auto (Honor, Xiaomi…)") }
        }
    }
}

/** Ligne compacte (écran Autorisations des réglages). */
@Composable
fun PermissionRow(permission: AppPermission, controller: PermissionsController) {
    val granted = controller.granted[permission] == true
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PermissionBadge(permission, granted, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(permission.title, style = MaterialTheme.typography.titleMedium)
                Text(permission.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (permission == AppPermission.BATTERY) {
                    TextButton(onClick = controller::openVendorAutostart, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Lancement auto (Honor, Xiaomi…)")
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!granted) FilledTonalButton(onClick = { controller.request(permission) }) { Text("Autoriser") }
            else Icon(Icons.Rounded.Check, contentDescription = "Accordée", tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}
