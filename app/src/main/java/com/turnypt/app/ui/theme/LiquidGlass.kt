package com.turnypt.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * v1.6.0-beta.11: REAL Liquid Glass for the main menu — the page itself,
 * frosted and bent, seen through the bar.
 *
 * The v1.5.10 material ("Calm Vault" glass) is a tint, a rim and a
 * shadow: nothing of what lies BEHIND a pane survives it. This file adds
 * the missing half — the backdrop — in three layers, cheapest first:
 *
 *  1. CAPTURE. The content area is recorded into one [GraphicsLayer]
 *     every time it draws ([liquidGlassSource]) and that same recording
 *     is both the normal draw AND the glass's texture. Nothing is drawn
 *     twice to the screen; the bar is a sibling of the recorded tree, so
 *     it can never see itself.
 *  2. FROST. The pane behind the pill re-draws the recording through a
 *     RenderEffect chain: blur -> vibrancy (saturation lift). The effect
 *     sits on the PANE's own layer, which is only the pill plus a margin
 *     of real content (three blur sigmas), so the blur never touches the
 *     whole screen.
 *  3. LENS (Android 13+). An AGSL runtime shader — [LIQUID_LENS_SKSL] —
 *     ends the chain: a rounded-rect distance field bends the sampling
 *     point outward near the rim, so content appears to wrap around the
 *     pill's edge the way a thick glass lens refracts what is under it,
 *     and shades the rim like a curved surface catching the light.
 *
 * Why this cannot lag the way the v1.6.0-alpha.5 attempt did: that build
 * called the member `GraphicsLayer.record(density, …)`, which does NOT
 * retarget the canvas — the page was painted twice to the screen at full
 * resolution every frame while the layer it meant to blur stayed EMPTY
 * (the "cost of blur, look of nothing" report). Here the DrawScope
 * extension `record { drawContent() }` is used, which does retarget it;
 * the page is rasterised once, and only a clipped copy the size of the
 * bar goes through the blur. At rest nothing is captured, blurred or
 * drawn: an idle screen costs exactly what it did before.
 *
 * Devices choose their layer through [LiquidGlass.tier]: full material on
 * 13+, frost only on 11–12, and the v1.5.10 pane (no capture modifier at
 * all) below 11, on low-RAM hardware, or when the user switches the live
 * glass off in Settings.
 */

/** How much of the material this device renders. */
enum class LiquidGlassTier {
    /** No backdrop capture: the v1.5.10 pane (tint, rim, shadow). */
    STATIC,

    /** The page behind the bar is captured and frosted (blur + vibrancy). */
    FROSTED,

    /** Frost plus the refraction lens at the rim — the full material. */
    REFRACTIVE,
}

/** The pure numbers behind the glass — no Android types, fully unit-tested. */
object LiquidGlass {
    /**
     * Android's blur "radius". HWUI turns it into sigma = 0.57735·r + 0.5
     * px (so 14dp is a sigma of ~8.6dp). Deliberately a SOFT frost, not a
     * heavy one: a lens can only bend structure that survived the blur,
     * and the tint below — not the blur — is what keeps text under the
     * bar unreadable.
     */
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

    /** Blur radii kept as REAL content on every side of the pill (three sigmas). */
    const val BLUR_SIGMA_MARGIN = 3f

    /**
     * The tint is laid down in two washes so it can thin toward the rim
     * (where the lens works): a flat base over the whole pill, carrying
     * this share of the final centre alpha, plus a blurred inner pill that
     * tops the centre up to the full alpha.
     */
    const val TINT_BASE_SHARE = 0.6f

    /** The inner pill is inset this far (dp) and blurred by half of it. */
    const val TINT_RAMP_DP = 10f

    /** AOSP `Blur::convertRadiusToSigma` — what `createBlurEffect(radius)` blurs by. */
    private const val BLUR_SIGMA_SCALE = 0.57735f

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
     * How much real content the pane needs around the pill on every side,
     * in pixels: enough that the pill area samples only un-clamped blur
     * ([radiusPx]), plus the lens's outward reach ([lensDepthPx]) and a
     * little air ([extraPx]).
     */
    fun backdropMarginPx(radiusPx: Float, lensDepthPx: Float, extraPx: Float): Float =
        BLUR_SIGMA_MARGIN * blurSigmaPx(radiusPx) + lensDepthPx + extraPx

