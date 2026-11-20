package com.turnypt.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
import com.turnypt.app.ui.theme.glassFrost
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

/** v1.5.7 perf: the one, forever-stable destination table (see TurnyptApp). */
private val NavDestinations = listOf(
    Destination(DestinationRoute.HOME, R.string.nav_home, TurnyptIcons.Home),
    Destination(DestinationRoute.ENCRYPT, R.string.nav_encrypt, TurnyptIcons.Lock),
    Destination(DestinationRoute.DECRYPT, R.string.nav_decrypt, TurnyptIcons.LockOpen),
    // v1.6.0-alpha.2 (user request): stego joins the MAIN menu as "Advanced
    // encryption" — رمزنگاری پیشرفته. The stego plan §6.1 put the frame
    // behind Quick Access only; the user wants it a first-class tab, so it
    // takes the middle slot between the two plain-crypto tabs and the
    // contacts/settings pair. Six slots stay comfortably legible at ≥48dp
    // per tap target on the pill (label size is unchanged).
    Destination(DestinationRoute.STEGO, R.string.nav_advanced_crypto, TurnyptIcons.Shield),
    Destination(DestinationRoute.CONTACTS, R.string.nav_contacts, TurnyptIcons.People),
    Destination(DestinationRoute.SETTINGS, R.string.nav_settings, TurnyptIcons.Settings),
)

