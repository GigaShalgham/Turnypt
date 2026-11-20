package com.turnypt.app.ui.theme

import android.os.Build
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

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
    /**
     * v1.6.0-alpha.4: the BLURRED underlay beneath the nav pane — the
     * user's "تار" (real blur, not just opacity). A translucent wash of
     * the scheme's own glass colour; [glassFrost] smears it with a
     * RenderEffect (API 31+; below 31 it stays a plain frost wash).
     */
    val frostTint: Color,
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
    // v1.6.0-alpha.3 (user report: "the fully transparent glass menu lets
    // you read — and want to tap — the buttons behind it"): FROSTED
    // glass. The navTint is lifted to near-opaque (0xB8 → 0xF2): the
    // backdrop now reads through only as a soft blur of light, the
    // slots' content is clearly separated from the page beneath, and
    // nothing behind the bar looks tappable. The rim alpha is raised
    // (0x47 → 0x66) so the refraction edge stays crisp against the
    // heavier tint.
    tint = Color(0xB8FFFFFF),
    tintHigh = Color(0xE8F0EAF9),
    // v1.6.0-alpha.4: the refraction rim strengthened for the frosted
    // pane (0x47 → 0x80) — a heavier violet edge that reads as LIGHT
    // BENDING through the blurred glass, the "شکست نور" half of the
    // report; the blur carries the "تار" half.
    border = Color(0x808256C7),
    highlight = Color(0x5CE8DDF9),
    // v1.6.0-alpha.5: the pane goes TRANSLUCENT again (0xF2 → 0x80).
    // alpha.3 raised navTint to near-opaque to "frost" the bar, but with
    // the REAL backdrop blur now in the pane (glassBackdropPane samples
    // the recorded screen pixels and smears them), opacity is the WRONG
    // tool — a near-opaque pane HIDES the very blur the user demanded
    // twice. The layering that works: translucent pane (0x80) → the
    // blurred screen content reads through it as smeared light → frost
    // wash (frostTint) → rim. Below API 31 (no blur) the caller still
    // gets honest opacity from frostTint — see TurnyptApp.
    navTint = Color(0x80FFFFFF),
    // v1.6.0-alpha.5: the frost wash OVER the blurred backdrop — half
    // the old weight (0xB8 → 0x5C): it now only cools the smeared
    // pixels' colour, never hides them.
    frostTint = Color(0x5CE8DDF9),
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
    // v1.6.0-alpha.4: refraction rim strengthened for the frosted pane
    // (0x47 → 0x80) — the violet edge reads as light bending through
    // the blur; brighter than v1.5.0's hairline because the pane itself
    // is heavier now (0x99 wash under a 0x47 rim vanished).
    border = Color(0x80B18AFB),
    highlight = Color(0x33B18AFB),
    // v1.5.5: a transparent bar pane must sit on a colour it does not
    // shift — the old navTint was a wash OF the backdrop's plum foot,
    // so bar-over-plum stacked into the reported purple band. The tint
    // is now the scheme's OWN neutral background colour (12101A), the
    // exact field the cards sit on.
    // v1.6.0-alpha.3 (user report: "the fully transparent glass menu lets
    // you read — and want to tap — the buttons behind it"): FROSTED
    // glass, dark side. The near-invisible wash (0x59) becomes a proper
    // smoked pane (0x99) — the page beneath the bar is no longer legible
    // enough to invite a tap through it, and the violet rim carries the
    // light-refraction character against the heavier tint.
    // v1.6.0-alpha.5: with the REAL backdrop blur in the pane, the dark
    // smoked pane also leans out (0x99 → 0x73) so the smeared content
    // reads through it — the blur makes the text unreadable; opacity
    // only has to keep the colour.
    navTint = Color(0x7312101A),
    // v1.6.0-alpha.5: the dark frost wash over the blurred backdrop —
    // half the old weight (0x66 → 0x40): cools the smeared pixels
    // without hiding them (the v1.5.5 lesson: no NEW colour at the
    // screen's foot).
    frostTint = Color(0x40191526),
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

    // v1.5.10 (lag audit, round two): the Shape objects for the fixed
    // radii, built ONCE. Call sites used to allocate a fresh
    // RoundedCornerShape on every recomposition — ContactCard alone
    // did it per contact per recomposition, and the LazyColumn
    // recomposes a card whenever its unlock chip flips. A corner-radius
    // shape with no corner overrides equals by value, so caching the
    // object changes NOTHING visually; only the allocation traffic is
    // gone. `pill` keeps the 9999.dp form (a stadium is what a huge
    // radius renders as).
    val chipShape: RoundedCornerShape = RoundedCornerShape(chip)
    val fieldShape: RoundedCornerShape = RoundedCornerShape(field)
    val cardShape: RoundedCornerShape = RoundedCornerShape(card)
    val sheetShape: RoundedCornerShape = RoundedCornerShape(sheet)
    val pillShape: RoundedCornerShape = RoundedCornerShape(pill)
}