    /** The alpha of the flat base wash for a tint of [centreAlpha]. */
    fun tintBaseAlpha(centreAlpha: Float): Float = (TINT_BASE_SHARE * centreAlpha).coerceIn(0f, 1f)

    /**
     * The alpha the blurred inner pill needs so that, over the base wash,
     * the centre of the pane reaches exactly [centreAlpha].
     */
    fun tintBodyAlpha(centreAlpha: Float): Float {
        val a = centreAlpha.coerceIn(0f, 1f)
        val base = tintBaseAlpha(a)
        return if (base >= 1f) 0f else (1f - (1f - a) / (1f - base)).coerceIn(0f, 1f)
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

/**
 * The refraction lens, in AGSL (the SkSL subset `RuntimeShader` accepts).
 *
 * `p` is a pixel of the pane's effect layer — the pill grown by the
 * backdrop margin — so the pill itself is the rectangle
 * [paneLeft, paneTop, paneRight, paneBottom] inside it. For every pixel
 * the shader computes the rounded-rect signed distance to the pill's rim
 * and the outward normal there; inside the band it slides the sampling
 * point OUTWARD along that normal (a smoothstep of how close to the rim
 * the pixel is), so what lies just outside the pill appears bent into its
 * edge — the refraction a thick glass pill gives its surroundings. The
 * whole pane is magnified a little about its centre so it reads as a lens.
 *
 * Two more touches make it glass even where the page is plain: red and
 * blue are refracted a hair further than green (dispersion — the faint
 * prism an edge of thick glass shows), and the band is shaded as a curved
 * surface would be, lit where its normal faces the light and darker where
 * it turns away.
 *
 * The marker comments are what `scripts/lg_preview.py` extracts to render
 * the device shader on the desktop: edit the shader here, never there.
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
    col.rgb = col.rgb * (1.0 - bend * rimShade * away) + float3(bend * rimLight * lit);
    return half4(col);
}
// </lens-shader>
""".trimIndent()

/**
 * The light the material is made of, resolved per colour scheme by
 * [liquidGlassColors]. Everything here is drawn OVER the refracted
 * backdrop; the text and icon colours stay the Material roles they
 * always were, so contrast never depends on the blur.
 */
@Stable
class LiquidGlassColors(
    /** The glass wash over the frosted backdrop — full strength in the centre. */
    val paneTint: Color,
    /** Specular wash: a ceiling light raking the pane from the top-left. */
    val sheen: Color,
    /** Contact shade along the bottom inside edge — the pane's thickness. */
    val innerShade: Color,
    /** The 1dp rim: bright where the light lands (top-left) … */
    val rimStart: Color,
    val rimMiddle: Color,
    /** … and quiet where it leaves (bottom-right). */
    val rimEnd: Color,
    /** The selection lens: a raised droplet of the same glass. */
    val lensTop: Color,
    val lensBottom: Color,
    val lensRim: Color,
    val lensSpec: Color,
    val lensShadow: Color,
    /** The soft brightening that spreads under a slot while it is pressed. */
    val bloom: Color,
    /** Lens shading (see [LIQUID_LENS_SKSL]): light added where the rim faces the lamp. */
    val rimLight: Float,
    /** … and darkening where it turns away from it. */
    val rimShade: Float,
    /** Dispersion at the rim: how much further red (and nearer blue) travel, as a share of the bend. */
    val dispersion: Float,
)

val LightLiquidGlass = LiquidGlassColors(
    paneTint = Color(0x99FFFFFF),
    sheen = Color(0x33FFFFFF),
    innerShade = Color(0x1A000000),
    rimStart = Color(0xF2FFFFFF),
    rimMiddle = Color(0x338256C7),
    rimEnd = Color(0x4D8256C7),
    lensTop = Color(0xFAFFFFFF),
    lensBottom = Color(0xF2D9C6F7),
    lensRim = Color(0xCCFFFFFF),
    lensSpec = Color(0xE6FFFFFF),
    lensShadow = Color(0x26002A6B),
    bloom = Color(0x59FFFFFF),
    // Near-white glass cannot be lit any whiter: its curvature comes from
    // the shade alone.
    rimLight = 0f,
    rimShade = 0.09f,
    dispersion = 0.05f,
)

val DarkLiquidGlass = LiquidGlassColors(
    paneTint = Color(0xA112101A),
    sheen = Color(0x14FFFFFF),
    innerShade = Color(0x80000000),
    rimStart = Color(0xD9B18AFB),
    rimMiddle = Color(0x59B18AFB),
    rimEnd = Color(0x40B18AFB),
    lensTop = Color(0xF76650A0),
    lensBottom = Color(0xF03D2F66),
    lensRim = Color(0x99FFFFFF),
    lensSpec = Color(0xCCFFFFFF),
    lensShadow = Color(0x80000000),
    bloom = Color(0x40FFFFFF),
    // On OLED black the rim GLOWS: additive light along the upper edge is
    // what makes an empty page read as a curved lens.
    rimLight = 0.34f,
    rimShade = 0.45f,
    dispersion = 0.06f,
)

/** The material's light, picked from the active scheme (no second theme flag). */
@Composable
fun liquidGlassColors(): LiquidGlassColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) DarkLiquidGlass else LightLiquidGlass
}

