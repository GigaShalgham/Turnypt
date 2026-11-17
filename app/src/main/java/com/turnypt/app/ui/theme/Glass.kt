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
    /**
     * v1.5.4: the violet ambient bloom rising from the foot of the
     * backdrop — the lit atmosphere under the floating bar, replacing the
     * flat black/white field that read as an extra sheet.
     */
    val backdropBloom: Color,
    /** v1.5.4: the bloom's warmest point, right at the screen's foot. */
    val backdropBloomStrong: Color,
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
    backdropTop = Color(0xFFEDE7F5),
    backdropBottom = Color(0xFFFAF8FD),
    // v1.5.4 light bloom: the white field's foot warms with the same
    // lilac the glass rims carry — the bar's region becomes lit
    // atmosphere, not a raw white sheet.
    backdropBloom = Color(0xFFF3EEF9),
    backdropBloomStrong = Color(0xFFEFE8F6),
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
    navTint = Color(0x59171522),
    backdropTop = Color(0xFF0E0C15),
    backdropBottom = Color(0xFF171522),
    // v1.5.4 dark bloom: the plum-black field's foot lifts toward the
    // primary violet — the region the bar floats in reads as ambient
    // LIGHT, not a raw black page. The stops stay deep (221D33/2A2440
    // territory) so OLED contrast is kept and the bloom is felt as
    // depth, never seen as a stripe.
    backdropBloom = Color(0xFF1C1828),
    backdropBloomStrong = Color(0xFF221D33),
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
): Modifier = this
    .clip(shape)
    .shadow(
        elevation = 6.dp,
        shape = shape,
        clip = false,
        ambientColor = glass.shadowColor,
        spotColor = glass.shadowColor,
    )
    .drawBehind {
        val bevelTop = highlightBrushColor(glass, tint)
        drawRect(brush = Brush.verticalGradient(listOf(tint, tint)))
        // Specular bevel: v1.5.1 (user report: the bar's LOWER half read
        // as a solid purple slab — "glass on top, flat on the bottom"):
        // the bevel previously covered only the top 45%, splitting the
        // surface into two visibly different halves. One continuous glass
        // needs one continuous treatment — the specular gradient now
        // spans the FULL height of every glass surface (still fading
        // toward the bottom, still restrained alpha: a ceiling light
        // raking the whole pane, not a stripe on the upper half).
        drawRect(
            brush = Brush.verticalGradient(
                0f to bevelTop,
                1f to bevelTop.copy(alpha = bevelTop.alpha * 0.3f),
            ),
        )
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

/** The refraction rim: uniform alpha across the full surface height. */
private fun borderBrush(glass: GlassColors): Brush =
    Brush.verticalGradient(listOf(glass.border, glass.border))

/** The bevel adapts to dark glass (a white band would fog OLED black). */
private fun highlightBrushColor(glass: GlassColors, tint: Color): Color =
    glass.highlight.copy(alpha = glass.highlight.alpha * (tint.alpha / 1f).coerceIn(0.4f, 1f))

/**
 * The quiet two-tone backdrop every screen sits on — the glass samples it
 * for depth. Applied once around the app content, never per-card.
 *
 * v1.5.4 (user report: "the black (or white, theme-dependent) page between
 * the glass menu and the texts is STILL there, though smaller"): the old
 * two-stop gradient spent the whole LOWER half of the screen on one flat
 * colour — a raw plum-black page under the dark theme, a raw white page
 * under the light one — and that flat field is what read as "an extra
 * black/white sheet" sitting between the floating bar and the content.
 * A backdrop is AMBIENT LIGHT, not a painted wall: a violet bloom now
 * rises from the foot of the field (where the bar floats) and an even
 * quieter one glows at the crown, so the region under the bar belongs to
 * the same lit atmosphere the cards live in instead of forming its own
 * flat band. The stops are tuned so the bloom is FELT as depth, never
 * seen as a stripe.
 */
fun Modifier.glassBackdrop(glass: GlassColors): Modifier =
    background(
        Brush.verticalGradient(
            0.00f to glass.backdropTop,
            0.62f to glass.backdropBottom,
            0.86f to glass.backdropBloom,
            1.00f to glass.backdropBloomStrong,
        )
    )

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