/**
 * v1.6.0-alpha.5 — the REAL backdrop blur, done right.
 *
 * The alpha.4 attempt was mathematically dead: it blurred a FLAT COLOR
 * swatch, and blurring a uniform fill returns the same uniform fill —
 * zero visible change, exactly what the user (rightly) called out
 * ("هیچ تاری و شکسته نوری وجود نداره"). What frosted glass actually
 * does is blur THE PIXELS BEHIND IT — the live screen content as it
 * scrolls under the pane.
 *
 * Compose cannot read the composited backdrop, but it CAN re-render any
 * subtree into a [GraphicsLayer] and draw that layer again with a
 * RenderEffect. So the app's own content becomes the backdrop:
 *
 *   Box(backdrop) {
 *     NavHost(.glassCapture(contentLayer))   // records itself every frame
 *     Bar(.glassBackdropPane(contentLayer))  // draws contentLayer BLURRED
 *   }
 *
 * Step 1 — THIS modifier: [glassCapture] wraps the content and mirrors
 * it into the shared [layer]. Every frame the content draws itself
 * normally (unchanged), then the same pixels are recorded into the
 * layer — a second, cheap draw into an offscreen buffer (HwuiAccelerate
 * or a RenderNode; the platform picks). The layer is NEVER drawn to
 * the screen here; it is only a snapshot the bar will sample.
 *
 * Performance shape: the record is a RenderNode replay, not a bitmap
 * copy (no readback, no GPU→CPU stall on any API level); the cost is
 * roughly one extra content draw pass while the nav bar is on screen.
 * The v1.5.0 "no per-frame shader" rule is kept in spirit: no shader is
 * compiled per frame — one static [BlurEffect] is applied ONCE on the
 * bar's layer (see [glassBackdropPane]), never animated.
 */
fun Modifier.glassCapture(
    layer: GraphicsLayer,
): Modifier = this.drawWithCache {
    // Record THIS node's content into the shared layer every frame.
    onDrawWithContent {
        // Record FIRST (the content must be in the layer before the bar
        // draws it blurred later in this same frame). The layer records
        // with THIS scope's density/fontScale, so its coordinate system
        // matches the screen's exactly.
        layer.record(
            Density(density, fontScale),
            layoutDirection,
            IntSize(size.width.roundToInt(), size.height.roundToInt()),
        ) {
            this@onDrawWithContent.drawContent()
        }
        // …then draw the content to the screen as if nothing happened.
        drawContent()
    }
}