/**
 * Everything the two halves of the material share: the recording of the
 * page, the tier, and — read only while drawing — where the pill sits so
 * the pane can line its copy of the page up with the real one.
 */
@Stable
class LiquidGlassState internal constructor(
    /** The page recording. Written by [liquidGlassSource], read by the pane. */
    val content: GraphicsLayer,
    val tier: LiquidGlassTier,
    /** Real content kept around the pill on every side ([LiquidGlass.backdropMarginPx]). */
    val margin: Dp,
) {
    val live: Boolean get() = tier != LiquidGlassTier.STATIC

    /** Top-left of the recorded page in window coordinates. */
    internal var contentOrigin by mutableStateOf(Offset.Zero)

    /** The pill's rectangle in window coordinates. */
    internal var pillRect by mutableStateOf(Rect.Zero)

    /**
     * The frost (+ lens) chain for a pane of the given pill size, or null
     * when this tier does not capture. Built once per size and light: the
     * blur radius and every lens uniform are pixels, so they depend on
     * density.
     */
    internal fun backdropEffect(
        pill: IntSize,
        density: Float,
        colors: LiquidGlassColors,
    ): RenderEffect? {
        if (tier == LiquidGlassTier.STATIC || pill == IntSize.Zero) return null
        if (Build.VERSION.SDK_INT < 31) return null
        val marginPx = (margin.value * density).toInt()
        return Api31.backdropEffect(
            tier = tier,
            widthPx = pill.width + marginPx * 2,
            heightPx = pill.height + marginPx * 2,
            marginPx = marginPx,
            density = density,
            colors = colors,
        )
    }
}

/**
 * The material's shared state: one page recording plus the tier this
 * device renders at. Recreated only when the tier can change.
 */
@Composable
fun rememberLiquidGlassState(enabled: Boolean): LiquidGlassState {
    val content = rememberGraphicsLayer()
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val lowRam = remember(context) { isLowRamDevice(context) }
    val tier = LiquidGlass.tier(Build.VERSION.SDK_INT, lowRam, enabled)
    val margin = with(density) {
        val radiusPx = LiquidGlass.BLUR_RADIUS_DP.dp.toPx()
        LiquidGlass.backdropMarginPx(
            radiusPx = radiusPx,
            lensDepthPx = LiquidGlass.LENS_DEPTH_DP.dp.toPx(),
            extraPx = 4.dp.toPx(),
        ).toDp()
    }
    return remember(content, tier, margin) { LiquidGlassState(content, tier, margin) }
}

private fun isLowRamDevice(context: Context): Boolean = runCatching {
    context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
}.getOrDefault(false)

/**
 * Marks the tree whose drawing becomes the glass's texture. Applied ONCE,
 * around the content area only — the bar itself stays a sibling, outside
 * the recording, so the pane can never feed its own output back in.
 *
 * Each draw records the content into the shared layer through the
 * DrawScope `record` extension (which retargets the canvas into the
 * layer) and then draws that layer: the page is rasterised once, exactly
 * as before, and the recording is free for the pane to reuse.
 */
