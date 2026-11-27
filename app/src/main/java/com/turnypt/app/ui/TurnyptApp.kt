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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
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
import com.turnypt.app.ui.theme.GlassRadii
import com.turnypt.app.ui.theme.LiquidGlass
import com.turnypt.app.ui.theme.LiquidGlassColors
import com.turnypt.app.ui.theme.LiquidGlassKind
import com.turnypt.app.ui.theme.LiquidGlassScene
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.LocalLiquidGlassScene
import com.turnypt.app.ui.theme.flatGlassSurface
import com.turnypt.app.ui.theme.glassBackdrop
import com.turnypt.app.ui.theme.liquidGlassAnchor
import com.turnypt.app.ui.theme.liquidGlassColors
import com.turnypt.app.ui.theme.liquidGlassField
import com.turnypt.app.ui.theme.liquidGlassPane
import com.turnypt.app.ui.theme.liquidGlassSource
import com.turnypt.app.ui.theme.liquidRim
import com.turnypt.app.ui.theme.rememberLiquidGlassAnchor
import com.turnypt.app.ui.theme.rememberLiquidGlassScene

/**
 * Six-tab main navigation: Home, Encrypt, Decrypt, Advanced encryption
 * (stego), Contacts, Settings. The Encrypt and Decrypt tabs host the
 * in-app tools (manual input); everything else stays where it was.
 *
 * v1.4.0 "Liquid Glass": the flat docked NavigationBar becomes a FLOATING
 * glass pill bar — the Liquid Glass navigation pattern. The bar floats
 * over a content area that runs edge-to-edge beneath it; selection is a
 * spring-animated lens sliding between the six slots, with the label
 * fading in and a soft scale on the active icon. The routes, the
 * tab-selection logic (base-route matching, the v1.2.0 fix) and the
 * deep-link retap behaviour are UNTOUCHED — this is a visual layer, not a
 * navigation rewrite.
 *
 * v1.6.0-beta.13: the root OWNS the liquid glass scene
 * ([LiquidGlassScene]) — the field, the recorded page and the tier — and
 * hands it down through [LocalLiquidGlassScene], so every glass surface
 * in the window (this bar, the cards, the FAB) is made of the same
 * material. See theme/LiquidGlass.kt.
 */
// v1.6.0-beta.13: internal (was private) so the desktop render harness
// (LiquidGlassRenderTest) draws the REAL bar, not a copy of it.
internal data class Destination(val route: String, val labelRes: Int, val icon: ImageVector)

/** v1.5.7 perf: the one, forever-stable destination table (see TurnyptApp). */
internal val NavDestinations = listOf(
    Destination(DestinationRoute.HOME, R.string.nav_home, TurnyptIcons.Home),
    Destination(DestinationRoute.ENCRYPT, R.string.nav_encrypt, TurnyptIcons.Lock),
    Destination(DestinationRoute.DECRYPT, R.string.nav_decrypt, TurnyptIcons.LockOpen),
    // v1.6.0-beta.6 (user request): stego stays a first-class slot —
    // "Advanced encryption" — now on the RESTORED floating glass bar
    // (the v1.5.10 design the user decreed back).
    Destination(DestinationRoute.STEGO, R.string.nav_advanced_crypto, TurnyptIcons.Shield),
    Destination(DestinationRoute.CONTACTS, R.string.nav_contacts, TurnyptIcons.People),
    Destination(DestinationRoute.SETTINGS, R.string.nav_settings, TurnyptIcons.Settings),
)

