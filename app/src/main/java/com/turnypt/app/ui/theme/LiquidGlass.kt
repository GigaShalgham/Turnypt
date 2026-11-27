package com.turnypt.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * v1.6.0-beta.13: the Liquid Glass MATERIAL — one engine behind every glass
 * surface in the app, not only the main menu.
 *
 * A pane of this glass is a COPY of what lies behind it, frosted and bent:
 *
 *  1. SOURCES. Two [GraphicsLayer]s are recorded and reused as textures.
 *     The FIELD is the quiet static light the whole window sits on (recorded
 *     once per size, [Modifier.liquidGlassField]); the PAGE is the live
 *     content area (recorded on every draw, [Modifier.liquidGlassSource]).
 *     A surface never samples a layer that contains itself: cards and wells
 *     read the field, the bar reads the page, the FAB reads the list — each
 *     from OUTSIDE the recorded tree.
 *  2. PANES. [Modifier.liquidGlassPane] gives any uniform-corner shape its
 *     own small layer: a copy of the sources shifted to line up with the
 *     real pixels, grown by a margin of real content so the blur never
 *     samples its own clamped edge, run through a RenderEffect chain —
 *     blur -> vibrancy -> (Android 13+) the refraction lens
 *     ([LIQUID_LENS_SKSL]) — and clipped to the shape. Card panes render at
 *     half resolution where the density allows it (a quarter of the pixels).
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
 */
enum class LiquidGlassKind(
    /** Android's blur radius. The selection lens is clearer than the bar it sits on. */
    val blurDp: Float,
    /** Width of the refraction band, inward from the rim. */
    val bandDp: Float,
    /** How far the rim samples OUTSIDE the pane at its very edge. */
    val depthDp: Float,
    /** Magnification about the pane's centre. */
    val magnify: Float,
    /** Share of the scheme's rim light, rim shade and dispersion. */
    val lightShare: Float,
    val shadeShare: Float,
    val dispersionShare: Float,
    /** The inner specular ring on the lit side of the rim: (dark) -> 0..1. */
    val specular: (dark: Boolean) -> Float,
    /** Half-resolution panes where the density allows (cards: the blur hides it). */
    val halfResolution: Boolean,
) {
    /** The floating navigation bar: the hero of the material. */
    BAR(
        blurDp = 14f, bandDp = 24f, depthDp = 18f, magnify = 1.05f,
        lightShare = 1f, shadeShare = 1f, dispersionShare = 1f,
        specular = { dark -> if (dark) 0.34f else 0.22f },
        halfResolution = false,
    ),

    /** The travelling selection lens: a clearer droplet of the same glass. */
    LENS(
        blurDp = 6f, bandDp = 14f, depthDp = 22f, magnify = 1.12f,
        lightShare = 1.4f, shadeShare = 1.25f, dispersionShare = 1.4f,
        specular = { dark -> if (dark) 0.6f else 0.45f },
        halfResolution = false,
    ),

    /** Cards, chips, fields, plates — everything that sits on the field. */
    CARD(
        blurDp = 10f, bandDp = 18f, depthDp = 7f, magnify = 1.012f,
        lightShare = 0.7f, shadeShare = 0.6f, dispersionShare = 0.4f,
        specular = { dark -> if (dark) 0.28f else 0.16f },
        halfResolution = true,
    ),

    /** The floating action button: a small round lens over the list. */
    FAB(
        blurDp = 10f, bandDp = 16f, depthDp = 18f, magnify = 1.08f,
        lightShare = 1.2f, shadeShare = 1f, dispersionShare = 1f,
        specular = { dark -> if (dark) 0.5f else 0.3f },
        halfResolution = false,
    ),
}

/** The pure numbers behind the glass — no Android types, fully unit-tested. */
object LiquidGlass {
    /** Android's blur "radius". HWUI turns it into sigma = 0.57735·r + 0.5 px. */
    const val BLUR_RADIUS_DP = 14f

    /** Vibrancy: the backdrop's saturation is lifted this much under the glass. */
    const val SATURATION = 1.3f

    /** Width of the refraction band, measured inward from the pill's rim. */
    const val LENS_BAND_DP = 24f

    /** How far the rim samples OUTSIDE the pill at the very edge (the bend). */
    const val LENS_DEPTH_DP = 18f

    /** The pane is a lens, so its centre magnifies the backdrop a little. */
    const val LENS_MAGNIFY = 1.05f

    /** How far the travelling selection lens stretches at full speed. */
    const val LENS_STRETCH = 0.26f

    /** How much of the stretch is given back as height (volume is kept). */
    const val LENS_SQUASH = 0.6f