fun Modifier.liquidGlassSource(state: LiquidGlassState): Modifier =
    this
        .onGloballyPositioned { coordinates ->
            state.contentOrigin = coordinates.positionInRoot()
        }
        .drawWithContent {
            state.content.record { this@drawWithContent.drawContent() }
            drawLayer(state.content)
        }

/**
 * The pane behind the pill: laid out [LiquidGlassState.margin] larger than
 * the pill on every side (so the blur samples real content, never its own
 * clamped edge), given the frost chain as its layer's render effect, and
 * painted with a copy of the page recording shifted to line up with the
 * real page. The parent clips all of it back to the pill.
 *
 * Until the pill has been measured (first frame) the effect is null and
 * the pane paints only the app field — it never flashes an unfrosted,
 * sharp copy of the page.
 */
@Composable
fun Modifier.liquidGlassBackdrop(
    state: LiquidGlassState,
    colors: LiquidGlassColors,
    fieldColor: Color,
): Modifier {
    val density = LocalDensity.current.density
    val pill = state.pillRect
    val pillSize = IntSize(pill.size.width.toInt(), pill.size.height.toInt())
    val effect = remember(state, pillSize, density, colors) {
        state.backdropEffect(pillSize, density, colors)
    }
    val margin = state.margin
    return this
        .onGloballyPositioned { coordinates ->
            state.pillRect = Rect(
                coordinates.positionInRoot(),
                Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
            )
        }
        .overscanBy(margin)
        .graphicsLayer {
            compositingStrategy = if (effect != null) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
            renderEffect = effect
        }
        .drawBehind {
            // The app field, carried past the screen edges so the blur's
            // clamp finds the same colour the real page sits on.
            drawRect(fieldColor)
            if (effect != null) {
                val m = margin.roundToPx().toFloat()
                val shift = state.contentOrigin - state.pillRect.topLeft + Offset(m, m)
                translate(shift.x, shift.y) { drawLayer(state.content) }
            }
        }
}

/** Measures the content [margin] larger than the slot on every side. */
private fun Modifier.overscanBy(margin: Dp): Modifier = layout { measurable, constraints ->
    val m = margin.roundToPx()
    val width = constraints.maxWidth + m * 2
    val height = constraints.maxHeight + m * 2
    val placeable = measurable.measure(
        Constraints(
            minWidth = width,
            maxWidth = width,
            minHeight = height,
            maxHeight = height,
        ),
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(-m, -m)
    }
}

