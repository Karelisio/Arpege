package com.karelisio.arpege.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun ArpegeRoot(nav: NavHostController) {
    val c = LocalContext.current.container
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
}