    /** A hop shorter than this (in slots) does not stretch — no jitter on settle. */
    const val STRETCH_DEAD_ZONE_SLOTS = 0.04f

    /** A jump of this many slots (or more) stretches by the full [LENS_STRETCH]. */
    const val STRETCH_FULL_DISTANCE_SLOTS = 1.5f

    /** Blur radii kept as REAL content on every side of a pane (three sigmas). */
    const val BLUR_SIGMA_MARGIN = 3f

    /** A little air past the blur and the lens reach, in dp. */
    const val MARGIN_AIR_DP = 4f

    /** From this density up, half-resolution panes are indistinguishable from full ones. */
    const val HALF_RES_MIN_DENSITY = 2.5f

    /** AOSP `Blur::convertRadiusToSigma` — what `createBlurEffect(radius)` blurs by. */
    private const val BLUR_SIGMA_SCALE = 0.57735f

    // The lens light by scheme, as plain floats so [profile] stays testable
    // without Compose. [LightLiquidGlass] / [DarkLiquidGlass] carry the same
    // numbers as colours.
    const val LIGHT_RIM_LIGHT = 0f
    const val LIGHT_RIM_SHADE = 0.10f
    const val LIGHT_DISPERSION = 0.05f
    const val DARK_RIM_LIGHT = 0.34f
    const val DARK_RIM_SHADE = 0.45f
    const val DARK_DISPERSION = 0.06f

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
     * How much real content a pane needs around itself on every side, in
     * pixels: enough that the pane samples only un-clamped blur ([radiusPx]),
     * plus the lens's outward reach ([lensDepthPx]) and a little air
     * ([extraPx]).
     */
    fun backdropMarginPx(radiusPx: Float, lensDepthPx: Float, extraPx: Float): Float =
        BLUR_SIGMA_MARGIN * blurSigmaPx(radiusPx) + lensDepthPx + extraPx

    /** The resolution a kind of pane renders at: 1, or 0.5 on dense screens. */
    fun paneScale(kind: LiquidGlassKind, density: Float): Float =
        if (kind.halfResolution && density >= HALF_RES_MIN_DENSITY) 0.5f else 1f

    /** Every number of a pane, in the SURFACE's pixels (not the layer's). */
    fun profile(kind: LiquidGlassKind, density: Float, dark: Boolean): LiquidGlassProfile {
        val blur = kind.blurDp * density
        val depth = kind.depthDp * density
        return LiquidGlassProfile(
            kind = kind,
            scale = paneScale(kind, density),
            blurRadiusPx = blur,
            lensBandPx = kind.bandDp * density,
            lensDepthPx = depth,
            magnify = kind.magnify,
            dispersion = (if (dark) DARK_DISPERSION else LIGHT_DISPERSION) * kind.dispersionShare,
            rimLight = (if (dark) DARK_RIM_LIGHT else LIGHT_RIM_LIGHT) * kind.lightShare,
            rimShade = (if (dark) DARK_RIM_SHADE else LIGHT_RIM_SHADE) * kind.shadeShare,
            specular = kind.specular(dark),
            marginPx = backdropMarginPx(blur, depth, MARGIN_AIR_DP * density),
        )
    }

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
    /** Layer pixels per surface pixel. */
    val scale: Float,
    val blurRadiusPx: Float,
    val lensBandPx: Float,
    val lensDepthPx: Float,
    val magnify: Float,
    val dispersion: Float,
    val rimLight: Float,
    val rimShade: Float,
    val specular: Float,
    /** Real content kept around the pane on every side (surface pixels). */
    val marginPx: Float,
) {
    /** Whether the lens shader runs for this pane at the refractive tier. */
    val bends: Boolean get() = lensDepthPx > 0f && magnify > 1f

    /** Width of the effect layer for a surface [width] (surface pixels). */
    fun layerWidth(width: Float): Int =
        ceil((width + 2f * marginPx) * scale).toInt().coerceAtLeast(1)

    /** Height of the effect layer for a surface [height] (surface pixels). */
    fun layerHeight(height: Float): Int =
        ceil((height + 2f * marginPx) * scale).toInt().coerceAtLeast(1)
}

