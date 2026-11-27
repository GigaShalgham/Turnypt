package com.turnypt.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * v1.6.0-beta.13: the Liquid Glass MATERIAL — one engine behind every glass
 * surface in the app, not only the main menu. v1.6.0-beta.14 rebuilt its lens
 * ([LIQUID_LENS_SKSL]) after the device reports on beta.13.
 *
 * A pane of this glass is a COPY of what lies behind it, frosted and bent:
 *
 *  1. SOURCES. Two [GraphicsLayer]s are recorded and reused as textures.
 *     The FIELD is the quiet static light the whole window sits on (recorded
 *     once per size, [Modifier.liquidGlassField]); the PAGE is the live
 *     content area (recorded on every draw, [Modifier.liquidGlassSource]).
 *     A surface never samples a layer that contains itself: the bar reads
 *     the page, the FAB reads the list — each from OUTSIDE the recorded
 *     tree.
 *  2. PANES. [Modifier.liquidGlassPane] gives any uniform-corner shape its
 *     own small layer: a copy of the sources shifted to line up with the
 *     real pixels, CLIPPED to the pane's own outline and grown by a margin
 *     that stays empty. The layer never holds a pixel of the page from
 *     beyond a rim: a blur mixes what lies around a pixel, and as long as
 *     the margin carried the real page the frost smeared whatever lies
 *     beyond a rim into the rim band (v1.6.0-beta.13 slid its sample there
 *     on purpose; beta.14's first cut removed the slide and the harness
 *     still found the strip under the bar inside the bar's bottom edge,
 *     ~32px deep). What the blur is missing at a rim — the half of its
 *     kernel that lies outside — is the pane's own mirror image: the lens
 *     shader completes every sample with its reflection across the nearest
 *     rim point, which is exact on a straight edge and near enough on a
 *     corner; the frost-only tier has no shader, and there the copy fades
 *     into the field's base colour over the last blur width. The layer then
 *     runs through a RenderEffect chain: blur -> (Android 13+) the lens
 *     shader, which carries the vibrancy and the bend. On 11–12 the bend
 *     does not exist and the vibrancy is a colour filter. Cards are no
 *     longer panes: what a card
 *     "sampled" was the smooth field it sits on, so blur and lens changed
 *     nothing a pixel could show, while twelve of them cost more frame time
 *     than the menu itself. A card is now its tint, the liquid light and the
 *     rim, drawn over the real field (see [Modifier.glassSurface]).
 *  3. LIGHT. Over the pane: one flat tint wash and full-width gradients
 *     (sheen, contact shade), then the rim. Nothing soft is ever a hard-edged
 *     shape: beta.11's BlurMaskFilter is not rendered by the hardware canvas
 *     at all, and beta.12's replacement (a hard inner rounded-rect plus a
 *     ring) reached the device as a "pill inside the pill". A rect that fills
 *     the clip and a gradient that spans it have no edge to show.
 *
 * Devices choose their tier through [LiquidGlass.tier]: the full material on
 * 13+, frost only on 11–12, the flat v1.5.10 pane below that, on low-RAM
 * hardware, or when the user switches the live glass off in Settings.
 */

/** How much of the material this device renders. */
enum class LiquidGlassTier {
    /** No capture at all: the flat pane (tint, rim, shadow). */
    STATIC,

    /** The backdrop is captured and frosted (blur + vibrancy). */
    FROSTED,

    /** Frost plus the refraction lens at the rim — the full material. */
    REFRACTIVE,
}

/**
 * What a pane is for. The numbers are dp (or unitless) and are turned into
 * pixels by [LiquidGlass.profile].
 *
 * The lens numbers obey one rule, checked by the unit tests for every kind:
 * the bevel is fold-free when [depthDp] is below two thirds of [bandDp]
 * ([LiquidGlass.FOLD_FREE_RATIO]).
 */
enum class LiquidGlassKind(
    /** Android's blur radius. The selection lens is clearer than the bar it sits on. */
    val blurDp: Float,
    /** Width of the bevel band, inward from the rim. */
    val bandDp: Float,
    /**
     * How far the rim pulls its sample INWARD, at the very edge: the depth
     * of the glass. A pixel on the rim shows the page this far inside the
     * rim — never anything from beyond it.
     */
    val depthDp: Float,
    /** Magnification about the pane's centre (1: the pane is flat in the middle). */
    val magnify: Float,
    /** Share of the scheme's rim light, rim shade and dispersion. */
    val lightShare: Float,
    val shadeShare: Float,
    val dispersionShare: Float,
    /** The inner specular ring on the lit side of the rim: (dark) -> 0..1. */
    val specular: (dark: Boolean) -> Float,
) {
    /**
     * The floating navigation bar: the hero of the material. A wide pill is
     * flat in the middle (its content must not slide about) and bends only
     * in the 10dp nearest its rim.
     */
    BAR(
        blurDp = 11f, bandDp = 10f, depthDp = 5f, magnify = 1f,
        lightShare = 1f, shadeShare = 1f, dispersionShare = 1f,
        specular = { dark -> if (dark) 0.55f else 0.40f },
    ),

    /** The travelling selection lens: a clearer, magnifying droplet of the same glass. */
    LENS(
        blurDp = 7f, bandDp = 6f, depthDp = 1f, magnify = 1.10f,
        lightShare = 1.4f, shadeShare = 1.2f, dispersionShare = 1.4f,
        specular = { dark -> if (dark) 0.80f else 0.65f },
    ),

    /** The floating action button: a small lens over the list. */
    FAB(
        blurDp = 8f, bandDp = 6f, depthDp = 3f, magnify = 1f,
        lightShare = 1.2f, shadeShare = 1.1f, dispersionShare = 1f,
        specular = { dark -> if (dark) 0.70f else 0.55f },
    ),
}

