package com.turnypt.app.ui.theme

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6.0-beta.13: the pure numbers behind the Liquid Glass MATERIAL — one
 * engine behind every glass surface in the app, not only the main menu
 * (see theme/LiquidGlass.kt).
 */
class LiquidGlassTest {

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
        // a 66px radius (22dp at 3x): 0.57735 * 66 + 0.5
        assertEquals(38.6051f, LiquidGlass.blurSigmaPx(66f), 0.001f)
    }

    @Test
    fun `the backdrop margin keeps three sigmas plus the lens reach`() {
        val radius = 42f // 14dp at 3x
        val sigma = LiquidGlass.blurSigmaPx(radius)
        val margin = LiquidGlass.backdropMarginPx(radius, lensDepthPx = 54f, extraPx = 12f)
        assertEquals(3f * sigma + 54f + 12f, margin, 0.001f)
        assertTrue(LiquidGlass.backdropMarginPx(radius, 0f, 0f) >= 3f * sigma)
        assertTrue(LiquidGlass.backdropMarginPx(radius + 10f, 54f, 12f) > margin)
    }

    @Test
    fun `the profile resolves every pane kind in surface pixels`() {
        // the bar at 3x: blur 14dp -> 42px, band 24dp -> 72px, depth 18dp -> 54px
        val bar = LiquidGlass.profile(LiquidGlassKind.BAR, density = 3f, dark = false)
        assertEquals(42f, bar.blurRadiusPx, 0.001f)
        assertEquals(72f, bar.lensBandPx, 0.001f)
        assertEquals(54f, bar.lensDepthPx, 0.001f)
        assertEquals(1.05f, bar.magnify, 0f)
        assertEquals(1f, bar.scale, 0f) // the bar never renders at half resolution
        assertTrue(bar.bends)

        // a CARD at 3x renders at half resolution on dense screens…
        val card = LiquidGlass.profile(LiquidGlassKind.CARD, density = 3f, dark = true)
        assertEquals(0.5f, card.scale, 0f)
        // …but not below the density threshold
        assertEquals(1f, LiquidGlass.profile(LiquidGlassKind.CARD, density = 2f, dark = true).scale, 0f)

        // the lens is CLEARER than the bar it sits on (beta.12 bug B: the
        // stripe read as a hard shape because everything was one blur)
        val lens = LiquidGlass.profile(LiquidGlassKind.LENS, density = 3f, dark = false)
        assertTrue(lens.blurRadiusPx < bar.blurRadiusPx)
        assertTrue(lens.magnify > bar.magnify)

        // the margin is the same arithmetic for every kind
        assertEquals(
            LiquidGlass.backdropMarginPx(card.blurRadiusPx, card.lensDepthPx, LiquidGlass.MARGIN_AIR_DP * 3f),
            card.marginPx,
            0.001f,
        )
    }

    @Test
    fun `the pane's effect layer covers the pane plus its margins`() {
        val card = LiquidGlass.profile(LiquidGlassKind.CARD, density = 3f, dark = false)
        // scale 0.5: ceil((300 + 2*margin) * 0.5) — ceil, so the layer is
        // never a pixel short of the pane plus its margins
        val w = card.layerWidth(300f)
        assertEquals(ceil((300f + 2f * card.marginPx) * 0.5f).toInt(), w)
        assertTrue(w > 150) // the margin is really in the layer
        // a pane smaller than its own margins still keeps them: the layer
        // is never smaller than the margin-scaled content on either side
        val tiny = LiquidGlass.profile(LiquidGlassKind.BAR, 3f, false)
        assertEquals(ceil(0.5f + 2f * tiny.marginPx).toInt(), tiny.layerWidth(0.5f))
        assertEquals(ceil(0.5f + 2f * tiny.marginPx).toInt(), tiny.layerHeight(0.5f))
    }

    @Test
    fun `the dark scheme lights its rims more than the light one`() {
        val light = LiquidGlass.profile(LiquidGlassKind.BAR, density = 3f, dark = false)
        val dark = LiquidGlass.profile(LiquidGlassKind.BAR, density = 3f, dark = true)
        // near-white glass cannot be lit whiter; OLED black GLOWS instead
        assertEquals(LiquidGlass.LIGHT_RIM_LIGHT, light.rimLight, 0f)
        assertTrue(dark.rimLight > light.rimLight)
        assertTrue(dark.rimShade > light.rimShade)
        assertTrue(dark.specular > light.specular)
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
    fun `the shipped lens shader is the one the preview renders`() {
        assertTrue(LIQUID_LENS_SKSL.contains("uniform shader content;"))
        assertTrue(LIQUID_LENS_SKSL.contains("half4 main(float2 p)"))
        val uniforms = listOf(
            "paneLeft", "paneTop", "paneRight", "paneBottom", "cornerRadius",
            "lensBand", "lensDepth", "magnify", "dispersion", "rimLight", "rimShade",
            "specular",
        )
        for (uniform in uniforms) {
            assertTrue(uniform, LIQUID_LENS_SKSL.contains("uniform float $uniform;"))
        }
        // every uniform the engine sets must exist in the shader text
        val sksl = LIQUID_LENS_SKSL
        for (name in listOf("content", "paneLeft", "specular")) {
            assertTrue(name, sksl.contains(name))
        }
    }
}