@Composable
fun TurnyptApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val glass = LocalGlassTokens.current

    // v1.5.7 perf: the destination table is a compile-time constant —
    // it was rebuilt (five data class allocations + the list) on every
    // recomposition of the app root, which happens on EVERY tab change
    // (the back-stack entry state flips). Hoisted to the file scope;
    // recomposition now re-reads one object.
    val destinations = NavDestinations

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
            // UNSYNCHRONIZED — the outgoing screen faded out in 180ms
            // while the incoming was only two-thirds through its 250ms
            // fade, so the whole page dipped toward the backdrop before
            // the new screen arrived (a crossfade trough that reads as a
            // jump, not a glide). The transitions now share ONE clock:
            // enter and exit run the SAME 280ms span with FastOutSlowIn
            // easing (decelerating arrival — the Material standard
            // curve), and the exit no longer finishes early, so the
            // incoming screen is fully opaque exactly when the outgoing
            // completes — the trough is gone. The durations are still
            // quiet and deliberate; nothing got slower than it needs to
            // be — they just no longer disagree. The selection pill and
            // icon springs below were re-tuned to this same beat.
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
            // v1.5.6 (user report: "an unwanted opaque overlay sits between
            // the main content and the floating glass menu — it covers the
            // LOWER half of the menu's backdrop, so only the upper half
            // reads as translucent glass, and scrolling content appears from
            // behind it"): the host used to hard-cut its viewport 88dp above
            // the screen foot (.padding(bottom = 88.dp)). The bar is ~64dp
            // tall and floats as low as the navigation inset allows, so its
            // lower half floated over a dead strip that showed nothing but
            // the opaque backdrop — an opaque band sitting between the
            // content and the menu, exactly as reported — and scrolling
            // items vanished at the invisible cut line. The cut is now
            // GONE: the host fills the whole screen, every scrollable
            // scrolls UNDER the glass (content slides beneath the pane —
            // the point of a floating bar), and each screen gives its own
            // trailing clearance so the last resting item still clears the
            // bar. Nothing opaque remains between content and glass.
            modifier = Modifier
                .fillMaxSize()
                // v1.5.3 (user report: "the top of the app spills out a
                // bit"): edge-to-edge is enforced at targetSdk 36 and NO
                // status-bar inset existed anywhere in the root — the
                // screens' plain 16dp padding let content scroll under
                // the clock/status pills. The host insets its content
                // past the status bar here, once, for every screen.
                .statusBarsPadding(),
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
            // Stego frame (stego plan §6.1 S4): the UI skeleton lands
            // first — v1.6.0-alpha.3 moved it onto the SHARED
            // ToolsViewModel (contact picker + unlock, the ecosystem's
            // flow) instead of standalone local state.
            composable(DestinationRoute.STEGO) {
                StegoScreen(viewModel = hiltViewModel())
            }
        }

        // The floating glass navigation bar, pinned above the content.
        // v1.5.0 (user brief §5): respect the system navigation bar's
        // WindowInsets — edge-to-edge is enforced at targetSdk 36, so the
        // bar must inset itself above the gesture pill / 3-button strip
        // on every device, with no hardcoded per-device offsets.
        // v1.5.2 (user report: "a solid purple chunk at the bottom of
        // the app looks ugly"): the navTint layer was nearly opaque
        // (0xD8/0x8A alpha), so the bar read as a dense purple SLAB
        // floating over a quieter backdrop — a visibly different chunk
        // at the screen's foot. Glass harmonizes with what it floats
        // over: the tint is lifted to match the general glass weight,
        // letting the two-tone backdrop and the soft shadow carry the
        // pane's presence instead of raw opacity.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
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
    // v1.5.7 perf: one pill shape, allocated once per bar composition
    // instead of per slot row pass.
    val barShape = GlassRadii.pillShape
    val selectedIndex = destinations.indexOfFirst { selectedRoute(it) }
        .coerceAtLeast(0)

    // The selection pill slides between the slots; fraction in
    // [0, destinations.lastIndex] — five slots until v1.6.0-alpha.2 added
    // the stego tab, making six. v1.5.0 (user brief §4): critically
    // damped (NO overshoot —
    // no bounce), high stiffness — the pill lands immediately and the
    // motion stays cheap. The animated value is read ONLY inside the
    // offset/graphicsLayer lambdas (deferred state reads): a running
    // animation repaints the layer, it never recomposes the slots.
    //
    // v1.5.7 (user report: "the bottom navigation animation is too fast,
    // making transitions feel jumpy rather than smooth"): the spring
    // ran at the LIBRARY DEFAULT stiffness (10_000) — a ~120ms snap
    // that finished while the page beneath it was still mid-fade, so
    // the pill teleported and the page caught up: two motions at two
    // speeds, the textbook recipe for "jumpy". The pill is now tuned to
    // the same beat as the page transition it accompanies: stiffness
    // 380 with critical damping settles in ~280ms — it ARRIVES with the
    // incoming screen instead of racing it, still with zero overshoot
    // (labels never clip) and the same deferred-read structure (each
    // frame is one layer translate; the slots never recompose).
    // The target is the live index of the selected slot, so six
    // destinations slide exactly as five did — no constants to update.
    val selectionFraction = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 380f,
            visibilityThreshold = 0.01f,
        ),
        label = "navSelection",
    )

    // v1.4.1 bugfix: the Row of slots is the size-defining child (a real
    // measured height), and the sliding pill sits in a matchParentSize
    // wrapper BEHIND it. In v1.4.0 every child was matchParentSize — such
    // children are excluded from the parent's size resolution, so the bar
    // resolved to ZERO height and the whole navigation was invisible.
    //
    // v1.6.0-alpha.4 (user report: "the blur matters to me very, very
    // much"): a matchParentSize BLURRED underlay beneath the crisp pane.
    // The underlay is the scheme's own glass tint smeared by a RenderEffect
    // (Modifier.blur), spilling ~14dp past the pane so the frost reads as
    // a continuous halo wrapping the bar — REAL blur, not just opacity,
    // on API 31+ (below 31 it degrades to the honest frosted tint).
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(glass, barShape, tint = glass.navTint)
            .padding(6.dp),
    ) {
        // The blurred underlay FIRST (behind the crisp pane's children).
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassFrost(
                    shape = barShape,
                    frostTint = glass.frostTint,
                ),
        )
        val slotWidth = maxWidth / destinations.size
        // The sliding selection pill under the active slot's content.
        // v1.5.0 perf: the offset reads the animation INSIDE the lambda
        // (deferred read) — each frame is a layer translate only; the
        // five slots never recompose while the pill glides.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset {
                    IntOffset(
                        x = (slotWidth.toPx() * selectionFraction.value).roundToInt(),
                        y = 0,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        barShape,
                    ),
            )
        }
        Row {
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
    // The active icon settles a hair larger — quiet emphasis. v1.5.0 perf:
    // no-bouncy spring, and the scale is read only inside the
    // graphicsLayer lambda — the animation never recomposes the slot.
    // v1.5.2 (user report: "the app feels static — add animation
    // everywhere"): the spring now carries a LITTLE life —
    // MediumBouncy, not NoBouncy — a single soft settle, still quiet,
    // still cheap (same deferred-read structure). The selected color
    // also cross-fades so the tint change reads as motion, not a blink.
    //
    // v1.5.7 (user report: "jumpy rather than smooth"): this spring ran
    // at StiffnessMediumLow — a ~500ms+ settle with its bounce landing
    // a quarter-second AFTER the page transition had already finished,
    // a tail of motion on a settled screen (and five slots re-arming
    // their animators on every selection change). The settle is re-tuned
    // to the shared ~280ms beat: same MediumBouncy character (the one
    // soft overshoot stays — it is the deliberate life of v1.5.2),
    // stiffness raised to 1200 so it finishes WITH the pill's arrival
    // instead of dragging on after it. The alpha cross-fade follows the
    // same clock (280ms FastOutSlowIn, matching the page fade).
    val iconScale = animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 1200f,
            visibilityThreshold = 0.01f,
        ),
        label = "navIconScale",
    )
    val iconAlpha = animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing,
        ),
        label = "navIconAlpha",
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
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconAlpha.value
                },
        )
        // v1.6.0-alpha.3 (user report: "the Advanced encryption label
        // overflows into the walls"): the label ran at a fixed 10sp with
        // maxLines=1 — five short English labels fit, but the sixth
        // ("Advanced encryption" / رمزنگاری پیشرفته) blew past its slot
        // and clipped into neighbouring slots. The slot is now
        // width-aware: the label auto-shrinks (BasicText autoSize with
        // TextAutoSize StepBased, 8sp floor) AND ellipsizes when even
        // 8sp cannot fit — every label legible inside its own slot,
        // whatever the language or bar width. Long labels keep their
        // full text on every realistic device; a 320dp-screen six-slot
        // worst case ellipsizes gracefully instead of bleeding.
        BasicText(
            text = stringResource(destination.labelRes),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 26.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                textAlign = TextAlign.Center,
            ),
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 10.sp,
                stepSize = 0.5.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

