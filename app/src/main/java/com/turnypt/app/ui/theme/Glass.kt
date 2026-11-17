package com.turnypt.app.ui.theme

import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * v1.4.0 "Liquid Glass" tokens (docs/DESIGN.md §Liquid Glass pass): the
 * ONE definition every glass surface is built from — screens never
 * hand-roll their own translucency (engineering rule: reusable tokens over
 * duplicated styling).
 *
 * The material model, translated to the Android tools Turnypt already has:
 *
 *  1. TINT — a translucent surface color over the backdrop (the glass
 *     itself). Alpha is deliberately moderate: the backdrop must stay
 *     readable, and translucency is an aid to depth, never a fog.
 *  2. BORDER — a hairline stroke, brighter at the top than the bottom:
 *     light rakes a glass edge unevenly (the "refraction" rim).
 *  3. HIGHLIGHT — the specular band: a faint gradient along the top
 *     inside edge, like a bevel catching a ceiling light.
 *  4. DEPTH — behind the glass, the backdrop is a quiet two-tone
 *     gradient, so every translucent surface samples something real.
 *
 * Adaptive: [LightGlass]/[DarkGlass] pairs provided by [TurnyptTheme] via
 * [LocalGlassColors], mirroring [LocalSemanticColors].
 *
 * Accessibility: every glass tint sits near the old container-role tones,
 * and text on glass keeps its Material-role on-color — contrast never
 * depends on what happens to be behind the glass.
 */
data class GlassColors(
    /** The glass tint itself (translucent; over the app backdrop). */
    val tint: Color,
    /** Tint for a raised glass layer (result insets, popovers, fields). */
    val tintHigh: Color,
    /** The refraction rim — hairline stroke, bright top / quiet bottom. */
    val border: Color,
    /** The specular bevel color (drawn as a fading gradient). */
    val highlight: Color,
    /** Tint for the floating navigation bar — the most prominent glass. */
    val navTint: Color,
    /** The quiet backdrop the glass samples (light: frost, dark: night). */
    val backdropTop: Color,
    val backdropBottom: Color,
    /** v1.5.0: the soft ambient shadow the glass casts (diffused, low). */
    val shadowColor: Color,
)

val LightGlass = GlassColors(
    // v1.5.0 Turnip: white-violet glass on the warm-white field — purple
    // ambient light without saturation; the rim is a violet hairline.
    // v1.5.2: navTint was lifted from near-opaque (0xD8) to the general
    // glass weight (0xB8) — the bar floats as glass over the backdrop,
    // not as a solid white-violet slab at the screen's foot.
    // v1.5.3: with the dark band report in mind, the light navTint keeps
    // EXACT parity with the general glass tint (same colour, same
    // alpha) — the bar is just another pane, never its own material.
    tint = Color(0xB8FFFFFF),
    tintHigh = Color(0xE8F0EAF9),
    border = Color(0x478256C7),
    highlight = Color(0x5CE8DDF9),
    navTint = Color(0xB8FFFFFF),
    // v1.5.5: the light backdrop is a UNIFORM warm-white field — the
    // same colour top to bottom. The old two-tone (lilac crown over
    // white foot) tinted the screen's lower region violet by design,
    // and every "extra band under the bar" report traced back to that
    // deliberately uneven field. One quiet field, one material.
    backdropTop = Color(0xFFFAF8FD),
    backdropBottom = Color(0xFFFAF8FD),
    shadowColor = Color(0x2E4A3B76),
)

val DarkGlass = GlassColors(
    // v1.5.0 Turnip: plum-smoke glass over the plum-black field; the rim
    // is violet (not white) so the edge reads as light, not chalk.
    // v1.5.3 (user report, third strike: "the extra purple background at
    // the bottom ruined the menu's transparency"): the dark navTint was
    // STILL a distinctly purple fill (0x66221D33 smoke-plum) laid over
    // the backdrop's own plum — two near-neighbour purples stacking into
    // a visibly darker band under the bar. A pane of glass over this
    // field must not ADD colour at all: the tint is now a low-alpha wash
    // of the backdrop's OWN bottom colour, raised by a whisper of
    // primary light — so the bar area blends with what is behind it and
    // the pane's presence comes from the violet rim + soft shadow only.
    // (Light navTint already matches its tint and was never reported.)
    tint = Color(0x66221D33),
    tintHigh = Color(0x992A2440),
    border = Color(0x47B18AFB),
    highlight = Color(0x33B18AFB),
    // v1.5.5: a transparent bar pane must sit on a colour it does not
    // shift — the old navTint was a wash OF the backdrop's plum foot,
    // so bar-over-plum stacked into the reported purple band. The tint
    // is now the scheme's OWN neutral background colour (12101A), the
    // exact field the cards sit on.
    navTint = Color(0x5912101A),
    // v1.5.5: the dark backdrop is a UNIFORM near-black field top to
    // bottom — the scheme's own background (12101A). The previous
    // two-tone (0E0C15 crown over a PLUM 171522 foot) plus the v1.5.4
    // "violet bloom" experiment BOTH spent the screen's lower region on
    // progressively purple-er colour — which is precisely what kept
    // reading as "an extra purple block between the menu and the texts".
    // A backdrop is the room, not a decoration: one quiet neutral field,
    // and the glass's presence comes from rim + shadow alone.
    backdropTop = Color(0xFF12101A),
    backdropBottom = Color(0xFF12101A),
    shadowColor = Color(0x5C000000),
)