/** The pure numbers behind the glass — no Android types, fully unit-tested. */
object LiquidGlass {
    /** Vibrancy: the backdrop's saturation is lifted this much under the glass. */
    const val SATURATION = 1.3f

    /**
     * A bevel folds (mirrors a strip of the page, the way a badly cut lens
     * does) once its depth reaches two thirds of its band; the shipped kinds
     * stay below it and the unit test holds them there.
     */
    const val FOLD_FREE_RATIO = 2f / 3f

    /** How far the travelling selection lens stretches at full speed. */
    const val LENS_STRETCH = 0.26f

    /** How much of the stretch is given back as height (volume is kept). */
    const val LENS_SQUASH = 0.6f

    /** A hop shorter than this (in slots) does not stretch — no jitter on settle. */
    const val STRETCH_DEAD_ZONE_SLOTS = 0.04f

    /** A jump of this many slots (or more) stretches by the full [LENS_STRETCH]. */
    const val STRETCH_FULL_DISTANCE_SLOTS = 1.5f

    /**
     * How far the frost's fade reaches past a rim, in blur sigmas. A pane's
     * effect layer keeps this much room so the fade is never clamped.
     */
    const val BLUR_SIGMA_MARGIN = 3f

    /** A little air past the blur, in dp. */
    const val MARGIN_AIR_DP = 4f

    /** AOSP `Blur::convertRadiusToSigma` — what `createBlurEffect(radius)` blurs by. */
    private const val BLUR_SIGMA_SCALE = 0.57735f

    // The lens light by scheme, as plain floats so [profile] stays testable
    // without Compose. [LightLiquidGlass] / [DarkLiquidGlass] carry the same
    // numbers as colours.
    const val LIGHT_RIM_LIGHT = 0.30f
    const val LIGHT_RIM_SHADE = 0.26f
    const val LIGHT_DISPERSION = 0.05f
    const val DARK_RIM_LIGHT = 0.42f
    const val DARK_RIM_SHADE = 0.50f
    const val DARK_DISPERSION = 0.07f

    /**
     * The tier a device renders at. Live glass needs the RenderEffect
     * pipeline (API 31); the lens shader additionally needs RuntimeShader
     * (API 33). Low-RAM hardware never captures, whatever its version.
     */
    fun tier(sdkInt: Int, lowRam: Boolean, enabled: Boolean): LiquidGlassTier = when {
        !enabled || lowRam || sdkInt < 31 -> LiquidGlassTier.STATIC
        sdkInt < 33 -> LiquidGlassTier.FROSTED
        else -> LiquidGlassTier.REFRACTIVE
    }

    /** The sigma Android blurs with for a given blur radius, in pixels. */
    fun blurSigmaPx(radiusPx: Float): Float =
        if (radiusPx <= 0f) 0f else BLUR_SIGMA_SCALE * radiusPx + 0.5f

    /**
     * How much room a pane's effect layer leaves around itself on every
     * side, in pixels: the blur ([radiusPx] is Android's blur radius) spreads
     * the pane's own copy that far outward, and the lens reads that spread —
     * it completes a sample near a rim with the blurred value just OUTSIDE
     * the rim, so the layer has to hold the whole fade instead of clamping
     * it at the rim (a clamped layer would answer with the rim itself).
     * Plus a little air ([extraPx]). Nothing but that fade is ever there:
     * the lens samples inside the pane, and past a rim the layer holds
     * nothing of the page.
     */
    fun backdropMarginPx(radiusPx: Float, extraPx: Float): Float =
        BLUR_SIGMA_MARGIN * blurSigmaPx(radiusPx) + extraPx

    /** Every number of a pane, in the SURFACE's pixels. */
    fun profile(kind: LiquidGlassKind, density: Float, dark: Boolean): LiquidGlassProfile =
        LiquidGlassProfile(
            kind = kind,
            blurRadiusPx = kind.blurDp * density,
            lensBandPx = kind.bandDp * density,
            lensDepthPx = kind.depthDp * density,
            magnify = kind.magnify,
            dispersion = (if (dark) DARK_DISPERSION else LIGHT_DISPERSION) * kind.dispersionShare,
            rimLight = (if (dark) DARK_RIM_LIGHT else LIGHT_RIM_LIGHT) * kind.lightShare,
            rimShade = (if (dark) DARK_RIM_SHADE else LIGHT_RIM_SHADE) * kind.shadeShare,
            specular = kind.specular(dark),
            marginPx = backdropMarginPx(kind.blurDp * density, MARGIN_AIR_DP * density),
        )

    /**
     * Horizontal scale of the travelling selection lens. `distanceSlots` is
     * how far the lens still is from its slot (signed): it stretches along
     * its flight, most on long jumps, and rests at exactly 1. The short
     * dead zone keeps the spring's own overshoot from flickering the
     * scale back up as the lens settles.
     */
    fun lensStretch(distanceSlots: Float): Float {
        val distance = if (distanceSlots.isNaN()) 0f else abs(distanceSlots)
        val effort = ((distance - STRETCH_DEAD_ZONE_SLOTS) /
            (STRETCH_FULL_DISTANCE_SLOTS - STRETCH_DEAD_ZONE_SLOTS)).coerceIn(0f, 1f)
        return 1f + LENS_STRETCH * effort
    }

    /**
     * Vertical scale of the lens for a given horizontal [stretch]: the
     * height it loses as it stretches (never below the stretch it gains,
     * so a lens in flight holds its volume instead of growing).
     */
    fun lensSquash(stretch: Float): Float = 1f - LENS_SQUASH * (stretch - 1f)
}