@Composable
fun TurnyptApp(liquidGlassMenu: Boolean = true) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val glass = LocalGlassTokens.current
    // v1.6.0-beta.13: ONE scene per window — the tier, the light, the
    // field source and the page source. Null on the static tier (below
    // Android 12, low-RAM, or switched off in Settings): every surface
    // then keeps the flat v1.5 look and nothing is captured at all.
    val scene = rememberLiquidGlassScene(enabled = liquidGlassMenu)

    // v1.5.7 perf: the destination table is a compile-time constant —
    // it was rebuilt (five data class allocations + the list) on every
    // recomposition of the app root, which happens on EVERY tab change
    // (the back-stack entry state flips). Hoisted to the file scope;
    // recomposition now re-reads one object.
    val destinations = NavDestinations

    CompositionLocalProvider(LocalLiquidGlassScene provides scene) {
        Box(
            // The window's field. On the live tiers it is the recorded
            // light field every card samples ([liquidGlassField]); on the
            // static tier it is the same base colour, painted flat.
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scene != null) Modifier.liquidGlassField(scene)
                    else Modifier.glassBackdrop(glass),
                ),
        ) {
            // v1.6.0-beta.11: ONLY the content area feeds the glass. The bar
            // below is a sibling, outside the recording, so the pane can never
            // see itself.
            //
            // v1.6.0-beta.13 (user report: the menu "has many bugs"): with
            // edge-to-edge enforced at targetSdk 36 Android 15 no longer
            // resizes the window for `adjustResize` — the keyboard simply
            // covered the bar and the lower fields. The content now pads
            // itself by the IME inset, and the bar rides on top of the
            // keyboard instead of disappearing under it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scene != null) Modifier.liquidGlassSource(scene.page) else Modifier,
                    )
                    .imePadding(),
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
                    // be — they just no longer disagree. The selection lens and
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
                    // bar (Modifier.bottomClearance(), theme/Glass.kt). Nothing
                    // opaque remains between content and glass.
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

                    // v1.6.0-beta.6: the stego screen on the restored glass base.
                    // v1.6.0-beta.8: biometrics flow in — the screen now hosts the
                    // fingerprint prompt + button the other tabs show.
                    composable(DestinationRoute.STEGO) {
                        StegoScreen(
                            viewModel = hiltViewModel<ToolsViewModel>(),
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
            }

            // The floating glass navigation bar, pinned above the content.
            // v1.5.0 (user brief §5): respect the system navigation bar's
            // WindowInsets — edge-to-edge is enforced at targetSdk 36, so the
            // bar must inset itself above the gesture pill / 3-button strip
            // on every device, with no hardcoded per-device offsets.
            // v1.6.0-beta.13: also above the KEYBOARD. The two insets are
            // united, so the bar sits over whichever is taller — the
            // navigation strip or the open IME — and never under either.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
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
                    scene = scene,
                    modifier = Modifier.fillMaxWidth(),
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
}

/**
 * The floating Liquid Glass navigation bar.
 *
 * One glass pill, six slots and a selection LENS that springs between
 * them. On the live tiers both are panes of the same material
 * ([liquidGlassPane]), each a COPY of the recorded page, frosted and bent
 * by its own RenderEffect chain:
 *
 *  - the BAR samples the page under its own footprint (blur 14dp);
 *  - the LENS is a second, clearer pane (blur 6dp, magnified) that
 *    samples the page at the slot it currently floats over — so it reads
 *    as a thick droplet of the same glass rather than as paint.
 *
 * The light on top of a pane is one flat tint plus gradients that span
 * the whole clip. Nothing soft is a hard-edged shape: beta.11's
 * BlurMaskFilter is not rendered by the hardware canvas at all, and
 * beta.12's replacement (a hard inner rounded-rect, a ring and a 7dp
 * specular stripe) reached the device as "a pill inside the pill" and a
 * white band over the selected tab — the bugs this rewrite removes.
 *
 * Every animated value is read ONLY inside graphicsLayer / drawBehind
 * lambdas (deferred state reads): a running animation repaints a layer,
 * it never recomposes the slots. Routes, selection and retap behaviour
 * live in [TurnyptApp] and are untouched.
 */
