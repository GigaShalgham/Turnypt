package com.turnypt.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.turnypt.app.ui.theme.GlassRadii
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.glassBackdrop
import com.turnypt.app.ui.theme.glassSurface

/**
 * Five-tab main navigation (v0.3.0): Home, Encrypt, Decrypt, Contacts,
 * Settings. The Encrypt and Decrypt tabs host the in-app tools (manual
 * input); everything else stays where it was.
 *
 * v1.4.0 "Liquid Glass": the flat docked NavigationBar becomes a FLOATING
 * glass pill bar — the Liquid Glass navigation pattern. The bar floats
 * over a content area that runs edge-to-edge beneath it; selection is a
 * spring-animated primary-container pill sliding between the five slots,
 * with the label fading in and a soft scale on the active icon. The
 * routes, the tab-selection logic (base-route matching, the v1.2.0 fix)
 * and the deep-link retap behaviour are UNTOUCHED — this is a visual
 * layer, not a navigation rewrite.
 */
private data class Destination(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun TurnyptApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val glass = LocalGlassTokens.current

    val destinations = listOf(
        Destination(DestinationRoute.HOME, R.string.nav_home, TurnyptIcons.Home),
        Destination(DestinationRoute.ENCRYPT, R.string.nav_encrypt, TurnyptIcons.Lock),
        Destination(DestinationRoute.DECRYPT, R.string.nav_decrypt, TurnyptIcons.LockOpen),
        Destination(DestinationRoute.CONTACTS, R.string.nav_contacts, TurnyptIcons.People),
        Destination(DestinationRoute.SETTINGS, R.string.nav_settings, TurnyptIcons.Settings),
    )

    // The quiet two-tone backdrop: the whole content area sits on it, so
    // every glass surface (cards, the floating bar) samples real depth.
    // The bar overlays the content bottom — screens keep their own
    // bottom padding through the contentPadding passed to NavHost.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassBackdrop(glass),
    ) {
        NavHost(
            navController = navController,
            startDestination = DestinationRoute.HOME,
            modifier = Modifier
                .fillMaxSize()
                // Room for the floating bar: the host insets its content
                // so no scrollable's last item hides under the glass.
                .padding(bottom = 88.dp),
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
        }

        // The floating glass navigation bar, pinned above the content.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            FloatingGlassNavBar(
                destinations = destinations,
                selectedRoute = { destination ->
                    // v1.2.0 (user report: "the bottom navigation bar fails to
                    // reflect the active page, and taps seem dead"): the
                    // Encrypt/Decrypt destinations live in the graph under
                    // QUERY-PATTERN routes ("encrypt?mode={mode}") — matching
                    // on the base route (the pattern before the '?') keeps
                    // every tab honestly selected, whatever arguments it was
                    // opened with.
                    backStackEntry?.destination?.hierarchy
                        ?.any { it.route?.substringBefore('?') == destination.route } == true
                },
                onSelect = { destination ->
                    val currentBase =
                        backStackEntry?.destination?.route?.substringBefore('?')
                    if (currentBase == destination.route) {
                        // Already on this tab. If it was deep-linked with
                        // non-default arguments (e.g. the image segment via
                        // Home), one tap returns the tab to its default
                        // state — a visible, honest response; at the default
                        // it is a no-op, like every standard bottom bar.
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
            )
        }
    }
}

/**
 * The floating Liquid Glass navigation bar: one glass pill, five slots,
 * and a selection pill that SPRINGS between slots (overshoot tuned small
 * so labels never clip). Each slot is a ≥48dp target with a visible
 * label — icon + label, never icon alone.
 */
@Composable
private fun FloatingGlassNavBar(
    destinations: List<Destination>,
    selectedRoute: (Destination) -> Boolean,
    onSelect: (Destination) -> Unit,
) {
    val glass = LocalGlassTokens.current
    val barShape = RoundedCornerShape(GlassRadii.pill)
    val selectedIndex = destinations.indexOfFirst { selectedRoute(it) }
        .coerceAtLeast(0)

    // The selection pill slides between the five slots; fraction in
    // [0, 4]. A restrained spring (high stiffness, little overshoot) —
    // liquid, not bouncy.
    val selectionFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "navSelection",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(glass, barShape, tint = glass.navTint)
            .padding(6.dp),
    ) {
        val slotWidth = maxWidth / destinations.size
        // The sliding selection pill under the active slot's content.
        Box(
            modifier = Modifier
                .width(slotWidth)
                .matchParentSize()
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    barShape,
                )
                .offset(x = slotWidth * selectionFraction),
        )
        Row(modifier = Modifier.matchParentSize()) {
            destinations.forEach { destination ->
                val selected = selectedRoute(destination)
                NavBarSlot(
                    destination = destination,
                    selected = selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One nav slot: icon + label, tinted by selection state. */
@Composable
private fun NavBarSlot(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The active icon settles a hair larger — quiet emphasis.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "navIconScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            stringResource(destination.labelRes),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = fg,
            maxLines = 1,
        )
    }
}