/** A pane's numbers, resolved for one device and one light. */
@Stable
class LiquidGlassProfile internal constructor(
    val kind: LiquidGlassKind,
    val blurRadiusPx: Float,
    val lensBandPx: Float,
    val lensDepthPx: Float,
    val magnify: Float,
    val dispersion: Float,
    val rimLight: Float,
    val rimShade: Float,
    val specular: Float,
    /** Real content kept around the pane on every side (pixels). */
    val marginPx: Float,
) {
    /** Width of the effect layer for a surface [width]. */
    fun layerWidth(width: Float): Int = ceil(width + 2f * marginPx).toInt().coerceAtLeast(1)

    /** Height of the effect layer for a surface [height]. */
    fun layerHeight(height: Float): Int = ceil(height + 2f * marginPx).toInt().coerceAtLeast(1)
}

/**
 * Signed distance from a point of a pane to its rim: negative inside. The
 * pane is a rounded rect of half size [halfWidth] x [halfHeight] (a corner
 * radius of [corner]) centred on the origin — the same arithmetic as
 * [LIQUID_LENS_SKSL], in plain floats for the tests.
 */
internal fun paneDistance(
    x: Float,
    y: Float,
    halfWidth: Float,
    halfHeight: Float,
    corner: Float,
): Float {
    val ex = abs(x) - (halfWidth - corner)
    val ey = abs(y) - (halfHeight - corner)
    return hypot(max(ex, 0f), max(ey, 0f)) + min(max(ex, ey), 0f) - corner
}

/**
 * The lens mapping of [LIQUID_LENS_SKSL] in plain floats: the point of the
 * page that the pane point (x, y) shows, both measured from the pane's
 * centre. The unit tests run THIS over every pane of the app to hold the
 * shipped numbers to their two promises — confined (no sample beyond the
 * rim) and fold-free (a positive Jacobian everywhere); the render harness
 * holds the shader text itself to the same.
 */
internal fun lensSample(
    x: Float,
    y: Float,
    halfWidth: Float,
    halfHeight: Float,
    corner: Float,
    band: Float,
    depth: Float,
    magnify: Float,
): Offset {
    val coreX = halfWidth - corner
    val coreY = halfHeight - corner
    val dist = paneDistance(x, y, halfWidth, halfHeight, corner)
    // the outward normal of the nearest rim point (the straight middle has none)
    val ox = x - x.coerceIn(-coreX, coreX)
    val oy = y - y.coerceIn(-coreY, coreY)
    val length = max(hypot(ox, oy), 0.0001f)
    val across = (-dist / band).coerceIn(0f, 1f)
    val ease = across * across * (3f - 2f * across)
    val bevel = 1f - ease
    val m = 1f + (magnify - 1f) * ease
    val reach = bevel * depth
    return Offset((x - ox / length * reach) / m, (y - oy / length * reach) / m)
}

/**
 * Where [LIQUID_LENS_SKSL]'s `frosted` reads the missing half of the frost
 * for the pane point (x, y): its reflection across the nearest rim point —
 * as far OUTSIDE the rim as the point lies inside it. The same arithmetic
 * as the shader, in plain floats for the tests. A point of the straight
 * middle (no nearest rim point) is its own mirror.
 */
internal fun paneMirror(
    x: Float,
    y: Float,
    halfWidth: Float,
    halfHeight: Float,
    corner: Float,
): Offset {
    val coreX = halfWidth - corner
    val coreY = halfHeight - corner
    val ox = x - x.coerceIn(-coreX, coreX)
    val oy = y - y.coerceIn(-coreY, coreY)
    val length = max(hypot(ox, oy), 0.0001f)
    val reach = 2f * paneDistance(x, y, halfWidth, halfHeight, corner)
    return Offset(x - ox / length * reach, y - oy / length * reach)
}

/**
 * The refraction lens, in AGSL (the SkSL subset `RuntimeShader` accepts).
 *
 * `p` is a pixel of the pane's effect layer — the shape grown by the
 * backdrop margin — so the shape itself is the rectangle
 * [paneLeft, paneTop, paneRight, paneBottom] inside it. For every pixel
 * the shader computes the rounded-rect signed distance to the rim and the
 * outward normal there.
 *
 * v1.6.0-beta.14 (device report on beta.13: an upside-down copy of the
 * strip UNDER the menu appeared inside the menu's bottom edge). The old
 * band slid the sampling point OUTWARD along the normal, by up to 18dp, so
 * the rim drew what lies beyond the pane — at the bottom edge that is the
 * gap and the navigation strip below the bar, laid into the bar. The lens
 * now works the other way round, like a real convex glass: the bevel pulls
 * the sample INWARD, by at most `lensDepth`, so the rim shows the page from
 * a little inside the rim and nothing from beyond it can ever be drawn;
 * and the middle of the pane is magnified about its centre (1 = flat), so a
 * near-round lens reads as a lens. Both eases are smoothsteps of the
 * distance to the rim over one `lensBand`, which keeps the mapping free of
 * folds as long as the depth stays below two thirds of the band.
 *
 * The second half of the bug was the frost's own. The pane's layer used to
 * carry the real page in a margin around the pane so the blur would have
 * "enough to mix" at the rim — and it mixed exactly what must not be there:
 * the blur of a point 4px inside the bar's bottom edge draws up to ~32px
 * beyond the rim (measured on the harness: the blue probe line 6px under
 * the bar tinted 1158 pixels inside it). The layer now holds the pane's
 * backdrop clipped to the pane and nothing past the rim, and `frosted`
 * completes the blur where it is missing: for a straight edge, the blur of
 * a mirror-padded image is the blur of the clipped one plus the same blur
 * read at the point's reflection across the rim. Each sample therefore
 * costs two reads (three with dispersion).
 *
 * Three more touches make it glass even where the page is plain. Red and
 * blue are refracted a hair further than green (dispersion — the faint
 * prism an edge of thick glass shows); the band is shaded as a curved
 * surface would be (lit where its normal faces the lamp, darker where it
 * turns away) with a specular ring glinting on the lit side of the rim;
 * and the backdrop's saturation is lifted (the vibrancy) here, inside the
 * same pass, instead of in a colour filter of its own.
 */