@Composable
internal fun FloatingGlassNavBar(
    destinations: List<Destination>,
    selectedRoute: (Destination) -> Boolean,
    onSelect: (Destination) -> Unit,
    scene: LiquidGlassScene?,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassTokens.current
    // v1.5.7 perf: one pill shape, allocated once per bar composition.
    val barShape = GlassRadii.pillShape
    val live = scene != null
    val colors = liquidGlassColors()
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = destinations.indexOfFirst { selectedRoute(it) }
        .coerceAtLeast(0)
    // translationX is NOT mirrored in RTL, so the sign is.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    // The selection lens slides between the slots (fraction in [0, 5]).
    // The same ~280ms beat as the page transition, with a breath of
    // overshoot: a liquid lens may wobble a hair past its slot — it
    // carries no label, so nothing can clip.
    val selectionFraction = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 420f,
            visibilityThreshold = 0.01f,
        ),
        label = "navSelection",
    )

    val rimBrush = remember(colors) {
        Brush.verticalGradient(0f to colors.rimTop, 1f to colors.rimBottom)
    }
    val lensRimBrush = remember(colors) { SolidColor(colors.lensRim) }

    // The pill's own material: the live pane (a frosted, bent copy of the
    // page) with the rim over it, or the flat v1.5 surface on the static
    // tier.
    val barSurface = if (scene != null) {
        Modifier
            .liquidGlassPane(
                scene = scene,
                shape = barShape,
                kind = LiquidGlassKind.BAR,
                tint = colors.paneTint,
                content = scene.page,
            )
            .liquidRim(barShape, rimBrush)
    } else {
        Modifier.flatGlassSurface(glass, barShape, tint = glass.navTint)
    }

    // v1.4.1 (kept): the slot Row is the size-defining child. The pane and
    // the lens are matchParentSize wrappers, which a Box leaves out of its
    // own size resolution — with ONLY such children the bar was 0dp tall.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = barShape,
                clip = true,
                ambientColor = glass.shadowColor,
                spotColor = glass.shadowColor,
            )
            .then(barSurface),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
        ) {
            val density = LocalDensity.current
            val slotWidth = maxWidth / destinations.size
            val slotPx = with(density) { slotWidth.toPx() }
            // v1.6.0-beta.13: the lens travels by a layer translation and
            // stretches by a layer scale, so its LAYOUT position never
            // changes — the pane is pointed at its real place by an anchor
            // outside those transforms plus the translation itself.
            val anchor = rememberLiquidGlassAnchor()

            // The selection lens under the active slot: one layer translate
            // per frame.
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 5.dp)
                        .then(if (live) Modifier.liquidGlassAnchor(anchor) else Modifier),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = direction * slotPx * selectionFraction.value
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Liquid: the lens stretches along its
                                    // flight and gives the height back, so it
                                    // holds its volume.
                                    val stretch = if (live) {
                                        LiquidGlass.lensStretch(selectedIndex - selectionFraction.value)
                                    } else {
                                        1f
                                    }
                                    scaleX = stretch
                                    scaleY = LiquidGlass.lensSquash(stretch)
                                }
                                .then(
                                    if (scene != null) {
                                        Modifier
                                            .shadow(
                                                elevation = 4.dp,
                                                shape = barShape,
                                                clip = true,
                                                ambientColor = colors.lensShadow,
                                                spotColor = colors.lensShadow,
                                            )
                                            .liquidGlassPane(
                                                scene = scene,
                                                shape = barShape,
                                                kind = LiquidGlassKind.LENS,
                                                tint = colors.lensBase,
                                                content = scene.page,
                                                anchor = anchor,
                                                shift = {
                                                    Offset(
                                                        direction * slotPx * selectionFraction.value,
                                                        0f,
                                                    )
                                                },
                                                light = false,
                                            )
                                            .drawBehind { drawRect(colors.lensTint) }
                                            .liquidRim(barShape, lensRimBrush)
                                    } else {
                                        Modifier
                                            .clip(barShape)
                                            .background(scheme.primaryContainer)
                                    },
                                ),
                        )
                    }
                }
            }

            Row {
                destinations.forEach { destination ->
                    NavBarSlot(
                        destination = destination,
                        selected = selectedRoute(destination),
                        onClick = { onSelect(destination) },
                        live = live,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One nav slot: icon + label, tinted by selection state, with a liquid press. */
@Composable
private fun NavBarSlot(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    live: Boolean,
    colors: LiquidGlassColors,
    modifier: Modifier = Modifier,
) {
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val density = LocalDensity.current
    // v1.6.0-beta.13 (user report: labels that do not fit the slot): the
    // label now sizes itself to the slot — stepwise down to 7sp — and
    // only follows the user's font scale up to 1.3x, like Material's own
    // navigation bar. Above that a Persian "رمزنگاری" at 2x is wider than
    // a sixth of the screen and collided with its neighbours.
    val labelDensity = remember(density) {
        Density(density.density, fontScale = density.fontScale.coerceAtMost(1.3f))
    }
    // v1.6.0-beta.11: the press is a liquid nudge — the slot sinks a hair
    // into the glass and a soft bloom spreads under its icon. Both are read
    // only in the draw phase, so a press never recomposes the slot.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "navPress",
    )
    // The active icon settles a hair larger (MediumBouncy, 1200: the one
    // soft overshoot is the deliberate life of v1.5.2, finishing on the
    // page's ~280ms beat per v1.5.7); the colour cross-fades on the same
    // clock. Scale and alpha are read only inside the graphicsLayer lambda.
    val iconScale = animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 1200f,
            visibilityThreshold = 0.01f,
        ),
        label = "navIconScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer {
                val scale = 1f - 0.06f * press.value
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (live && press.value > 0.01f) {
                    // the icon's centre: 8dp top padding + half of 22dp
                    val centre = Offset(size.width / 2f, 19.dp.toPx())
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colors.bloom.copy(alpha = colors.bloom.alpha * press.value),
                            1f to colors.bloom.copy(alpha = 0f),
                            center = centre,
                        ),
                        radius = 23.dp.toPx(),
                        center = centre,
                    )
                }
            }
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
                },
        )
        // v1.6.0-beta.13: the unselected label and icon keep their FULL
        // colour. At the old 0.72 alpha they failed 4.5:1 wherever dark
        // content passed under the pane; selection is now carried by the
        // colour role and the lens, not by fading text.
        CompositionLocalProvider(LocalDensity provides labelDensity) {
            Text(
                stringResource(destination.labelRes),
                color = fg,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 7.sp,
                    maxFontSize = 10.sp,
                    stepSize = 0.5.sp,
                ),
                style = LocalTextStyle.current.copy(lineHeight = 12.sp),
            )
        }
    }
}
