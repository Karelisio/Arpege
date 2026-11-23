package com.karelisio.arpege.ui.nav

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.karelisio.arpege.ui.components.ComingSoon
import com.karelisio.arpege.ui.hub.HubScreen
import com.karelisio.arpege.ui.settings.SettingsScreen
import com.karelisio.arpege.ui.permissions.PermissionsScreen
import com.karelisio.arpege.ui.tuner.TunerScreen
import com.karelisio.arpege.ui.metronome.MetronomeScreen

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments, deepLinks) { entry ->
    CompositionLocalProvider(LocalNavAnimScope provides this) { content(entry) }
}

@Composable
fun ArpegeNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    val open: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val settings: () -> Unit = { open(Routes.SETTINGS) }

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = Routes.TOOLS,
                enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.96f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.96f) },
            ) {
                screen(Routes.TOOLS) { HubScreen(Section.TOOLS, open, settings) }
                screen(Routes.THEORY) { HubScreen(Section.THEORY, open, settings) }
                screen(Routes.TRAINING) { HubScreen(Section.TRAINING, open, settings) }
                screen(Routes.JOURNAL) { ComingSoon("Journal", back) }

                screen(Routes.TUNER, deepLinks = listOf(navDeepLink { uriPattern = "arpege://tuner" })) {
                    TunerScreen(back)
                }
                screen(
                    "${Routes.METRONOME}?bpm={bpm}",
                    arguments = listOf(navArgument("bpm") { type = NavType.IntType; defaultValue = -1 }),
                    deepLinks = listOf(navDeepLink { uriPattern = "arpege://metronome?bpm={bpm}" }, navDeepLink { uriPattern = "arpege://metronome" }),
                ) { entry -> MetronomeScreen(back, entry.arguments?.getInt("bpm")?.takeIf { it > 0 }) }
                screen(Routes.TABS) { ComingSoon("Tablatures", back, "tool-${Routes.TABS}") }
                screen(
                    "${Routes.CHORDS}?name={name}",
                    arguments = listOf(navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    deepLinks = listOf(navDeepLink { uriPattern = "arpege://chord?name={name}" }),
                ) { ComingSoon("Accords", back, "tool-${Routes.CHORDS}") }
                screen(Routes.SCALES) { ComingSoon("Gammes & modes", back, "tool-${Routes.SCALES}") }
                screen(Routes.CIRCLE) { ComingSoon("Cercle des quintes", back, "tool-${Routes.CIRCLE}") }
                screen(Routes.EAR) { ComingSoon("Oreille", back, "tool-${Routes.EAR}") }
                screen(Routes.RHYTHM) { ComingSoon("Rythme", back, "tool-${Routes.RHYTHM}") }
                screen(Routes.SOLFEGE) { ComingSoon("Solfège", back, "tool-${Routes.SOLFEGE}") }
                screen(Routes.SETTINGS) { SettingsScreen(onBack = back, onPermissions = { open(Routes.PERMISSIONS) }) }
                screen(Routes.PERMISSIONS) { PermissionsScreen(back) }
            }
        }
    }
}