internal val LIQUID_LENS_SKSL: String = """
// <lens-shader>
uniform shader content;
uniform float paneLeft;
uniform float paneTop;
uniform float paneRight;
uniform float paneBottom;
uniform float cornerRadius;
uniform float lensBand;
uniform float lensDepth;
uniform float magnify;
uniform float dispersion;
uniform float rimLight;
uniform float rimShade;
uniform float specular;
uniform float saturation;

// Signed distance from a point to the pane's rim: negative inside. The point
// is measured from the pane's centre; the pane is the rounded rect of half
// size (core + radius).
float paneDistance(float2 q, float2 core, float radius) {
    float2 e = abs(q) - core;
    return length(max(e, float2(0.0, 0.0))) + min(max(e.x, e.y), 0.0) - radius;
}

// The blurred layer holds the backdrop UNDER the pane and nothing past its
// rim, so near a rim the blur is missing the half of its kernel that lies
// outside. That half would see the pane's own mirror image, and the blur of
// a mirror-padded edge is the clipped blur plus the same blur read at the
// point's reflection across the rim — so that is what completes the sample.
// Both reads are premultiplied; their sum is divided by its own alpha.
float4 frosted(float2 s, float2 c, float2 core, float radius) {
    float2 q = s - c;
    float2 outward = q - clamp(q, -core, core);
    float2 n = outward / max(length(outward), 0.0001);
    float2 mirror = s - 2.0 * paneDistance(q, core, radius) * n;
    float4 sum = content.eval(s) + content.eval(mirror);
    return float4(sum.rgb / max(sum.a, 0.0001), 1.0);
}

half4 main(float2 p) {
    float2 c = float2((paneLeft + paneRight) * 0.5, (paneTop + paneBottom) * 0.5);
    float2 h = float2(paneRight - paneLeft, paneBottom - paneTop) * 0.5;
    float2 core = h - float2(cornerRadius, cornerRadius);
    float2 q = p - c;
    float dist = paneDistance(q, core, cornerRadius);
    // past the rim the node is clipped away; the copy's own fade goes on
    // there, so the clip's anti-aliased edge has the page's colour under it
    // rather than the black of an empty layer
    if (dist > 0.0) {
        float4 raw = content.eval(p);
        return half4(raw.rgb / max(raw.a, 0.0001), 1.0);
    }
    // the outward normal of the nearest rim point (the straight middle has none)
    float2 outward = q - clamp(q, -core, core);
    float2 n = outward / max(length(outward), 0.0001);
    // 0 on the rim, 1 one band-width inside it
    float across = clamp(-dist / lensBand, 0.0, 1.0);
    float ease = across * across * (3.0 - 2.0 * across);
    // the bevel: full on the rim, gone one band inside it
    float bevel = 1.0 - ease;
    // the lens magnifies about its centre; on the rim it lies flat
    float m = 1.0 + (magnify - 1.0) * ease;
    float bend = bevel * lensDepth;
    // the sample is pulled INWARD along the normal: it never leaves the pane
    float4 middle = frosted(c + (p - n * bend - c) / m, c, core, cornerRadius);
    float4 warm = frosted(c + (p - n * bend * (1.0 + dispersion) - c) / m, c, core, cornerRadius);
    float4 cool = frosted(c + (p - n * bend * (1.0 - dispersion) - c) / m, c, core, cornerRadius);
    float4 col = float4(warm.r, middle.g, cool.b, 1.0);
    // vibrancy: the backdrop's colour is lifted under the glass
    float luma = dot(col.rgb, float3(0.213, 0.715, 0.072));
    col.rgb = float3(luma) + (col.rgb - float3(luma)) * saturation;
    // a lamp above, and a little to the left
    float facing = dot(n, float2(-0.3, -0.954));
    float lit = max(facing, 0.0);
    float away = max(-facing, 0.0);
    float edge = bevel * bevel * bevel;
    col.rgb = col.rgb * (1.0 - bevel * rimShade * away)
        + float3(bevel * rimLight * lit + edge * specular * lit);
    return half4(col);
}
// </lens-shader>
""".trimIndent()

/**
 * The static light field a window sits on: the base colour, a crown of
 * light from above and two side tints. Everything fades out by [reach] of
 * the window height, so the lower part of the screen stays the plain base —
 * the user reported every colour ever placed near the screen's foot as "an
 * extra block under the menu" (v1.5.2 … v1.5.5); the foot stays clean.
 */
@Stable
class LiquidGlassField(
    val base: Color,
    val crown: Color,
    val left: Color,
    val right: Color,
    /** Share of the window height at which the crown has fully faded. */
    val reach: Float = 0.62f,
    /** Film grain (0..1): breaks gradient banding on OLED panels. */
    val grain: Float = 0.025f,
)

val LightLiquidField = LiquidGlassField(
    base = Color(0xFFFAF8FD),
    crown = Color(0x26CDBCFF),
    left = Color(0x1FB9E4FF),
    right = Color(0x22FFD2B8),
    grain = 0.02f,
)

val DarkLiquidField = LiquidGlassField(
    base = Color(0xFF12101A),
    crown = Color(0x5C3A2F80),
    left = Color(0x400E4A55),
    right = Color(0x47501C5A),
    grain = 0.03f,
)

/**
 * The light the material is made of, resolved per colour scheme. Everything
 * here is drawn OVER the pane; the text and icon colours stay the Material
 * roles they always were.
 *
 * v1.6.0-beta.14 (device report: the glass was white and lifeless). The
 * washes are what a pane lays between the user and the page, so they are
 * kept LOW and the page carries the colour: beta.13's bar drew 74% white
 * over its own frosted copy (measured: every pixel of the bar within two
 * levels of white), and the selection lens another 50% plus a tint — a
 * pale shadow of a lens.
 */
