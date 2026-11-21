package com.turnypt.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * v1.6.0-beta.1 — GLASS IS GONE, BY USER DECREE ("بازگردید به طراحی
 * اصلی، بدون هیچ شیشه‌ای، و هر تأخیری که به همراه داشت را حذف کنید").
 *
 * The whole v1.4.0 "Liquid Glass" language — translucent tints,
 * refraction rims, specular bevels, the per-frame glassCapture /
 * glassBackdropPane RenderEffect pipeline, the frost washes — is
 * deleted. What the app returns to is the pre-glass v1.3.2 Material
 * design: Scaffold + NavigationBar, plain Surface containers, opaque
 * colour roles. No capture layers, no blur, no translucency, no
 * readbacks — the frame cost of the glass pipeline is gone with it.
 *
 * This file survives ONLY as the corner-radius system, because the
 * radius family (12/16/20/28 + pill) predates the glass pass (v0.5
 * shapes were 8/12/16/24/32; v1.4.0 renumbered them) and a hundred
 * call sites read their shapes from here. Names keep the `GlassRadii`
 * spelling so the diff stays mechanical; the "glass" in the name is
 * historical, like the project's old CipherKey name.
 */
object GlassRadii {
    val chip = 12.dp
    val field = 16.dp
    val card = 20.dp
    val sheet = 28.dp
    val pill = 9999.dp

    // v1.5.10 (lag audit): the Shape objects for the fixed radii,
    // built ONCE. Call sites used to allocate a fresh
    // RoundedCornerShape on every recomposition — ContactCard alone
    // did it per contact per recomposition. A corner-radius shape with
    // no corner overrides equals by value, so caching the object
    // changes NOTHING visually; only the allocation traffic is gone.
    // `pill` keeps the 9999.dp form (a stadium is what a huge radius
    // renders as).
    val chipShape: RoundedCornerShape = RoundedCornerShape(chip)
    val fieldShape: RoundedCornerShape = RoundedCornerShape(field)
    val cardShape: RoundedCornerShape = RoundedCornerShape(card)
    val sheetShape: RoundedCornerShape = RoundedCornerShape(sheet)
    val pillShape: RoundedCornerShape = RoundedCornerShape(pill)
}