@RequiresApi(31)
private object Api31 {
    /** Frost: blur -> vibrancy, then (13+) the lens. Each step wraps the last. */
    fun backdropEffect(
        tier: LiquidGlassTier,
        widthPx: Int,
        heightPx: Int,
        marginPx: Int,
        density: Float,
        colors: LiquidGlassColors,
    ): RenderEffect {
        val radius = LiquidGlass.BLUR_RADIUS_DP * density
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
        if (tier == LiquidGlassTier.REFRACTIVE && Build.VERSION.SDK_INT >= 33) {
            // A driver that rejects the shader must not take the frost
            // down with it: the pane keeps blurring without the bend.
            runCatching {
                val lens = Api33.lensEffect(widthPx, heightPx, marginPx, density, colors)
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
        widthPx: Int,
        heightPx: Int,
        marginPx: Int,
        density: Float,
        colors: LiquidGlassColors,
    ) = android.graphics.RenderEffect.createRuntimeShaderEffect(
        android.graphics.RuntimeShader(LIQUID_LENS_SKSL).apply {
            setFloatUniform("paneLeft", marginPx.toFloat())
            setFloatUniform("paneTop", marginPx.toFloat())
            setFloatUniform("paneRight", (widthPx - marginPx).toFloat())
            setFloatUniform("paneBottom", (heightPx - marginPx).toFloat())
            setFloatUniform("cornerRadius", (heightPx - marginPx * 2) / 2f)
            setFloatUniform("lensBand", LiquidGlass.LENS_BAND_DP * density)
            setFloatUniform("lensDepth", LiquidGlass.LENS_DEPTH_DP * density)
            setFloatUniform("magnify", LiquidGlass.LENS_MAGNIFY)
            setFloatUniform("dispersion", colors.dispersion)
            setFloatUniform("rimLight", colors.rimLight)
            setFloatUniform("rimShade", colors.rimShade)
        },
        "content",
    )
}

/**
 * The pane's own light, painted over the backdrop. The tint is TWO washes:
 * a flat base over the whole pill, and a blurred inner pill that tops the
 * centre up to full strength — so the glass is thinnest at its rim, right
 * where the lens bends the page, and densest behind the tabs, where the
 * labels need their contrast. A specular sheen rakes in from the top-left
 * and a contact shade closes the bottom edge.
 *
 * Without a live backdrop this is the v1.5.10 flat tint.
 */
fun Modifier.liquidGlassSurface(
    colors: LiquidGlassColors,
    live: Boolean,
    flatTint: Color,
): Modifier = drawBehind {
    if (!live) {
        drawRect(flatTint)
        return@drawBehind
    }
    val tint = colors.paneTint
    val base = tint.copy(alpha = LiquidGlass.tintBaseAlpha(tint.alpha))
    val body = tint.copy(alpha = LiquidGlass.tintBodyAlpha(tint.alpha))
    drawRect(base)
    val ramp = LiquidGlass.TINT_RAMP_DP.dp.toPx()
    val radius = (size.height - ramp * 2f) / 2f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            ramp,
            ramp,
            size.width - ramp,
            size.height - ramp,
            radius,
            radius,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = body.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(
                    ramp / 2f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            },
        )
    }
    drawRect(
        brush = Brush.linearGradient(
            0f to colors.sheen,
            0.6f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width * 0.8f, size.height * 1.2f),
        ),
    )
    val shadeHeight = 9.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to colors.innerShade,
            startY = size.height - shadeHeight,
            endY = size.height,
        ),
    )
}

/**
 * The rim, drawn last so it sits over everything the pane contains: one
 * hairline, bright where the light lands and quiet where it leaves.
 */
fun Modifier.liquidGlassRim(
    colors: LiquidGlassColors,
    live: Boolean,
    flatRim: Color,
): Modifier = drawWithContent {
    drawContent()
    val width = 1.dp.toPx()
    drawRoundRect(
        brush = if (live) {
            Brush.linearGradient(
                0f to colors.rimStart,
                0.5f to colors.rimMiddle,
                1f to colors.rimEnd,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        } else {
            SolidColor(flatRim)
        },
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((size.height - width) / 2f),
        style = Stroke(width),
    )
}

/**
 * The selection lens's body: a raised droplet of the same glass — a
 * top-lit gradient, a specular streak under its upper edge and its own
 * hairline rim. Without a live backdrop it is the flat selection pill.
 */
fun Modifier.liquidLens(
    colors: LiquidGlassColors,
    live: Boolean,
    flatColor: Color,
): Modifier = drawBehind {
    val radius = size.height / 2f
    if (!live) {
        drawRoundRect(flatColor, cornerRadius = CornerRadius(radius))
        return@drawBehind
    }
    drawRoundRect(
        brush = Brush.verticalGradient(0f to colors.lensTop, 1f to colors.lensBottom),
        cornerRadius = CornerRadius(radius),
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to colors.lensSpec,
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
        ),
        size = Size(size.width, 7.dp.toPx()),
        cornerRadius = CornerRadius(radius),
    )
    val rim = 0.8.dp.toPx()
    drawRoundRect(
        brush = SolidColor(colors.lensRim),
        topLeft = Offset(rim / 2f, rim / 2f),
        size = Size(size.width - rim, size.height - rim),
        cornerRadius = CornerRadius(radius - rim / 2f),
        style = Stroke(rim),
    )
}

/** The selection lens's soft drop shadow. Applied OUTSIDE the lens's clip. */
fun Modifier.liquidLensShadow(colors: LiquidGlassColors): Modifier = drawBehind {
    val lift = 2.dp.toPx()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            0f,
            lift,
            size.width,
            size.height + lift,
            size.height / 2f,
            size.height / 2f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.lensShadow.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(
                    2.dp.toPx(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            },
        )
    }
}

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