@Stable
class LiquidGlassColors(
    /** The bar's glass wash over the frosted backdrop. */
    val paneTint: Color,
    /** Specular wash: a ceiling light raking the pane from the top. */
    val sheen: Color,
    /** Contact shade along the bottom inside edge — the pane's thickness. */
    val innerShade: Color,
    /** The 1dp rim: bright where the light lands (top) … */
    val rimTop: Color,
    /** … and quiet where it leaves (bottom). */
    val rimBottom: Color,
    /** The selection lens: almost no wash at all — it is clear glass. */
    val lensBase: Color,
    /** Its hairline rim: a glint along the upper edge … */
    val lensRimTop: Color,
    /** … fading round to a quiet lower edge. */
    val lensRimBottom: Color,
    /** The floating action button's wash: it carries a label, so it milks the list a little. */
    val fabTint: Color,
    /** The soft brightening that spreads under a slot while it is pressed. */
    val bloom: Color,
    /** The contact shadow the selection lens casts on the bar's pane. */
    val lensShadow: Color,
    /** Lens shading (see [LIQUID_LENS_SKSL]): light added where the rim faces the lamp. */
    val rimLight: Float,
    /** … and darkening where it turns away from it. */
    val rimShade: Float,
    /** Dispersion at the rim: how much further red (and nearer blue) travel, as a share of the bend. */
    val dispersion: Float,
    /** The window's light field. */
    val field: LiquidGlassField,
    /** True for the dark scheme. */
    val dark: Boolean,
)

val LightLiquidGlass = LiquidGlassColors(
    paneTint = Color(0x3DFFFFFF),
    sheen = Color(0x14FFFFFF),
    innerShade = Color(0x1A001B3D),
    rimTop = Color(0xF2FFFFFF),
    rimBottom = Color(0x66708499),
    lensBase = Color(0x0DFFFFFF),
    lensRimTop = Color(0xE6FFFFFF),
    lensRimBottom = Color(0x4DFFFFFF),
    fabTint = Color(0x73FFFFFF),
    bloom = Color(0x2EFFFFFF),
    lensShadow = Color(0x3D4A3B76),
    // beta.13 had NO rim light in this scheme ("near-white glass cannot be
    // lit whiter") — true of a pane that was 74% white, and the reason its
    // edge refraction was invisible. Over a pane that lets the page
    // through, a lit bevel and a shaded one both show; the shade carries
    // the edge wherever the page is white.
    rimLight = LiquidGlass.LIGHT_RIM_LIGHT,
    rimShade = LiquidGlass.LIGHT_RIM_SHADE,
    dispersion = LiquidGlass.LIGHT_DISPERSION,
    field = LightLiquidField,
    dark = false,
)

val DarkLiquidGlass = LiquidGlassColors(
    paneTint = Color(0x8012101A),
    sheen = Color(0x14FFFFFF),
    innerShade = Color(0x73000000),
    rimTop = Color(0xD9B18AFB),
    rimBottom = Color(0x40B18AFB),
    lensBase = Color(0x1AFFFFFF),
    lensRimTop = Color(0xF2D6C6FF),
    lensRimBottom = Color(0x59FFFFFF),
    fabTint = Color(0x661A1626),
    bloom = Color(0x26FFFFFF),
    lensShadow = Color(0xB3000000),
    // On OLED black the rim GLOWS: additive light along the upper edge is
    // what makes an empty page read as a curved lens.
    rimLight = LiquidGlass.DARK_RIM_LIGHT,
    rimShade = LiquidGlass.DARK_RIM_SHADE,
    dispersion = LiquidGlass.DARK_DISPERSION,
    field = DarkLiquidField,
    dark = true,
)

/** The material's light, picked from the active scheme (no second theme flag). */
@Composable
fun liquidGlassColors(): LiquidGlassColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) DarkLiquidGlass else LightLiquidGlass
}

/** A recorded backdrop a pane can sample. */
@Stable
class LiquidGlassSource internal constructor(
    /** The recording. Written by the source modifier, read by panes. */
    val layer: GraphicsLayer,
) {
    /** Top-left of the recorded content in root coordinates. */
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

/**
 * Everything the panes of one window share: the tier, the light, and the two
 * sources. One per window content — never per surface.
 */
@Stable
class LiquidGlassScene internal constructor(
    val tier: LiquidGlassTier,
    val colors: LiquidGlassColors,
    /** The static light field. */
    val field: LiquidGlassSource,
    /** The live content area (what the bar refracts). */
    val page: LiquidGlassSource,
    private val view: View?,
) {
    /** True when this scene belongs to the window `v` is drawn in. */
    fun owns(v: View?): Boolean = view == null || view === v
}

/** The scene of the current window; null where the glass is flat. */
val LocalLiquidGlassScene = staticCompositionLocalOf<LiquidGlassScene?> { null }

/**
 * The material's shared state, or null on the static tier (nothing is
 * captured there and every surface keeps the flat look). Recreated only
 * when the tier or the light can change.
 */
@Composable
fun rememberLiquidGlassScene(enabled: Boolean): LiquidGlassScene? {
    val context = LocalContext.current.applicationContext
    val lowRam = remember(context) { isLowRamDevice(context) }
    val tier = LiquidGlass.tier(Build.VERSION.SDK_INT, lowRam, enabled)
    val colors = liquidGlassColors()
    val view = LocalView.current
    val field = rememberGraphicsLayer()
    val page = rememberGraphicsLayer()
    return remember(tier, colors, view, field, page) {
        if (tier == LiquidGlassTier.STATIC) {
            null
        } else {
            LiquidGlassScene(tier, colors, LiquidGlassSource(field), LiquidGlassSource(page), view)
        }
    }
}

/**
 * The scene to sample from HERE. Composition locals flow into dialogs and
 * popups, but those are other windows with their own root: their coordinates
 * would address the wrong part of the field. They keep the flat material.
 */
@Composable
fun currentLiquidGlassScene(): LiquidGlassScene? {
    val scene = LocalLiquidGlassScene.current ?: return null
    return if (scene.owns(LocalView.current)) scene else null
}

private fun isLowRamDevice(context: Context): Boolean = runCatching {
    context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
}.getOrDefault(false)

/**
 * True when this device renders the live material at all (API 31+, not
 * low-RAM), so Settings can explain a switch that would do nothing
 * instead of offering one that lies.
 */
@Composable
fun rememberLiquidGlassSupported(): Boolean {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        LiquidGlass.tier(Build.VERSION.SDK_INT, isLowRamDevice(context), enabled = true) !=
            LiquidGlassTier.STATIC
    }
}

