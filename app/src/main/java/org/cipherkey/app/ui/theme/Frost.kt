package org.cipherkey.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * v0.5.3 "Frosted Vault": one frosted-glass material for every glass
 * surface in the app (user request: replace the standard tonal glass
 * with a consistent frosted glass).
 *
 * Two layers of "frosted", honestly split by where the glass lives:
 *
 * 1. Panes inside the app sit on flat color backgrounds, so there is
 *    nothing behind them to defocus — their frost IS the material:
 *    a translucent tint veil, a brighter sheen band at the top (light
 *    catching the frost), and a hairline top edge. Same geometry and
 *    tone rules as the v0.5 tonal system, now rendered as glass.
 *
 * 2. The two surfaces that really float over other apps' pixels — the
 *    companion sheet and the floating assistant — get a REAL backdrop
 *    blur via WindowManager's blur-behind on API 31+ (applied in
 *    BaseCompanionActivity / FloatingAssistantService). Below S they
 *    keep the plain translucent dim: no fake frost.
 *
 * Status containers (success/error) keep their saturated fills — the
 * security-state channel must stay readable at a glance; frost is for
 * the neutral panes only.
 */
data class FrostedColors(
    /** Neutral glass pane (cards, tiles, sheets) tint, translucent. */
    val paneTint: Color,
    /** Quieter inset pane (result blocks, history rows) tint. */
    val insetTint: Color,
    /** Hairline frost edge along a pane's top. */
    val edge: Color,
)

val DarkFrosted = FrostedColors(
    // v0.5 role colors at ~70% alpha: the pane lets the app's own
    // background tones breathe through, reading as one glass system.
    paneTint = Color(0xB3141B25),
    insetTint = Color(0xB31D2735),
    edge = Color(0x2EFFFFFF),
)

val LightFrosted = FrostedColors(
    paneTint = Color(0xB3E7ECF5),
    insetTint = Color(0xB3D5DCEA),
    edge = Color(0x66FFFFFF),
)

val LocalFrostedColors = staticCompositionLocalOf { LightFrosted }

private val SHEEN_HEIGHT = 24.dp
private val SHEEN_BOOST = 0.06f
private val EDGE_HEIGHT = 1.dp

/**
 * The one modifier every frosted pane wears: a translucent tint veil,
 * a brighter sheen band at the top (frost catching light) and a
 * hairline top edge. Draws behind content — pair with a Surface whose
 * color is transparent.
 *
 * [tint] overrides the default pane tint (pass
 * [FrostedColors.insetTint] for inset panes). Never a status color:
 * state keeps its saturated containers.
 */
@Composable
fun Modifier.frostedPane(tint: Color? = null): Modifier {
    val frost = LocalFrostedColors.current
    val pane = tint ?: frost.paneTint
    return this.drawBehind {
        // The tint veil, uniform over the pane.
        drawRect(pane)
        // Sheen: the top band runs SHEEN_BOOST brighter, fading into
        // the pane tint — the light-on-frost cue.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = SHEEN_BOOST),
                    Color.White.copy(alpha = 0f),
                ),
                startY = 0f,
                endY = SHEEN_HEIGHT.toPx(),
            ),
        )
        // Hairline top edge.
        drawRect(
            color = frost.edge,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = androidx.compose.ui.geometry.Size(size.width, EDGE_HEIGHT.toPx()),
        )
    }
}