/** Provided by [TurnyptTheme]; read as `LocalGlassColors.current`. */
val LocalGlassColors = androidx.compose.runtime.staticCompositionLocalOf { LightGlass }

/**
 * The corner system. Liquid Glass reads as ONE continuous radius family —
 * 12 → 16 → 20 → 28 dp plus the full pill — replacing the v0.5
 * 8/12/16/24/32 set. Components take their shape from here, never from
 * local literals.
 */
object GlassRadii {
    val chip = 12.dp
    val field = 16.dp
    val card = 20.dp
    val sheet = 28.dp
    val pill = 9999.dp
}

/**
 * The glass material as ONE modifier chain: clip → soft diffused shadow →
 * tint fill → specular bevel → refraction border. `drawBehind` keeps tint
 * and bevel UNDER the content; the border stroke draws over the clipped
 * edge.
 *
 * v1.5.0 Turnip lighting: depth comes from ONE soft, diffused ambient
 * shadow (large blur, low alpha — never stacked, never crisp) plus the
 * restrained violet rim; the specular bevel stays quiet. No per-frame
 * blur: real backdrop blur is only available at the WINDOW level (see
 * [applyWindowBackgroundBlur]), and in-app surfaces get honest
 * translucency — the performance-conscious reading of the brief.
 */
fun Modifier.glassSurface(
    glass: GlassColors,
    shape: Shape,
    tint: Color = glass.tint,
): Modifier {
    // v1.5.7 perf: the brushes are resolved ONCE when the modifier node
    // is built, not on every draw pass. The old drawBehind allocated a
    // fresh verticalGradient (brush object + shader on first paint)
    // TWICE per redraw — and any animated sibling (the sliding nav
    // selection pill) forces the whole glass bar to redraw every frame,
    // so those allocations ran at animation frequency. The tint "gradient"
    // was two identical stops ([tint, tint]) — a solid fill wearing a
    // gradient's cost — and is now a plain solid brush. The bevel
    // gradient depends only on (glass, tint), both captured here.
    // Visual output is byte-for-byte identical; only the allocation
    // traffic is gone.
    val tintBrush = SolidColor(tint)
    val bevelColor = highlightBrushColor(glass, tint)
    val bevelBrush = Brush.verticalGradient(
        0f to bevelColor,
        1f to bevelColor.copy(alpha = bevelColor.alpha * 0.3f),
    )
    return this
        .clip(shape)
        .shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            ambientColor = glass.shadowColor,
            spotColor = glass.shadowColor,
        )
        .drawBehind {
            // Specular bevel: v1.5.1 (user report: the bar's LOWER half read
            // as a solid purple slab — "glass on top, flat on the bottom"):
            // the bevel previously covered only the top 45%, splitting the
            // surface into two visibly different halves. One continuous glass
            // needs one continuous treatment — the specular gradient now
            // spans the FULL height of every glass surface (still fading
            // toward the bottom, still restrained alpha: a ceiling light
            // raking the whole pane, not a stripe on the upper half).
            drawRect(brush = tintBrush)
            drawRect(brush = bevelBrush)
        }
        .border(
            width = 1.dp,
            // The rim follows the same rule: a uniform hairline top to
            // bottom. The old 0.35 bottom fade let the glass edge vanish
            // midway, which read as the lower half being cut from different
            // material; one pane means one rim.
            borderBrush(glass),
            shape = shape,
        )
}

/** The refraction rim: uniform alpha across the full surface height. */
private fun borderBrush(glass: GlassColors): Brush =
    Brush.verticalGradient(listOf(glass.border, glass.border))

/** The bevel adapts to dark glass (a white band would fog OLED black). */
private fun highlightBrushColor(glass: GlassColors, tint: Color): Color =
    glass.highlight.copy(alpha = glass.highlight.alpha * (tint.alpha / 1f).coerceIn(0.4f, 1f))

/**
 * The quiet backdrop every screen sits on. Applied once around the app
 * content, never per-card.
 *
 * v1.5.5 (user report, fourth strike: "the extra purple block between
 * the menu and the page elements at the bottom is STILL there"): this
 * time the diagnosis went to the actual PIXELS under the bar, and the
 * finding is humbling — every previous "fix" ADDED colour to the screen's
 * lower region instead of removing it. v1.5.4's violet bloom painted a
 * literal purple gradient there; before it, the two-tone field itself
 * put a PLUM foot (171522) under a near-black crown; and the bar's own
 * tint was a wash OF that plum, stacking two purples. The backdrop is
 * now ONE uniform neutral field (the scheme's own background colour,
 * top to bottom) and the bar's tint samples that same neutral. There is
 * no second colour anywhere near the screen's foot, so no region can
 * read as "an extra block". The glass keeps its presence through the
 * violet rim and the soft shadow — light, not paint.
 */
fun Modifier.glassBackdrop(glass: GlassColors): Modifier =
    background(glass.backdropTop)

/**
 * REAL Liquid-Glass backdrop blur, where Android genuinely provides it:
 * the translucent companion sheets. `Window.setBackgroundBlurRadius`
 * (API 31+) blurs everything BEHIND the window — the calling app beneath
 * the sheet — using the platform compositor, not a per-frame shader.
 * Gated and failure-safe: below 31 the sheets keep their dimmed scrim,
 * which already reads as glass.
 *
 * This is the "use native platform capabilities where appropriate" clause
 * of the redesign brief — no library, no recreated blur pipeline.
 */
fun applyWindowBackgroundBlur(window: Window) {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        runCatching { window.setBackgroundBlurRadius(24) }
    }
}