/**
 * The window's light field. The field is recorded once per size into the
 * scene's source layer and that layer is what is drawn — so the panes and
 * the real pixels are the same drawing, pixel for pixel.
 */
@Composable
fun Modifier.liquidGlassField(scene: LiquidGlassScene): Modifier {
    val field = rememberLiquidGlassField(scene)
    // A new painter (a scheme switch) starts with an empty size, so the
    // layer is re-recorded for the new colours.
    val recorded = remember(field) { IntSizeHolder() }
    return this
        .onGloballyPositioned { coordinates -> scene.field.origin = coordinates.positionInRoot() }
        .drawBehind {
            val px = IntSize(size.width.toInt(), size.height.toInt())
            if (px.width > 0 && px.height > 0 && recorded.size != px) {
                recorded.size = px
                // DrawScope IS the Density (the scope's own `density` is a
                // Float), so it records itself into the layer.
                scene.field.layer.record(this, layoutDirection, px) { drawLiquidField(field) }
            }
            drawLayer(scene.field.layer)
        }
}

private class IntSizeHolder {
    var size = IntSize.Zero
}

/** The field's drawing, resolved once: the colours and the grain tile. */
@Stable
private class LiquidGlassFieldPainter internal constructor(
    val colours: LiquidGlassField,
    val grain: ShaderBrush?,
)

@Composable
private fun rememberLiquidGlassField(scene: LiquidGlassScene): LiquidGlassFieldPainter =
    remember(scene.colors.field) {
        val colours = scene.colors.field
        LiquidGlassFieldPainter(
            colours = colours,
            grain = if (colours.grain > 0f) GrainBrush(makeGrainTile()) else null,
        )
    }

private fun DrawScope.drawLiquidField(field: LiquidGlassFieldPainter) {
    val c = field.colours
    val w = size.width
    val h = size.height
    val reach = h * c.reach
    drawRect(c.base)
    // the crown: light from a lamp above the phone
    drawRect(
        Brush.radialGradient(
            0f to c.crown,
            0.45f to c.crown.copy(alpha = c.crown.alpha * 0.5f),
            1f to c.crown.copy(alpha = 0f),
            center = Offset(w * 0.5f, 0f),
            radius = reach,
        ),
    )
    drawRect(
        Brush.radialGradient(
            0f to c.left,
            1f to c.left.copy(alpha = 0f),
            center = Offset(0f, h * 0.10f),
            radius = reach * 0.85f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            0f to c.right,
            1f to c.right.copy(alpha = 0f),
            center = Offset(w, h * 0.14f),
            radius = reach * 0.9f,
        ),
    )
    field.grain?.let { drawRect(it, alpha = c.grain) }
}

/** A tile of grey noise, repeated across the field. */
private class GrainBrush(private val tile: ImageBitmap) : ShaderBrush() {
    // The platform shader over the bitmap the tile was made from — the
    // compose "ImageBitmapShader" is ui-graphics-desktop only.
    private var shader: Shader? = null

    override fun createShader(size: Size): Shader =
        shader ?: android.graphics.BitmapShader(
            tile.asAndroidBitmap(),
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT,
        ).also { shader = it }
}

private fun makeGrainTile(): ImageBitmap {
    val n = 96
    val px = IntArray(n * n)
    var seed = 0x9E3779B9.toInt()
    for (i in px.indices) {
        seed = seed * 1664525 + 1013904223
        val v = seed ushr 24
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(
        px, n, n, android.graphics.Bitmap.Config.ARGB_8888,
    ).asImageBitmap()
}

/**
 * Marks the tree whose drawing becomes a source. Applied ONCE, around the
 * content area only — the bar stays a sibling, outside the recording, so a
 * pane can never feed its own output back in.
 *
 * Each draw records the content into the shared layer through the
 * DrawScope `record` extension (which retargets the canvas into the
 * layer) and then draws that layer: the page is rasterised once, exactly
 * as before, and the recording is free for the panes to reuse.
 */
fun Modifier.liquidGlassSource(source: LiquidGlassSource): Modifier =
    this
        .onGloballyPositioned { coordinates ->
            source.origin = coordinates.positionInRoot()
        }
        .drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            drawLayer(source.layer)
        }

/**
 * Where a surface sits, in root coordinates. Normally a pane tracks itself;
 * a pane that is moved by a `graphicsLayer` transform (the travelling
 * selection lens) needs an anchor placed OUTSIDE that transform, because
 * the layout position of the pane itself never changes.
 */
@Stable
class LiquidGlassAnchor {
    internal var bounds by mutableStateOf(Rect.Zero)
}

@Composable
fun rememberLiquidGlassAnchor(): LiquidGlassAnchor = remember { LiquidGlassAnchor() }

fun Modifier.liquidGlassAnchor(anchor: LiquidGlassAnchor): Modifier =
    onGloballyPositioned { coordinates ->
        anchor.bounds = Rect(
            coordinates.positionInRoot(),
            Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
        )
    }

