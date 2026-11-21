package com.turnypt.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavArgument
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.turnypt.app.ContactsViewModel
import com.turnypt.app.LocalBiometrics
import com.turnypt.app.MainViewModel
import com.turnypt.app.R
import com.turnypt.app.SettingsViewModel
import com.turnypt.app.ToolsViewModel
import com.turnypt.app.companion.DestinationRoute
import com.turnypt.app.security.BiometricKeyStore

/**
 * Six-tab main navigation: Home, Encrypt, Decrypt, Advanced encryption
 * (stego), Contacts, Settings. The Encrypt and Decrypt tabs host the
 * in-app tools (manual input); everything else stays where it was.
 *
 * v1.6.0-beta.1 — THE GLASS IS GONE. The v1.4.0 floating "Liquid Glass"
 * pill bar (translucent pane, per-frame glassCapture of the whole
 * NavHost, BlurEffect backdrop pane, lens refraction) is deleted by
 * user decree: "back to the previous main design without any glass,
 * and any lag it brought". The bar is the standard docked Material
 * [NavigationBar] in a [Scaffold] — the pre-glass v1.3.2 design.
 *
 * What deliberately SURVIVES the revert (they are features, not glass):
 *  - the six-tab set (the v1.6.0-alpha.2 Advanced encryption tab stays
 *    a first-class slot);
 *  - the 280ms single-clock crossfade transitions (the v1.5.7 fix);
 *  - the base-route tab matching, retap-to-default behaviour, deep
 *    links (the v1.2.0/v0.6.0 fixes);
 *  - edge-to-edge insets: the Scaffold's own NavigationBar handles
 *    the navigation bar inset natively, and contentPadding keeps the
 *    NavHost clear of the bar — the v1.5.3 status-bar inset is kept
 *    via the screens' Scaffold-aware padding.
 */
private data class Destination(val route: String, val labelRes: Int, val icon: ImageVector)

/** v1.5.7 perf: the one, forever-stable destination table. */
private val NavDestinations = listOf(
    Destination(DestinationRoute.HOME, R.string.nav_home, TurnyptIcons.Home),
    Destination(DestinationRoute.ENCRYPT, R.string.nav_encrypt, TurnyptIcons.Lock),
    Destination(DestinationRoute.DECRYPT, R.string.nav_decrypt, TurnyptIcons.LockOpen),
    // v1.6.0-alpha.2 (user request): stego joins the MAIN menu as "Advanced
    // encryption" — رمزنگاری پیشرفته. Six slots on the standard bar.
    Destination(DestinationRoute.STEGO, R.string.nav_advanced_crypto, TurnyptIcons.Shield),
    Destination(DestinationRoute.CONTACTS, R.string.nav_contacts, TurnyptIcons.People),
    Destination(DestinationRoute.SETTINGS, R.string.nav_settings, TurnyptIcons.Settings),
)

@Composable
fun TurnyptApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // v1.5.7 perf: the destination table is a compile-time constant —
    // recomposition re-reads one object, never rebuilds the list.
    val destinations = NavDestinations

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    // v1.2.0 (user report: "the bottom navigation bar fails to
                    // reflect the active page, and taps seem dead"): the
                    // Encrypt/Decrypt destinations live in the graph under
                    // QUERY-PATTERN routes ("encrypt?mode={mode}") — matching
                    // on the base route (the pattern before the '?') keeps
                    // every tab honestly selected, whatever arguments it was
                    // opened with.
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route?.substringBefore('?') == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val currentBase =
                                backStackEntry?.destination?.route?.substringBefore('?')
                            if (currentBase == destination.route) {
                                // Already on this tab. If it was deep-linked with
                                // non-default arguments (e.g. the image segment
                                // via Home), one tap returns the tab to its
                                // default state — a visible, honest response;
                                // at the default it is a no-op, like every
                                // standard bottom bar.
                                val mode = backStackEntry?.arguments
                                    ?.getString(DestinationRoute.ARG_MODE)
                                if (mode != null && mode != DestinationRoute.MODE_TEXT) {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(destination.labelRes),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DestinationRoute.HOME,
            modifier = androidx.compose.ui.Modifier.padding(padding),
            // v1.5.2 (user report: "the bottom menu flies to the Settings
            // page with NO animation"): every tab change — Settings
            // included — now transitions. One deliberate motion, not a
            // circus: the incoming screen fades in while sliding up a
            // short 10% distance, the outgoing fades out — the app's calm
            // tone kept. Pop (back) mirrors it so the back gesture feels
            // the same in reverse.
            //
            // v1.5.7 (user report: "the app feels laggy; the bottom
            // navigation animation is too fast — transitions feel jumpy
            // rather than smooth"): the three motion clocks were
            // UNSYNCHRONIZED. The transitions now share ONE clock: enter
            // and exit run the SAME 280ms span with FastOutSlowIn easing,
            // so the incoming screen is fully opaque exactly when the
            // outgoing completes. The durations are quiet and deliberate.
            enterTransition = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                    initialOffsetY = { it / 10 },
                )
            },
            exitTransition = {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                )
            },
            popEnterTransition = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                )
            },
            popExitTransition = {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                    targetOffsetY = { it / 10 },
                )
            },
        ) {
            composable(DestinationRoute.HOME) {
                HomeScreen(
                    viewModel = hiltViewModel<MainViewModel>(),
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            // v0.6.0: optional mode argument (text | image) — Quick Access
            // deep-links into the image segments of both tabs.
            composable(
                "${DestinationRoute.ENCRYPT}?${DestinationRoute.ARG_MODE}={${DestinationRoute.ARG_MODE}}",
                arguments = listOf(
                    navArgument(DestinationRoute.ARG_MODE) {
                        defaultValue = DestinationRoute.MODE_TEXT
                    },
                ),
            ) { entry ->
                val mode = entry.arguments?.getString(DestinationRoute.ARG_MODE)
                EncryptScreen(
                    viewModel = hiltViewModel<ToolsViewModel>(),
                    biometrics = LocalBiometrics.current,
                    initialImage = mode == DestinationRoute.MODE_IMAGE,
                )
            }
            composable(
                "${DestinationRoute.DECRYPT}?${DestinationRoute.ARG_MODE}={${DestinationRoute.ARG_MODE}}",
                arguments = listOf(
                    navArgument(DestinationRoute.ARG_MODE) {
                        defaultValue = DestinationRoute.MODE_TEXT
                    },
                ),
            ) { entry ->
                val mode = entry.arguments?.getString(DestinationRoute.ARG_MODE)
                DecryptScreen(
                    viewModel = hiltViewModel<ToolsViewModel>(),
                    biometrics = LocalBiometrics.current,
                    initialImage = mode == DestinationRoute.MODE_IMAGE,
                )
            }
            composable(DestinationRoute.CONTACTS) {
                ContactsScreen(
                    viewModel = hiltViewModel<ContactsViewModel>(),
                    biometrics = LocalBiometrics.current,
                )
            }
            composable(DestinationRoute.SETTINGS) {
                SettingsScreen(
                    viewModel = hiltViewModel<SettingsViewModel>(),
                    contactsViewModel = hiltViewModel<ContactsViewModel>(),
                )
            }
            // Stego frame (stego plan §6.1 S4): the Advanced encryption tab
            // on the SHARED ToolsViewModel (contact picker + unlock, the
            // ecosystem's flow).
            composable(DestinationRoute.STEGO) {
                StegoScreen(viewModel = hiltViewModel())
            }
        }
    }
}