/**
 * v1.6.0-alpha.5 — Step 2: the glass pane that DRAWS the recorded
 * backdrop, blurred. Applied INSIDE the nav bar, as the pane's own
 * draw pass (under the tint, bevel and slots that [glassSurface]
 * paints above it).
 *
 * What the user sees when this works: the page's real content —
 * cards, text, list rows — visibly SMearing and swimming as they
 * scroll beneath the floating pill, like a frosted window over the
 * screen. That is the "تار" they asked for, and it can only exist
 * when real pixels are blurred, which is why alpha.4 (a blurred flat
 * swatch) showed nothing.
 *
 * The blur, drawn edge to edge of the pane, then CLIPPED to the pill:
 *
 *  1. clip to [shape] (the pill),
 *  2. translate so the pane samples the region of the screen it
 *     actually floats over (the layer holds the WHOLE screen),
 *  3. scale the sampled region by [lensScale] about the pane's center —
 *     the refraction ("شکست نور"): a convex glass edge magnifies its
 *     backdrop slightly, so the blurred content also carries a faint
 *     lens distortion at the pane's expense,
 *  4. draw [layer] with a static BlurEffect RenderEffect,
 *  5. over it, [frostTint] wash (the glass's own colour) — kept
 *     LIGHTER than the pane tint because the blur now does the work
 *     that opacity used to do.
 *
 * API < 31 (no RenderEffect): the pane still samples and draws the
 * backdrop UNBLURRED under the frost wash — translucent, honest, and
 * the refraction scale still reads — but there is no smear. On those
 * devices (API 26–30) the pane also gets the alpha.3 heavier wash so
 * nothing behind the bar invites a tap; see the caller in TurnyptApp.
 */
fun Modifier.glassBackdropPane(
    layer: GraphicsLayer,
    shape: Shape,
    frostTint: Color,
    blurRadiusPx: Float,
    lensScale: Float = 1.08f,
    paneTopLeft: (() -> IntOffset)? = null,
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val panePath = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Generic -> outline.path
    }
    // One static blur effect, created once per pane build — never
    // animated, never recompiled (the v1.5.0 rule).
    val blurEffect = if (Build.VERSION.SDK_INT >= 31) {
        BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
    } else {
        null
    }
    // v1.6.0-alpha.5 fallback: without RenderEffect there is no smear,
    // so the frost wash doubles as the honest opacity (alpha.3 weight)
    // — the recorded backdrop still reads through it, but nothing
    // behind the bar is legible enough to invite a tap.
    val effectiveTint = if (blurEffect != null) frostTint
    else frostTint.copy(alpha = (frostTint.alpha * 1.75f).coerceAtMost(0.92f))
    val tintBrush = SolidColor(effectiveTint)
    onDrawWithContent {
        // The pane's own content (slots, labels) draws ABOVE the blurred
        // backdrop: backdrop first, then the caller's content.
        clipPath(panePath) {
            // Lens: scale the sampled backdrop about the pane's center —
            // the refraction ("شکست نور") a convex pane causes. Scaling the
            // LAYER scales the whole recorded screen about the pane's
            // center pivot, which reads as the pane magnifying its slice
            // of the world.
            layer.scaleX = lensScale
            layer.scaleY = lensScale
            layer.pivotOffset = Offset(size.width / 2f, size.height / 2f)
            layer.renderEffect = blurEffect
            // The layer records the WHOLE screen (the NavHost at 0,0 in
            // screen coordinates); the pane sits at [paneTopLeft] in the
            // same coordinates, so translating by -paneTopLeft makes the
            // layer's pixels land where the screen's pixels are — the pane
            // shows a blurred copy of EXACTLY what floats behind it.
            val pos = paneTopLeft?.invoke() ?: IntOffset.Zero
            translate(-pos.x.toFloat(), -pos.y.toFloat()) {
                drawLayer(layer)
            }
            // The glass's own colour over the smeared backdrop.
            drawRect(tintBrush)
        }
        // The pane's own content (the caller's tint/bevel/slots painted by
        // the modifiers ABOVE this one on the chain) draws OVER the blur.
        this@onDrawWithContent.drawContent()
    }
}

/**
 * The quiet backdrop every screen sits on. Applied once around the app
 * content, never per-card.
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

/**
 * The refraction rim: uniform alpha across the full surface height.
 *
 * v1.5.9 (lag audit): the rim "gradient" had TWO IDENTICAL stops —
 * [glass.border, glass.border] — a solid fill wearing a gradient's
 * allocation cost. The v1.5.7 pass converted the tint to a SolidColor
 * but missed this one: every glass card rebuilt the brush (and its
 * shader on first paint) on every recomposition, and the sliding nav
 * pill forces the glass bar to redraw at animation frequency. A solid
 * brush paints the exact same pixels — byte-for-byte identical output.
 */
private fun borderBrush(glass: GlassColors): Brush = SolidColor(glass.border)

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