/** The effect of one pane, rebuilt only when what it depends on changes. */
private class PaneEffect {
    var size = Size.Zero
    var corner = 0f
    var profile: LiquidGlassProfile? = null
    var tier: LiquidGlassTier? = null
    var shape: Shape? = null
    var direction: LayoutDirection? = null
    var effect: RenderEffect? = null

    /**
     * True when the effect ends in the lens shader, the only thing that
     * completes the frost at a rim; without it the copy needs the base
     * colour under it (see [Modifier.liquidGlassPane]).
     */
    var lens = false

    /** The pane's outline, where it sits in its own effect layer (moved in by the margin). */
    var outline: Path? = null

    /** The effect last handed to the pane's layer, so a steady frame never re-applies it. */
    private var attached: RenderEffect? = null

    fun get(
        tier: LiquidGlassTier,
        profile: LiquidGlassProfile,
        shape: Shape,
        width: Float,
        height: Float,
        density: Density,
        layoutDirection: LayoutDirection,
    ): RenderEffect? {
        if (width <= 0f || height <= 0f || Build.VERSION.SDK_INT < 31) return null
        val size = Size(width, height)
        val r = cornerRadiusPx(shape, min(width, height), density)
        if (size != this.size || r != corner || profile != this.profile || tier != this.tier ||
            shape !== this.shape || layoutDirection != direction || effect == null
        ) {
            val frost = Api31.paneEffect(tier, profile, size, r)
            effect = frost.effect
            lens = frost.lens
            this.size = size
            corner = r
            this.profile = profile
            this.tier = tier
            this.shape = shape
            direction = layoutDirection
            outline = paneOutline(shape, size, density, layoutDirection).apply {
                translate(Offset(profile.marginPx, profile.marginPx))
            }
        }
        return effect
    }

    /** Gives the pane's layer its effect — once per change, not once per frame. */
    fun attach(pane: GraphicsLayer) {
        if (attached !== effect) {
            pane.renderEffect = effect
            attached = effect
        }
    }
}

/** The shape as a path over its own size (no offset: the pane's top-left is the origin). */
private fun paneOutline(shape: Shape, size: Size, density: Density, layoutDirection: LayoutDirection): Path =
    when (val outline = shape.createOutline(size, layoutDirection, density)) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }

/**
 * One pane of glass behind the content of the node it is applied to:
 *
 *  1. the node is clipped to [shape];
 *  2. its own small [GraphicsLayer] gets a copy of the [scene]'s field —
 *     plus the live [content] source when there is one — shifted so the
 *     copy lines up with the real pixels, and CLIPPED to the pane's own
 *     outline: past a rim the layer holds nothing of the page. It is still
 *     grown by the profile's margin, but only as room for the blur's fade
 *     ([LiquidGlass.backdropMarginPx]); the lens shader completes that fade
 *     with the pane's mirror image ([LIQUID_LENS_SKSL]). Where there is no
 *     lens — the frost-only tier, or a driver that rejected the shader —
 *     the field's base colour is laid under the copy instead, and the copy
 *     fades into it over the last blur width;
 *  3. that layer carries the frost chain (blur, and the lens at the
 *     refractive tier) as its render effect, so the TEXT and ICONS drawn
 *     afterwards are never blurred — only the backdrop copy is;
 *  4. over it the flat [tint] and, with [light], the sheen and the contact
 *     shade — full-width gradients, never a hard-edged shape.
 *
 * Until the node has been measured the pane paints only the tint (it never
 * flashes a sharp copy of the page). [shift] moves the sampling window for
 * panes that travel by transform; [anchor] supplies the position of such a
 * pane (see [LiquidGlassAnchor]).
 */
@Composable
fun Modifier.liquidGlassPane(
    scene: LiquidGlassScene,
    shape: Shape,
    kind: LiquidGlassKind,
    tint: Color,
    content: LiquidGlassSource? = null,
    anchor: LiquidGlassAnchor? = null,
    shift: () -> Offset = { Offset.Zero },
    light: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val pane = rememberGraphicsLayer()
    val own = rememberLiquidGlassAnchor()
    val tracker = anchor ?: own
    val profile = remember(kind, density, scene.colors) {
        LiquidGlass.profile(kind, density.density, scene.colors.dark)
    }
    val cache = remember { PaneEffect() }
    return this
        .then(if (anchor == null) Modifier.liquidGlassAnchor(own) else Modifier)
        .clip(shape)
        .drawBehind {
            val b = tracker.bounds
            val w = b.width
            val h = b.height
            if (w <= 0f || h <= 0f) return@drawBehind
            val effect = cache.get(scene.tier, profile, shape, w, h, density, layoutDirection)
            if (effect == null) {
                drawRect(tint)
                return@drawBehind
            }
            val m = profile.marginPx
            // which root point sits at the pane layer's (0, 0)
            val origin = b.topLeft + shift() - Offset(m, m)
            pane.record(
                this,
                layoutDirection,
                IntSize(profile.layerWidth(w), profile.layerHeight(h)),
            ) {
                // no lens to complete the frost: the copy fades into the base
                if (!cache.lens) drawRect(scene.colors.field.base)
                // the pane's backdrop, and nothing from beyond its rim
                clipPath(checkNotNull(cache.outline)) {
                    translate(scene.field.origin.x - origin.x, scene.field.origin.y - origin.y) {
                        drawLayer(scene.field.layer)
                    }
                    if (content != null) {
                        translate(content.origin.x - origin.x, content.origin.y - origin.y) {
                            drawLayer(content.layer)
                        }
                    }
                }
            }
            cache.attach(pane)
            // the layer carries the margin on every side: drop it
            translate(-m, -m) { drawLayer(pane) }
            drawRect(tint)
            if (light) drawGlassLight(scene.colors)
        }
}