/**
 * The refraction lens, in AGSL (the SkSL subset `RuntimeShader` accepts).
 *
 * `p` is a pixel of the pane's effect layer — the shape grown by the
 * backdrop margin — so the shape itself is the rectangle
 * [paneLeft, paneTop, paneRight, paneBottom] inside it. For every pixel
 * the shader computes the rounded-rect signed distance to the rim and the
 * outward normal there; inside the band it slides the sampling point
 * OUTWARD along that normal (a smoothstep of how close to the rim the
 * pixel is), so what lies just outside the pane appears bent into its
 * edge — the refraction a thick glass pane gives its surroundings. The
 * whole pane is magnified a little about its centre so it reads as a lens.
 *
 * Three more touches make it glass even where the page is plain: red and
 * blue are refracted a hair further than green (dispersion — the faint
 * prism an edge of thick glass shows), the band is shaded as a curved
 * surface would be (lit where its normal faces the lamp, darker where it
 * turns away), and a specular ring glints on the lit side of the rim.
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

float4 throughLens(float2 p, float2 c, float2 n, float bend, float spread) {
    float2 shifted = p + n * (bend * lensDepth * spread);
    return content.eval(c + (shifted - c) / magnify);
}

half4 main(float2 p) {
    float2 c = float2((paneLeft + paneRight) * 0.5, (paneTop + paneBottom) * 0.5);
    float2 h = float2(paneRight - paneLeft, paneBottom - paneTop) * 0.5;
    float2 core = h - float2(cornerRadius, cornerRadius);
    float2 q = p - c;
    float2 e = abs(q) - core;
    float2 outside = max(e, float2(0.0, 0.0));
    float dist = length(outside) + min(max(e.x, e.y), 0.0) - cornerRadius;
    float2 outward = q - clamp(q, -core, core);
    float2 n = outward / max(length(outward), 0.0001);
    float t = clamp(dist / lensBand + 1.0, 0.0, 1.0);
    float bend = t * t * (3.0 - 2.0 * t);
    float4 middle = throughLens(p, c, n, bend, 1.0);
    float4 warm = throughLens(p, c, n, bend, 1.0 + dispersion);
    float4 cool = throughLens(p, c, n, bend, 1.0 - dispersion);
    float4 col = float4(warm.r, middle.g, cool.b, middle.a);
    float facing = dot(n, float2(-0.3, -0.954));
    float lit = max(facing, 0.0);
    float away = max(-facing, 0.0);
    float edge = bend * bend * bend;
    col.rgb = col.rgb * (1.0 - bend * rimShade * away)
        + float3(bend * rimLight * lit + edge * specular * lit);
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
    /** The selection lens: a wash keeping it legible … */
    val lensBase: Color,
    /** … a tint of the primary light, so the droplet reads as raised, … */
    val lensTint: Color,
    /** … its own hairline rim and the specular band under its upper edge. */
    val lensRim: Color,
    val lensSpec: Color,
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
    paneTint = Color(0xBDFFFFFF),
    sheen = Color(0x38FFFFFF),
    innerShade = Color(0x1A002A6B),
    rimTop = Color(0xF2FFFFFF),
    rimBottom = Color(0x598256C7),
    lensBase = Color(0x80FFFFFF),
    lensTint = Color(0x42B388FF),
    lensRim = Color(0xB3FFFFFF),
    lensSpec = Color(0xCCFFFFFF),
    bloom = Color(0x59FFFFFF),
    lensShadow = Color(0x334A3B76),
    // Near-white glass cannot be lit any whiter: its curvature comes from
    // the shade and the specular ring alone.
    rimLight = LiquidGlass.LIGHT_RIM_LIGHT,
    rimShade = LiquidGlass.LIGHT_RIM_SHADE,
    dispersion = LiquidGlass.LIGHT_DISPERSION,
    field = LightLiquidField,
    dark = false,
)

val DarkLiquidGlass = LiquidGlassColors(
    paneTint = Color(0xB812101A),
    sheen = Color(0x12FFFFFF),
    innerShade = Color(0x73000000),
    rimTop = Color(0xD9B18AFB),
    rimBottom = Color(0x40B18AFB),
    lensBase = Color(0x591A1626),
    lensTint = Color(0x3D6650A0),
    lensRim = Color(0x80FFFFFF),
    lensSpec = Color(0xB3FFFFFF),
    bloom = Color(0x40FFFFFF),
    lensShadow = Color(0x99000000),
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
    var size = IntSize.Zero
    var corner = 0f
    var profile: LiquidGlassProfile? = null
    var tier: LiquidGlassTier? = null
    var effect: RenderEffect? = null

    fun get(
        tier: LiquidGlassTier,
        profile: LiquidGlassProfile,
        shape: Shape,
        width: Float,
        height: Float,
        density: Density,
    ): RenderEffect? {
        if (width <= 0f || height <= 0f || Build.VERSION.SDK_INT < 31) return null
        val w = width.toInt()
        val h = height.toInt()
        val r = cornerRadiusPx(shape, min(width, height), density)
        if (w != size.width || h != size.height || r != corner ||
            profile != this.profile || tier != this.tier || effect == null
        ) {
            effect = Api31.paneEffect(tier, profile, IntSize(w, h), r)
            size = IntSize(w, h)
            corner = r
            this.profile = profile
            this.tier = tier
        }
        return effect
    }
}

