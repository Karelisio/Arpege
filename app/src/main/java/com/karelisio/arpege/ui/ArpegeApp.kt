package com.karelisio.arpege.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.karelisio.arpege.data.CrashReporter
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.karelisio.arpege.ui.onboarding.OnboardingScreen
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.karelisio.arpege.data.container
import com.karelisio.arpege.ui.components.rememberHaptics
import com.karelisio.arpege.ui.nav.ArpegeNavHost
import com.karelisio.arpege.ui.nav.Section
import com.karelisio.arpege.update.UpdateSheet

/** Racine : NavigationSuiteScaffold (barre, rail ou tiroir selon la taille d'écran). */
@Composable
fun ArpegeRoot(nav: NavHostController, onboardingDone: Boolean) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    if (!onboardingDone) {
        OnboardingScreen(onDone = { scope.launch { c.settings.setOnboardingDone(true) } })
        return
    }
    val entry by nav.currentBackStackEntryAsState()
    val current = Section.of(entry?.destination?.route) ?: Section.TOOLS
    val haptics = rememberHaptics()
    val update by c.updates.state.collectAsState()

    LaunchedEffect(Unit) { c.updates.checkIfDue() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Section.entries.forEach { s ->
                item(
                    selected = s == current,
                    onClick = {
                        haptics.tick()
                        nav.navigate(s.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(s.icon, contentDescription = null) },
                    label = { Text(s.label) },
                )
            }
        },
    ) {
        ArpegeNavHost(nav)
    }

    UpdateSheet(state = update, onDownload = c.updates::download, onDismiss = c.updates::dismiss)
    CrashReportDialog()
}

/** Propose de partager le rapport du dernier plantage (local, jamais envoyé automatiquement). */
@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.pending(context)) }
    val r = report ?: return
    AlertDialog(
        onDismissRequest = { CrashReporter.clear(context); report = null },
        title = { Text("Arpège s'est arrêté") },
        text = { Text("L'appli a planté lors de la dernière utilisation. Tu peux partager le rapport (texte, aucune donnée personnelle) pour aider à corriger le problème.") },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(CrashReporter.shareIntent(r)) }
                CrashReporter.clear(context); report = null
            }) { Text("Partager") }
        },
        dismissButton = { TextButton(onClick = { CrashReporter.clear(context); report = null }) { Text("Ignorer") } },
    )
}