/** Sheen from the top, contact shade at the bottom — gradients that span the whole clip. */
internal fun DrawScope.drawGlassLight(colors: LiquidGlassColors) {
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to colors.sheen,
            1f to colors.sheen.copy(alpha = 0f),
            startY = 0f,
            endY = h * 0.55f,
        ),
    )
    drawRect(
        Brush.verticalGradient(
            0f to colors.innerShade.copy(alpha = 0f),
            1f to colors.innerShade,
            startY = h * 0.74f,
            endY = h,
        ),
    )
}

/**
 * The corner radius of a uniform-corner [shape] on a surface whose shortest
 * side is [shortest]. Corner sizes mix units — dp, percent (a circle is 50
 * percent) — so each is resolved through its own [CornerSize.toPx] with the
 * REAL density and the full shape size: at 3x a 16dp corner read with
 * density 1 would bend the wrong band.
 */
internal fun cornerRadiusPx(shape: Shape, shortest: Float, density: Density): Float {
    return when (shape) {
        is CornerBasedShape -> {
            val shapeSize = Size(shortest, shortest)
            val r = listOf(
                shape.topStart,
                shape.topEnd,
                shape.bottomStart,
                shape.bottomEnd,
            ).minOf { it.toPx(shapeSize, density) }
            r.coerceIn(0f, shortest / 2f)
        }
        else -> 0f
    }
}

/** What a pane's frost is made of: the chain, and whether the lens is its last link. */
private class PaneFrost(val effect: RenderEffect, val lens: Boolean)

@RequiresApi(31)
private object Api31 {
    /**
     * One chain: blur, then (Android 13+) the lens — which carries the
     * vibrancy in its own pass — or the vibrancy alone below 13 and wherever
     * a driver rejects the shader. Each step wraps the ones before it.
     */
    fun paneEffect(
        tier: LiquidGlassTier,
        profile: LiquidGlassProfile,
        surfaceSize: Size,
        cornerPx: Float,
    ): PaneFrost {
        val radius = profile.blurRadiusPx
        val blur = android.graphics.RenderEffect.createBlurEffect(
            radius,
            radius,
            android.graphics.Shader.TileMode.CLAMP,
        )
        if (tier == LiquidGlassTier.REFRACTIVE && Build.VERSION.SDK_INT >= 33) {
            // A driver that rejects the shader must not take the frost
            // down with it: the pane keeps blurring without the bend.
            val lens = runCatching { Api33.lensEffect(profile, surfaceSize, cornerPx) }.getOrNull()
            if (lens != null) {
                return PaneFrost(
                    android.graphics.RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect(),
                    lens = true,
                )
            }
        }
        val vibrancy = android.graphics.RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(LiquidGlass.SATURATION) },
            ),
        )
        return PaneFrost(
            android.graphics.RenderEffect.createChainEffect(vibrancy, blur).asComposeRenderEffect(),
            lens = false,
        )
    }
}

@RequiresApi(33)
private object Api33 {
    /** The refraction lens over the already-blurred pane (see [LIQUID_LENS_SKSL]). */
    fun lensEffect(
        profile: LiquidGlassProfile,
        surfaceSize: Size,
        cornerPx: Float,
    ) = android.graphics.RenderEffect.createRuntimeShaderEffect(
        android.graphics.RuntimeShader(LIQUID_LENS_SKSL).apply {
            val m = profile.marginPx
            setFloatUniform("paneLeft", m)
            setFloatUniform("paneTop", m)
            setFloatUniform("paneRight", surfaceSize.width + m)
            setFloatUniform("paneBottom", surfaceSize.height + m)
            setFloatUniform("cornerRadius", cornerPx)
            setFloatUniform("lensBand", profile.lensBandPx)
            setFloatUniform("lensDepth", profile.lensDepthPx)
            setFloatUniform("magnify", profile.magnify)
            setFloatUniform("dispersion", profile.dispersion)
            setFloatUniform("rimLight", profile.rimLight)
            setFloatUniform("rimShade", profile.rimShade)
            setFloatUniform("specular", profile.specular)
            setFloatUniform("saturation", LiquidGlass.SATURATION)
        },
        "content",
    )
}

/**
 * The rim, drawn last so it sits over everything the pane contains: one
 * hairline just inside the clip, for any corner shape.
 */
fun Modifier.liquidRim(shape: Shape, brush: Brush, width: Dp = 1.dp): Modifier =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        val inset = w / 2f
        val inner = Size(
            (size.width - 2f * inset).coerceAtLeast(0.5f),
            (size.height - 2f * inset).coerceAtLeast(0.5f),
        )
        // the shape re-created at the INSET size: a CornerSize stays
        // proportional, so the rim follows the pane's own corners
        val innerShape = if (shape is CornerBasedShape) {
            val shapeSize = Size(size.width, size.height)
            fun corner(cs: CornerSize) = CornerSize(
                (cs.toPx(shapeSize, this) - inset).coerceAtLeast(0f),
            )
            RoundedCornerShape(
                topStart = corner(shape.topStart),
                bottomEnd = corner(shape.bottomEnd),
                topEnd = corner(shape.topEnd),
                bottomStart = corner(shape.bottomStart),
            )
        } else {
            shape
        }
        // the hairline is centred on the path: translate by half its width
        // so the stroke falls fully INSIDE the inset outline
        translate(inset, inset) {
            when (val outline = innerShape.createOutline(inner, layoutDirection, this)) {
                is Outline.Rounded -> drawRoundRect(
                    brush = brush,
                    topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
                    size = Size(outline.roundRect.width, outline.roundRect.height),
                    cornerRadius = CornerRadius(
                        outline.roundRect.topLeftCornerRadius.x,
                        outline.roundRect.topLeftCornerRadius.y,
                    ),
                    style = Stroke(w),
                )
                is Outline.Generic -> drawPath(outline.path, brush = brush, style = Stroke(w))
                is Outline.Rectangle -> drawRect(
                    brush = brush,
                    topLeft = Offset.Zero,
                    size = inner,
                    style = Stroke(w),
                )
            }
        }
    }
