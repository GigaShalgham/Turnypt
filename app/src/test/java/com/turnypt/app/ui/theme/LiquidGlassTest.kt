package com.turnypt.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.6.0-beta.11: the pure numbers behind the Liquid Glass main menu. */
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
    fun `the tint thins toward the rim but reaches its centre alpha`() {
        val centre = 0.6f
        val base = LiquidGlass.tintBaseAlpha(centre)
        val body = LiquidGlass.tintBodyAlpha(centre)
        // two washes composited: 1 - (1 - base) * (1 - body)
        assertEquals(centre, 1f - (1f - base) * (1f - body), 0.0001f)
        assertTrue(base < centre)
        assertEquals(0f, LiquidGlass.tintBaseAlpha(0f), 0f)
        assertEquals(0f, LiquidGlass.tintBodyAlpha(0f), 0f)
        // the base always carries only its share of the centre alpha…
        assertEquals(LiquidGlass.TINT_BASE_SHARE, LiquidGlass.tintBaseAlpha(1f), 0f)
        // …and the blurred body tops the centre up to exactly full strength
        assertEquals(1f, LiquidGlass.tintBodyAlpha(1f), 0f)
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
        )
        for (uniform in uniforms) {
            assertTrue(uniform, LIQUID_LENS_SKSL.contains("uniform float $uniform;"))
        }
    }
}
