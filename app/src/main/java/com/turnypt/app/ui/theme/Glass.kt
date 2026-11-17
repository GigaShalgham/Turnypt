package com.turnypt.app.ui.theme

import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
)

val LightGlass = GlassColors(
    // Frost-white glass: opaque enough to read as a surface, translucent
    // enough for the backdrop's tone to shift beneath it.
    tint = Color(0xB8FFFFFF),
    tintHigh = Color(0xD8F0F2FA),
    border = Color(0x66FFFFFF),
    highlight = Color(0xB4FFFFFF),
    navTint = Color(0xC8FFFFFF),
    backdropTop = Color(0xFFE9EDF9),
    backdropBottom = Color(0xFFF7F8FC),
)

val DarkGlass = GlassColors(
    // Night glass: smoky, with a brighter rim so the edge reads on OLED.
    tint = Color(0x66262A3E),
    tintHigh = Color(0x992E3346),
    border = Color(0x40FFFFFF),
    highlight = Color(0x30FFFFFF),
    navTint = Color(0x90202438),
    backdropTop = Color(0xFF101320),
    backdropBottom = Color(0xFF171A2A),
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
 * The glass material as ONE modifier chain: clip → tint fill → specular
 * bevel → refraction border. `drawBehind` keeps tint and bevel UNDER the
 * content; the border stroke draws over the clipped edge.
 *
 * No per-frame blur: real backdrop blur is only available at the WINDOW
 * level (see [applyWindowBackgroundBlur]), and in-app surfaces get honest
 * translucency — the performance-conscious reading of the brief.
 */
fun Modifier.glassSurface(
    glass: GlassColors,
    shape: Shape,
    tint: Color = glass.tint,
): Modifier = this
    .clip(shape)
    .drawBehind {
        drawRect(brush = Brush.verticalGradient(listOf(tint, tint)))
        // Specular bevel: the top 45% (max 56dp) fades out — a curved
        // highlight, not a flat stripe.
        val bevelHeight = (size.height * 0.45f).coerceAtMost(56.dp.toPx())
        drawRect(
            brush = Brush.verticalGradient(
                0f to highlightBrushColor(glass, tint),
                1f to Color.Transparent,
                startY = 0f,
                endY = bevelHeight,
            ),
            size = androidx.compose.ui.geometry.Size(size.width, bevelHeight),
        )
    }
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                glass.border,
                glass.border.copy(alpha = glass.border.alpha * 0.35f),
            ),
        ),
        shape = shape,
    )

/** The bevel adapts to dark glass (a white band would fog OLED black). */
private fun highlightBrushColor(glass: GlassColors, tint: Color): Color =
    glass.highlight.copy(alpha = glass.highlight.alpha * (tint.alpha / 1f).coerceIn(0.4f, 1f))

/**
 * The quiet two-tone backdrop every screen sits on — the glass samples it
 * for depth. Applied once around the app content, never per-card.
 */
fun Modifier.glassBackdrop(glass: GlassColors): Modifier =
    background(
        Brush.verticalGradient(
            colors = listOf(glass.backdropTop, glass.backdropBottom),
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
