package com.turnypt.app.ui.theme

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure numbers behind the Liquid Glass MATERIAL — one engine behind
 * every glass surface in the app (theme/LiquidGlass.kt).
 *
 * v1.6.0-beta.14 holds the lens mapping to its two promises, on the pane
 * sizes the app really ships (the device report on beta.13: an upside-down
 * copy of the strip under the menu appeared inside the menu's bottom edge):
 *
 *  - CONFINED — every pixel of a pane shows a pixel of the page that lies
 *    under that same pane, so nothing from beyond a rim can be drawn in;
 *  - FOLD-FREE — the mapping never runs backwards (a positive Jacobian
 *    everywhere), so no strip is mirrored inside another.
 *
 * [lensSample] is the mapping in plain floats, the same arithmetic as the
 * shader; the render harness holds the shader text itself to the same two
 * promises on real pixels.
 */
class LiquidGlassTest {

    /** A pane of the app: half sizes and corner radius in pixels at 3x. */
    private class Pane(val kind: LiquidGlassKind, val halfWidth: Float, val halfHeight: Float, val corner: Float)

    private val bar = Pane(LiquidGlassKind.BAR, halfWidth = 492f, halfHeight = 99f, corner = 99f)      // 328x66dp pill
    private val lens = Pane(LiquidGlassKind.LENS, halfWidth = 70f, halfHeight = 72f, corner = 70f)     // 46.7x48dp pill
    private val fab = Pane(LiquidGlassKind.FAB, halfWidth = 240f, halfHeight = 84f, corner = 48f)      // 160x56dp, 16dp rim
    private val panes = listOf(bar, lens, fab)

    private val density = 3f

    private fun numbers(pane: Pane, dark: Boolean = false): LiquidGlassProfile =
        LiquidGlass.profile(pane.kind, density, dark)

    /** The Jacobian determinant of the mapping at (x, y), by central differences. */
    private fun determinant(pane: Pane, x: Float, y: Float, band: Float, depth: Float, magnify: Float): Float {
        val e = 0.05f
        fun at(px: Float, py: Float) = lensSample(px, py, pane.halfWidth, pane.halfHeight, pane.corner, band, depth, magnify)
        val right = at(x + e, y)
        val left = at(x - e, y)
        val down = at(x, y + e)
        val up = at(x, y - e)
        val a = (right.x - left.x) / (2f * e)
        val b = (down.x - up.x) / (2f * e)
        val c = (right.y - left.y) / (2f * e)
        val d = (down.y - up.y) / (2f * e)
        return a * d - b * c
    }

    @Test
    fun `tier follows the platform`() {
        assertEquals(LiquidGlassTier.STATIC, LiquidGlass.tier(sdkInt = 26, lowRam = false, enabled = true))
        assertEquals(LiquidGlassTier.STATIC, LiquidGlass.tier(sdkInt = 30, lowRam = false, enabled = true))
        assertEquals(LiquidGlassTier.FROSTED, LiquidGlass.tier(sdkInt = 31, lowRam = false, enabled = true))
        assertEquals(LiquidGlassTier.FROSTED, LiquidGlass.tier(sdkInt = 32, lowRam = false, enabled = true))
        assertEquals(LiquidGlassTier.REFRACTIVE, LiquidGlass.tier(sdkInt = 33, lowRam = false, enabled = true))
        assertEquals(LiquidGlassTier.REFRACTIVE, LiquidGlass.tier(sdkInt = 36, lowRam = false, enabled = true))
    }

    @Test
    fun `low memory or the switch off means the static pane`() {
        assertEquals(LiquidGlassTier.STATIC, LiquidGlass.tier(sdkInt = 36, lowRam = true, enabled = true))
        assertEquals(LiquidGlassTier.STATIC, LiquidGlass.tier(sdkInt = 36, lowRam = false, enabled = false))
        assertEquals(LiquidGlassTier.STATIC, LiquidGlass.tier(sdkInt = 30, lowRam = true, enabled = false))
    }

    @Test
    fun `blur sigma matches the platform conversion`() {
        assertEquals(0f, LiquidGlass.blurSigmaPx(0f), 0f)
        assertEquals(0f, LiquidGlass.blurSigmaPx(-5f), 0f)
        // a 33px radius (11dp at 3x): 0.57735 * 33 + 0.5
        assertEquals(19.5526f, LiquidGlass.blurSigmaPx(33f), 0.001f)
    }

    @Test
    fun `the backdrop margin keeps three sigmas of the blur's fade and a little air`() {
        val radius = 33f // 11dp at 3x
        val sigma = LiquidGlass.blurSigmaPx(radius)
        assertEquals(3f * sigma + 12f, LiquidGlass.backdropMarginPx(radius, extraPx = 12f), 0.001f)
        assertTrue(LiquidGlass.backdropMarginPx(radius, 0f) >= 3f * sigma)
        assertTrue(LiquidGlass.backdropMarginPx(radius + 10f, 12f) > LiquidGlass.backdropMarginPx(radius, 12f))
    }

    @Test
    fun `the profile resolves every pane kind in surface pixels`() {
        // the bar at 3x: blur 11dp -> 33px, band 10dp -> 30px, depth 5dp -> 15px, flat in the middle
        val barProfile = numbers(bar)
        assertEquals(33f, barProfile.blurRadiusPx, 0.001f)
        assertEquals(30f, barProfile.lensBandPx, 0.001f)
        assertEquals(15f, barProfile.lensDepthPx, 0.001f)
        assertEquals(1f, barProfile.magnify, 0f)

        // the travelling lens is CLEARER than the bar it sits on, bends less
        // and magnifies a little — a droplet, not a stripe
        val lensProfile = numbers(lens)
        assertEquals(21f, lensProfile.blurRadiusPx, 0.001f)
        assertEquals(18f, lensProfile.lensBandPx, 0.001f)
        assertEquals(3f, lensProfile.lensDepthPx, 0.001f)
        assertEquals(1.10f, lensProfile.magnify, 0f)
        assertTrue(lensProfile.blurRadiusPx < barProfile.blurRadiusPx)
        assertTrue(lensProfile.lensDepthPx < barProfile.lensDepthPx)
        assertTrue(lensProfile.magnify > barProfile.magnify)

        val fabProfile = numbers(fab)
        assertEquals(24f, fabProfile.blurRadiusPx, 0.001f)
        assertEquals(18f, fabProfile.lensBandPx, 0.001f)
        assertEquals(9f, fabProfile.lensDepthPx, 0.001f)

        // the margin is the same arithmetic for every kind
        for (pane in panes) {
            val p = numbers(pane)
            assertEquals(LiquidGlass.backdropMarginPx(p.blurRadiusPx, LiquidGlass.MARGIN_AIR_DP * density), p.marginPx, 0.001f)
        }
    }

    @Test
    fun `the pane's effect layer covers the pane plus its margins`() {
        for (pane in panes) {
            val p = numbers(pane)
            val width = p.layerWidth(2f * pane.halfWidth)
            assertEquals(ceil(2f * pane.halfWidth + 2f * p.marginPx).toInt(), width)
            assertTrue(width > 2f * pane.halfWidth)
        }
        // a pane smaller than its own margins still keeps them
        val tiny = numbers(bar)
        assertEquals(ceil(0.5f + 2f * tiny.marginPx).toInt(), tiny.layerWidth(0.5f))
        assertEquals(ceil(0.5f + 2f * tiny.marginPx).toInt(), tiny.layerHeight(0.5f))
    }

    @Test
    fun `every pane shows only the page that lies under it`() {
        // the bottom-of-the-menu bug: beta.13's rim band sampled up to 46px
        // BEYOND the bar's bottom rim — the gap and the navigation strip.
        // On a 1px grid of each pane's footprint, the sample must stay inside.
        for (pane in panes) {
            val p = numbers(pane)
            var tested = 0
            var deepest = 0f
            var y = -pane.halfHeight + 1f
            while (y < pane.halfHeight) {
                var x = -pane.halfWidth + 1f
                while (x < pane.halfWidth) {
                    if (paneDistance(x, y, pane.halfWidth, pane.halfHeight, pane.corner) <= 0f) {
                        val s = lensSample(x, y, pane.halfWidth, pane.halfHeight, pane.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
                        val outside = paneDistance(s.x, s.y, pane.halfWidth, pane.halfHeight, pane.corner)
                        deepest = max(deepest, outside)
                        tested++
                    }
                    x += 1f
                }
                y += 1f
            }
            assertTrue("${pane.kind}: the grid was not walked", tested > 1000)
            assertTrue("${pane.kind}: a pixel shows the page ${deepest}px beyond the rim", deepest <= 0.001f)
        }
    }

    @Test
    fun `the mapping never folds`() {
        // a fold is a mirrored strip (the Jacobian determinant goes to zero
        // and negative): the middle of a rim band, where the bevel is
        // steepest, is where a lens that is too deep would fold first
        for (pane in panes) {
            val p = numbers(pane)
            var smallest = Float.MAX_VALUE
            var y = -pane.halfHeight + 1f
            while (y < pane.halfHeight) {
                var x = -pane.halfWidth + 1f
                while (x < pane.halfWidth) {
                    if (paneDistance(x, y, pane.halfWidth, pane.halfHeight, pane.corner) <= 0f) {
                        smallest = minOf(smallest, determinant(pane, x, y, p.lensBandPx, p.lensDepthPx, p.magnify))
                    }
                    x += 2f
                }
                y += 2f
            }
            assertTrue("${pane.kind}: the mapping folds (determinant $smallest)", smallest > 0f)
        }
    }

    @Test
    fun `the fold-free rule holds every pane and catches a deeper one`() {
        for (kind in LiquidGlassKind.entries) {
            assertTrue(
                "${kind.name}: depth ${kind.depthDp}dp is not below two thirds of its band ${kind.bandDp}dp",
                kind.depthDp < kind.bandDp * LiquidGlass.FOLD_FREE_RATIO,
            )
        }
        // the same bar one third deeper than the rule allows folds at its
        // bottom rim: the control proves the rule (and the test) can fail
        val band = bar.kind.bandDp * density
        val tooDeep = band * LiquidGlass.FOLD_FREE_RATIO + 2f
        var smallest = Float.MAX_VALUE
        var y = bar.halfHeight - band - 1f
        while (y < bar.halfHeight) {
            smallest = minOf(smallest, determinant(bar, 0f, y, band, tooDeep, 1f))
            y += 0.25f
        }
        assertTrue("a depth of ${tooDeep}px over a ${band}px band should fold somewhere, smallest determinant $smallest", smallest <= 0f)
    }

    @Test
    fun `the rim shows the page from a little inside it`() {
        val p = numbers(bar)
        // one pixel inside the bar's bottom rim lies 15px of page inside the rim
        val atRim = lensSample(0f, bar.halfHeight - 1f, bar.halfWidth, bar.halfHeight, bar.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
        assertEquals(16.0f, bar.halfHeight - atRim.y, 0.1f)
        // one band in, the bevel is over: the pane lies flat and shows what is under it
        val inside = lensSample(0f, bar.halfHeight - p.lensBandPx, bar.halfWidth, bar.halfHeight, bar.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
        assertEquals(bar.halfHeight - p.lensBandPx, inside.y, 0.001f)
        // and at the top rim the same, mirrored: a real bevel is symmetric
        val atTop = lensSample(0f, -bar.halfHeight + 1f, bar.halfWidth, bar.halfHeight, bar.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
        assertEquals(-atRim.y, atTop.y, 0.001f)
    }

    @Test
    fun `the lens magnifies about its centre and lies flat on its rim`() {
        val p = numbers(lens)
        // 36px below the centre — one band in from the bottom rim
        val middle = lensSample(0f, 36f, lens.halfWidth, lens.halfHeight, lens.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
        assertEquals(36f / p.magnify, middle.y, 0.01f)
        assertEquals(0f, middle.x, 0f)
        // on the rim itself the magnification is one: nothing slides at the edge
        val rim = lensSample(0f, lens.halfHeight - 0.001f, lens.halfWidth, lens.halfHeight, lens.corner, p.lensBandPx, p.lensDepthPx, p.magnify)
        assertTrue(rim.y < lens.halfHeight)
        assertTrue(lens.halfHeight - rim.y <= p.lensDepthPx + 0.01f)
    }

    @Test
    fun `the dark scheme lights its rims more than the light one`() {
        val light = numbers(bar, dark = false)
        val dark = numbers(bar, dark = true)
        // beta.13 had NO rim light in the light scheme (near-white glass
        // cannot be lit whiter) — and its edge refraction was invisible.
        // Over glass that lets the page through, both schemes show an edge.
        assertTrue(light.rimLight > 0f)
        assertTrue(light.rimShade > 0f)
        assertTrue(dark.rimLight > light.rimLight)
        assertTrue(dark.rimShade > light.rimShade)
        assertTrue(dark.specular > light.specular)
        assertTrue(light.dispersion > 0f)
        // the lens is the brightest, most refractive pane in both schemes
        assertTrue(numbers(lens).rimLight > light.rimLight)
        assertTrue(numbers(lens, dark = true).specular > dark.specular)
    }

    @Test
    fun `the washes stay low so the page carries the colour`() {
        // beta.13: the bar drew 74% white over its frosted copy and the lens
        // another 50% plus a tint — white and lifeless.
        assertTrue(LightLiquidGlass.paneTint.alpha <= 0.25f)
        assertTrue(LightLiquidGlass.lensBase.alpha <= 0.08f)
        assertTrue(LightLiquidGlass.sheen.alpha <= 0.10f)
        assertTrue(DarkLiquidGlass.paneTint.alpha <= 0.52f)
        assertTrue(DarkLiquidGlass.lensBase.alpha <= 0.12f)
        // the lens is clearer than the bar it sits on
        assertTrue(LightLiquidGlass.lensBase.alpha < LightLiquidGlass.paneTint.alpha)
        assertTrue(DarkLiquidGlass.lensBase.alpha < DarkLiquidGlass.paneTint.alpha)
        // its rim glints on top and quiets at the bottom
        assertTrue(LightLiquidGlass.lensRimTop.alpha > LightLiquidGlass.lensRimBottom.alpha)
        assertTrue(DarkLiquidGlass.lensRimTop.alpha > DarkLiquidGlass.lensRimBottom.alpha)
    }

    @Test
    fun `the lens rests at its own size and ignores a NaN flight`() {
        assertEquals(1f, LiquidGlass.lensStretch(0f), 0f)
        assertEquals(1f, LiquidGlass.lensStretch(Float.NaN), 0f)
        assertEquals(1f, LiquidGlass.lensSquash(1f), 0f)
    }

    @Test
    fun `the lens stretches with distance, the same in both directions`() {
        val dead = LiquidGlass.STRETCH_DEAD_ZONE_SLOTS
        assertEquals(1f, LiquidGlass.lensStretch(dead), 0f)
        assertEquals(1f, LiquidGlass.lensStretch(dead / 2f), 0f)
        assertEquals(LiquidGlass.lensStretch(0.8f), LiquidGlass.lensStretch(-0.8f), 0f)
        assertTrue(LiquidGlass.lensStretch(0.4f) < LiquidGlass.lensStretch(0.8f))
        val full = 1f + LiquidGlass.LENS_STRETCH
        assertEquals(full, LiquidGlass.lensStretch(LiquidGlass.STRETCH_FULL_DISTANCE_SLOTS), 0.0001f)
        assertEquals(full, LiquidGlass.lensStretch(5f), 0.0001f)
    }

    @Test
    fun `a stretched lens gives height back but keeps its volume`() {
        val stretch = 1f + LiquidGlass.LENS_STRETCH
        val squash = LiquidGlass.lensSquash(stretch)
        assertTrue(squash < 1f)
        assertTrue(stretch * squash >= 1f)
        assertEquals(1f - LiquidGlass.LENS_SQUASH * LiquidGlass.LENS_STRETCH, squash, 0.0001f)
    }

    @Test
    fun `the shipped lens shader is the one the numbers mirror`() {
        assertTrue(LIQUID_LENS_SKSL.contains("uniform shader content;"))
        assertTrue(LIQUID_LENS_SKSL.contains("half4 main(float2 p)"))
        val uniforms = listOf(
            "paneLeft", "paneTop", "paneRight", "paneBottom", "cornerRadius",
            "lensBand", "lensDepth", "magnify", "dispersion", "rimLight", "rimShade",
            "specular", "saturation",
        )
        for (uniform in uniforms) {
            assertTrue(uniform, LIQUID_LENS_SKSL.contains("uniform float $uniform;"))
        }
        // the bevel pulls the sample INWARD (`p - n * bend`), never outward,
        // and the magnification is about the pane's centre; every sample is
        // completed through `frosted`, never a bare read of the layer
        assertTrue(LIQUID_LENS_SKSL.contains("frosted(c + (p - n * bend - c) / m, c, core, cornerRadius)"))
        assertTrue(LIQUID_LENS_SKSL.contains("float bevel = 1.0 - ease;"))
        assertTrue(LIQUID_LENS_SKSL.contains("float m = 1.0 + (magnify - 1.0) * ease;"))
        assertEquals(3, Regex("frosted\\(").findAll(LIQUID_LENS_SKSL).count() - 1)
        assertEquals(0, Regex("middle = content\\.eval|warm = content\\.eval|cool = content\\.eval").findAll(LIQUID_LENS_SKSL).count())
        // the frost at a rim is completed with the sample's mirror image across
        // the rim, as the second half of a mirror-padded blur
        assertTrue(LIQUID_LENS_SKSL.contains("float2 mirror = s - 2.0 * paneDistance(q, core, radius) * n;"))
        assertTrue(LIQUID_LENS_SKSL.contains("content.eval(s) + content.eval(mirror)"))
        assertTrue(LIQUID_LENS_SKSL.contains("sum.rgb / max(sum.a, 0.0001)"))
        // the vibrancy rides in this pass, not in a colour filter of its own
        assertTrue(LIQUID_LENS_SKSL.contains("* saturation;"))
    }

    @Test
    fun `the mirror of a point lies as far outside the rim as the point lies inside`() {
        // on every straight edge and round the corners of every pane
        for (pane in panes) {
            var tested = 0
            var worst = 0f
            var t = -1f
            while (t <= 1.001f) {
                // just inside the bottom edge, from corner centre to corner centre
                val x = t * (pane.halfWidth - pane.corner)
                for (depth in listOf(1f, 6f, 18f)) {
                    val y = pane.halfHeight - depth
                    val m = paneMirror(x, y, pane.halfWidth, pane.halfHeight, pane.corner)
                    val outside = paneDistance(m.x, m.y, pane.halfWidth, pane.halfHeight, pane.corner)
                    worst = max(worst, abs(outside - depth))
                    assertEquals(x, m.x, 0.001f)
                    tested++
                }
                t += 0.125f
            }
            assertTrue("${pane.kind}: nothing was walked", tested >= 40)
            assertTrue("${pane.kind}: the mirror is off by ${worst}px", worst < 0.01f)
        }
    }

    @Test
    fun `a point of the straight middle is its own mirror`() {
        // nothing of the blur is missing there, so nothing is added. The bar
        // is a pill: its corner radius is its half height, so its straight
        // middle is the single line through the centre
        val middle = paneMirror(30f, 0f, bar.halfWidth, bar.halfHeight, bar.corner)
        assertEquals(30f, middle.x, 0.001f)
        assertEquals(0f, middle.y, 0.001f)
        // one pixel inside the bar's bottom edge: 1px outside it (99 + 1)
        val edge = paneMirror(0f, bar.halfHeight - 1f, bar.halfWidth, bar.halfHeight, bar.corner)
        assertEquals(bar.halfHeight + 1f, edge.y, 0.001f)
    }

    @Test
    fun `the mirror round a corner stays on the line through the corner's centre`() {
        // a rounded rim is reflected across the tangent at its nearest point:
        // the mirror keeps the corner's centre, the point and itself in line
        for (pane in panes.filter { it.corner > 0f }) {
            val c = pane.corner
            // a point 8px inside the bottom-right arc, 45 degrees round it
            val r = c - 8f
            val cx = pane.halfWidth - c + r * 0.70710678f
            val cy = pane.halfHeight - c + r * 0.70710678f
            val m = paneMirror(cx, cy, pane.halfWidth, pane.halfHeight, pane.corner)
            val ox = pane.halfWidth - c
            val oy = pane.halfHeight - c
            val cross = (cx - ox) * (m.y - oy) - (cy - oy) * (m.x - ox)
            assertEquals(0f, cross, 0.01f)
            assertTrue(hypot(m.x - ox, m.y - oy) > hypot(cx - ox, cy - oy))
        }
    }

    @Test
    fun `the layer's margin holds the whole fade the mirror reads`() {
        // the blur is incomplete within three sigmas of a rim; that far in,
        // the mirror is that far out, and the margin must still cover it —
        // a clamped layer would answer with the rim itself
        for (pane in panes) {
            val p = numbers(pane)
            val sigma = LiquidGlass.blurSigmaPx(p.blurRadiusPx)
            val depth = LiquidGlass.BLUR_SIGMA_MARGIN * sigma
            val m = paneMirror(0f, pane.halfHeight - depth, pane.halfWidth, pane.halfHeight, pane.corner)
            assertTrue("${pane.kind}: the mirror falls outside the layer", m.y <= pane.halfHeight + p.marginPx + 0.001f)
            assertTrue("${pane.kind}: no room left for the fade", pane.halfHeight + p.marginPx - m.y >= LiquidGlass.MARGIN_AIR_DP * density - 0.001f)
        }
    }
}