/**
 * One pane of glass behind the content of the node it is applied to:
 *
 *  1. the node is clipped to [shape];
 *  2. its own small [GraphicsLayer] gets a copy of the [scene]'s field —
 *     plus the live [content] source when there is one — shifted so the
 *     copy lines up with the real pixels, and grown by the profile's
 *     margin of real content;
 *  3. that layer carries the frost chain (and the lens at the refractive
 *     tier) as its render effect, so the TEXT and ICONS drawn afterwards
 *     are never blurred — only the backdrop copy is;
 *  4. over it the flat [tint] and, with [light], the sheen and the contact
 *     shade — full-width gradients, never a hard-edged shape.
 *
 * Until the node has been measured the pane paints only the tint (it never
 * flashes a sharp copy of the page). [shift] moves the sampling window for
 * panes that travel by transform; [anchor] supplies the position of such
 * a pane (see [LiquidGlassAnchor]).
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
            val effect = cache.get(scene.tier, profile, shape, w, h, density)
            if (effect == null) {
                drawRect(tint)
                return@drawBehind
            }
            val s = profile.scale
            val m = profile.marginPx
            // which root point sits at the pane layer's (0, 0)
            val origin = b.topLeft + shift() - Offset(m, m)
            pane.record(
                this,
                layoutDirection,
                IntSize(profile.layerWidth(w), profile.layerHeight(h)),
            ) {
                scale(s, s, pivot = Offset.Zero) {
                    // the base colour first: the copy is opaque into its margin
                    drawRect(scene.colors.field.base)
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
            pane.renderEffect = effect
            scale(1f / s, 1f / s, pivot = Offset.Zero) {
                translate(-m, -m) { drawLayer(pane) }
            }
            drawRect(tint)
            if (light) drawGlassLight(scene.colors)
        }
}

/** Sheen from the top, contact shade at the bottom — gradients that span the whole clip. */
private fun DrawScope.drawGlassLight(colors: LiquidGlassColors) {
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

@RequiresApi(31)
private object Api31 {
    /** Frost: blur -> vibrancy, then (13+) the lens. Each step wraps the last. */
    fun paneEffect(
        tier: LiquidGlassTier,
        profile: LiquidGlassProfile,
        surfaceSize: IntSize,
        cornerPx: Float,
    ): RenderEffect {
        val s = profile.scale
        val radius = profile.blurRadiusPx * s
        val blur = android.graphics.RenderEffect.createBlurEffect(
            radius,
            radius,
            android.graphics.Shader.TileMode.CLAMP,
        )
        val vibrancy = android.graphics.RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setSaturation(LiquidGlass.SATURATION) },
            ),
        )
        var chain = android.graphics.RenderEffect.createChainEffect(vibrancy, blur)
        if (tier == LiquidGlassTier.REFRACTIVE && profile.bends && Build.VERSION.SDK_INT >= 33) {
            // A driver that rejects the shader must not take the frost
            // down with it: the pane keeps blurring without the bend.
            runCatching {
                val lens = Api33.lensEffect(profile, surfaceSize, cornerPx)
                chain = android.graphics.RenderEffect.createChainEffect(lens, chain)
            }
        }
        return chain.asComposeRenderEffect()
    }
}

@RequiresApi(33)
private object Api33 {
    /** The refraction lens over the already-frosted pane (see [LIQUID_LENS_SKSL]). */
    fun lensEffect(
        profile: LiquidGlassProfile,
        surfaceSize: IntSize,
        cornerPx: Float,
    ) = android.graphics.RenderEffect.createRuntimeShaderEffect(
        android.graphics.RuntimeShader(LIQUID_LENS_SKSL).apply {
            val s = profile.scale
            val m = profile.marginPx * s
            setFloatUniform("paneLeft", m)
            setFloatUniform("paneTop", m)
            setFloatUniform("paneRight", surfaceSize.width * s + m)
            setFloatUniform("paneBottom", surfaceSize.height * s + m)
            setFloatUniform("cornerRadius", cornerPx * s)
            setFloatUniform("lensBand", profile.lensBandPx * s)
            setFloatUniform("lensDepth", profile.lensDepthPx * s)
            setFloatUniform("magnify", profile.magnify)
            setFloatUniform("dispersion", profile.dispersion)
            setFloatUniform("rimLight", profile.rimLight)
            setFloatUniform("rimShade", profile.rimShade)
            setFloatUniform("specular", profile.specular)
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
