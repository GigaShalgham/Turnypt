package com.turnypt.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.LocalLiquidGlassScene
import com.turnypt.app.ui.theme.LiquidGlassColors
import com.turnypt.app.ui.theme.TurnyptTheme
import com.turnypt.app.ui.theme.glassBackdrop
import com.turnypt.app.ui.theme.liquidGlassField
import com.turnypt.app.ui.theme.liquidGlassSource
import com.turnypt.app.ui.theme.rememberLiquidGlassScene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * v1.6.0-beta.14: the desktop render harness.
 *
 * Draws the REAL composables through Robolectric's native graphics (the real
 * hwui + Skia, not a hand-written imitation) and writes PNGs, so the glass is
 * judged on pixels before it ships. beta.13 was released on three numbers
 * (lens centre, row jumps, label contrast) and none of them could see a white
 * matte pane or a rim that swallowed 24dp of the bar — so the frames are now
 * built to answer questions, not just to exist:
 *
 *  - CONTENT / CARDS pages: real text, stripes and glass cards under and
 *    around the bar — transmission, whiteness, the card material, frame time;
 *  - RAMP pages: every pixel carries its own position as the phase of a
 *    sine (one code left of a split, its cosine twin right of it). A pane
 *    pixel is decoded together with a partner pixel of the other code that
 *    shows the SAME page position; what the pane laid over the two (tint,
 *    sheen, shade, rim light — one affine map) cancels in the difference,
 *    so nothing about the pane's colours has to be known. Decoding gives the
 *    exact page pixel a pane pixel shows — the refraction mapping, any
 *    content pulled in from OUTSIDE a pane, measured instead of argued;
 *  - MARKERS pages: a flat grey field with two probe lines, one just inside
 *    the bar's bottom rim and one in the 12dp gap BELOW the bar. The lower
 *    line is BLUE — the only blue on the page — so a pane that samples
 *    beyond its rim draws it and is caught by colour, whatever its own tint
 *    and light (the bar's selected icon is violet, red above green, which
 *    the probe never shows): [assertNoPageFromBeyondTheRim] fails the test
 *    on one pixel;
 *  - TINT pages: flat, grey on the left half and teal on the right. Over a
 *    flat page a pane's colour is (1 - a) * page + a * wash, so the share of
 *    the pane's own wash is read straight off the pixels:
 *    [assertThePageShowsThrough] holds the bar and the lens to it;
 *  - a scrolled frame: the page moved under panes that did not;
 *  - flights: the selection lens captured mid-hop, stretched, between slots;
 *  - PERF frames: the same window redrawn with the page moved by 3px, timed.
 *
 * Opt-in only — see the lgRender block in app/build.gradle.kts:
 *
 *   ./gradlew :app:testDebugUnitTest -PlgRender --tests "*LiquidGlassRenderTest*"
 *
 * Output: -PlgRenderOut=<dir> (default /tmp/lg-render); -PlgRenderOnly=a,b
 * keeps named frames (a trailing `*` is a prefix); -PlgRenderGroup=
 * stills,flight,perf keeps whole groups; -PlgRenderPrefix names the files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w360dp-h760dp-xxhdpi",
    application = Application::class,
    instrumentedPackages = ["androidx.loader.content"],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** The compose root view, captured from the composition for the screenshot. */
    private var rootView: View? = null

    /** One still frame. */
    private data class Shot(
        val name: String,
        val dark: Boolean = false,
        val rtl: Boolean = false,
        val page: Page = Page.Content,
        val selected: Int = 2,
        val scrollPx: Int = 0,
    )

    /** The selection lens hopping [from] -> [to], captured while it flies. */
    private data class Flight(
        val name: String,
        val dark: Boolean,
        val rtl: Boolean,
        val from: Int,
        val to: Int,
    )

    /** What the composition currently shows. */
    private data class Stage(
        val dark: Boolean = false,
        val rtl: Boolean = false,
        val page: Page = Page.Content,
        val selected: Int = 2,
        val scrollPx: Int = 0,
    )

    private val stills = listOf(
        Shot("light"),
        Shot("dark", dark = true),
        Shot("dark_rtl", dark = true, rtl = true, selected = 4),
        Shot("light_rtl", rtl = true, selected = 0),
        Shot("cards_light", page = Page.Cards),
        Shot("cards_dark", dark = true, page = Page.Cards),
        // the mapping probes: slot 2 is selected, so its lens sits astride
        // the split of a ramp page, a strip of each code beside its icon
        Shot("rampy_light", page = Page.RampY),
        Shot("rampy_dark", dark = true, page = Page.RampY),
        Shot("rampy_light_rtl", rtl = true, page = Page.RampY),
        Shot("rampy_dark_rtl", dark = true, rtl = true, page = Page.RampY),
        Shot("rampx_light", page = Page.RampX),
        Shot("rampx_dark", dark = true, page = Page.RampX),
        Shot("rampx_light_rtl", rtl = true, page = Page.RampX),
        Shot("rampx_dark_rtl", dark = true, rtl = true, page = Page.RampX),
        Shot("scroll_rampy_light", page = Page.RampY, scrollPx = 137),
        Shot("scroll_rampx_dark", dark = true, page = Page.RampX, scrollPx = 137),
        Shot("marker_light", page = Page.Markers),
        Shot("marker_dark", dark = true, page = Page.Markers),
        Shot("scroll_marker_light", page = Page.Markers, scrollPx = 137),
        // the bar over a flat page: how much of the page its own wash lets through
        Shot("tint_light", page = Page.Tint),
        Shot("tint_dark", dark = true, page = Page.Tint),
    )

    private val flights = listOf(
        Flight("flight_light", dark = false, rtl = false, from = 0, to = 4),
        Flight("flight_dark_rtl", dark = true, rtl = true, from = 4, to = 0),
    )

    /** Milliseconds into the hop at which the lens is captured. */
    private val flightTimesMs = longArrayOf(60, 140, 260)

    private val perfPages = listOf(
        "perf_content_light" to Page.Content,
        "perf_cards_light" to Page.Cards,
    )

    @Test
    fun rendersTheBarAndTheSurfaces() {
        val out = File(System.getProperty("turnypt.lgRenderOut") ?: "/tmp/lg-render")
        check(out.isDirectory || out.mkdirs()) { "cannot create $out" }
        val only = (System.getProperty("turnypt.lgRenderOnly") ?: "")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val groups = (System.getProperty("turnypt.lgRenderGroup") ?: "stills,flight,perf")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val prefix = System.getProperty("turnypt.lgRenderPrefix") ?: "render"
        boundsFile = File(out, "${prefix}_bounds.txt")
        boundsFile?.delete()

        fun wanted(name: String): Boolean =
            only.isEmpty() || only.any { it.endsWith("*") && name.startsWith(it.dropLast(1)) || it == name }

        var stage by mutableStateOf(Stage())
        // the test clock drives every animation: a flight can be frozen
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view }
            val s = stage
            CompositionLocalProvider(
                LocalLayoutDirection provides if (s.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                TurnyptTheme(darkTheme = s.dark) {
                    LiquidGlassHarness(
                        selected = s.selected,
                        scrollPx = s.scrollPx,
                        page = s.page,
                        dark = s.dark,
                        rtl = s.rtl,
                    )
                }
            }
        }

        if ("stills" in groups) {
            for (shot in stills) {
                if (!wanted(shot.name)) continue
                stage = Stage(shot.dark, shot.rtl, shot.page, shot.selected, shot.scrollPx)
                compose.waitForIdle()
                settle()
                val bitmap = capture(File(out, "${prefix}_${shot.name}.png"))
                // two complaints about beta.13, as failing tests on the frames built for them
                // (once the page has scrolled, the gap line lies under the bar by design)
                if (shot.page == Page.Markers && shot.scrollPx == 0) {
                    assertNoPageFromBeyondTheRim(bitmap, shot.dark, shot.name)
                }
                if (shot.page == Page.Tint) assertThePageShowsThrough(bitmap, shot.dark, shot.name)
            }
        }

        if ("flight" in groups) {
            for (flight in flights) {
                if (!wanted(flight.name)) continue
                // start from a lens at rest on [from]
                stage = Stage(flight.dark, flight.rtl, Page.Markers, flight.from, 0)
                compose.waitForIdle()
                settle()
                stage = stage.copy(selected = flight.to)
                compose.waitForIdle()
                var elapsed = 0L
                flightTimesMs.forEachIndexed { i, at ->
                    compose.mainClock.advanceTimeBy(at - elapsed)
                    elapsed = at
                    compose.waitForIdle()
                    val bitmap = capture(File(out, "${prefix}_${flight.name}_${'a' + i}.png"))
                    // a lens in flight bends most: it must still draw nothing from beyond a rim
                    assertNoPageFromBeyondTheRim(bitmap, flight.dark, "${flight.name}_${'a' + i}")
                }
            }
        }

        if ("perf" in groups) {
            for ((name, page) in perfPages) {
                if (!wanted(name)) continue
                // the first frame pays for shaders and layers: it is the
                // warm-up, and the two after it are the measurement
                stage = Stage(page = page, scrollPx = 0)
                compose.waitForIdle()
                settle()
                capture(File(out, "${prefix}_${name}_t0.png"))
                for (i in 1..2) {
                    stage = stage.copy(scrollPx = i * 3)
                    compose.waitForIdle()
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    capture(File(out, "${prefix}_${name}_t$i.png"))
                }
            }
        }
    }

    /** Lets every animation of the current stage run to its rest. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    /**
     * The window as pixels. Compose's own `captureToImage()` needs
     * `PixelCopy` to call back from a compositor this sandbox does not
     * run, so the root view is rendered directly: first through
     * Robolectric's REAL hardware pipeline (a `HardwareRenderer` drawing
     * the view's render node into an image — RenderEffects, runtime
     * shaders and shadows are the genuine libhwui code), and, if that is
     * unavailable, through a software canvas.
     */
    private fun capture(file: File): Bitmap {
        val view = checkNotNull(rootView) { "the composition never reported its view" }
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val started = System.nanoTime()
        val mode = hardwareScreenshot(view, bitmap)
        if (mode == null) {
            view.draw(Canvas(bitmap))
        }
        val millis = (System.nanoTime() - started) / 1e6
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("LG_RENDER ${file.name} ${bitmap.width}x${bitmap.height} ${mode ?: "software"}")
        println("LG_RENDER_MS ${file.name} ${"%.1f".format(millis)}")
        return bitmap
    }

    /** How far blue must stand above red for a pixel to be the blue probe line: the page itself is never blue. */
    private val leakBlue = 30

    /**
     * The probe is pure blue on a grey page, so wherever it shows, red equals
     * green (within dispersion and rounding). The one blue-ish thing drawn ON
     * the bar is its selected icon, a violet with red some 40 levels above
     * green: beta.14's clear lens lets it through unchanged, and it is not
     * the probe.
     */
    private val probeViolet = 8

    /**
     * The bottom-of-the-menu bug as a failing test. The probe line below the
     * bar is the only BLUE on its page, so a pane that draws it is caught by
     * colour, whatever its own tint and light: not one blue pixel may lie
     * inside the bar — every pixel of it is read, rims and lens included.
     */
    private fun assertNoPageFromBeyondTheRim(bitmap: Bitmap, dark: Boolean, name: String) {
        val bar = checkNotNull(boundsNow["bar"]) { "the bar never reported its bounds" }
        val left = bar[0]
        val top = bar[1]
        val width = bar[2]
        val height = bar[3]
        val line = IntArray(width)
        var blue = 0
        var strongest = 0
        // 5px in from the rim: its own hairline (violet on the dark bar) is not the page
        for (y in top + 5 until top + height - 5) {
            bitmap.getPixels(line, 0, width, left, y, width, 1)
            for (p in line) {
                val far = android.graphics.Color.blue(p) - android.graphics.Color.red(p)
                strongest = maxOf(strongest, far)
                if (far >= leakBlue && android.graphics.Color.red(p) - android.graphics.Color.green(p) <= probeViolet) blue++
            }
        }
        check(blue == 0) {
            "$name: $blue pixels inside the bar are the BLUE probe line from below it " +
                "(blue minus red up to $strongest) — a pane samples beyond its rim"
        }
    }

    /**
     * "White and lifeless" as a failing test. Over a flat page a pane's
     * colour is (1 - a) * page + a * wash, so the share of the pane's own
     * wash is read straight off the pixels; the light bar must keep it under
     * a third (beta.13 drew 74% white), the lens under a quarter, and the
     * dark bar — glass that is tinted by design — under two thirds.
     */
    private fun assertThePageShowsThrough(bitmap: Bitmap, dark: Boolean, name: String) {
        val bar = checkNotNull(boundsNow["bar"]) { "the bar never reported its bounds" }
        val colours = checkNotNull(glassColorsNow) { "the harness never saw the glass's colours" }
        val left = bar[0]
        val top = bar[1]
        val width = bar[2]
        val height = bar[3]
        // below the sheen (gone by 55%) and above the inner shade (starts at 74%)
        val rows = top + (height * .56f).roundToInt() until top + (height * .73f).roundToInt()
        val dp = with(compose.density) { 1.dp.toPx() }
        val slot = (width - 12f * dp) / 6f
        val slotBoundary = { k: Int -> left + 6f * dp + k * slot }
        val greyPage = IntArray(24) { 12 + it }
        val page = meanColour(bitmap, top - 300 until top - 200, greyPage)
        val washShare = { pane: FloatArray, wash: Color ->
            (0..2).map { c -> (pane[c] - page[c]) / (255f * floatArrayOf(wash.red, wash.green, wash.blue)[c] - page[c]) }
                .average().toFloat()
        }
        val barColumns = (1..2).flatMap { k -> (-8..8).map { (slotBoundary(k) + it).roundToInt() } }.toIntArray()
        val barShare = washShare(meanColour(bitmap, rows, barColumns), colours.paneTint)
        // the lens of slot 2 (all on the grey half): its middle, past the
        // bevel, with the icon drawn on it left out
        val lensLeft = (slotBoundary(2) + 3f * dp).roundToInt()
        val lensRight = (slotBoundary(3) - 3f * dp).roundToInt()
        val lensTop = top + (9f * dp).roundToInt()
        val lensBottom = top + height - (9f * dp).roundToInt()
        val inset = 22
        val lensShare = washShare(
            glassColour(bitmap, lensLeft + inset, lensTop + inset, lensRight - inset, lensBottom - inset),
            colours.lensBase,
        )
        check(barShare <= if (dark) 0.66f else 0.33f) {
            "$name: the bar covers ${"%.0f".format(barShare * 100)}% of the page with its own wash"
        }
        check(lensShare <= 0.25f) {
            "$name: the selection lens covers ${"%.0f".format(lensShare * 100)}% of the page with its own wash"
        }
    }

    /**
     * Mean sRGB of a rectangle of the window with what is drawn ON the glass
     * left out: a pixel that is violet (red above green by [probeViolet] —
     * the selected icon) and every pixel within 2px of one.
     */
    private fun glassColour(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): FloatArray {
        val w = right - left
        val h = bottom - top
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, left, top, w, h)
        fun violet(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= w || y >= h) return false
            val p = px[y * w + x]
            return android.graphics.Color.red(p) - android.graphics.Color.green(p) >= probeViolet
        }
        val sum = FloatArray(3)
        var n = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var onGlyph = false
                loop@ for (dy in -2..2) {
                    for (dx in -2..2) {
                        if (violet(x + dx, y + dy)) {
                            onGlyph = true
                            break@loop
                        }
                    }
                }
                if (onGlyph) continue
                val p = px[y * w + x]
                sum[0] += android.graphics.Color.red(p)
                sum[1] += android.graphics.Color.green(p)
                sum[2] += android.graphics.Color.blue(p)
                n++
            }
        }
        check(n > 200) { "only ${n}px of clear glass to read" }
        return FloatArray(3) { sum[it] / n }
    }

    /** Mean sRGB of the pixels at [columns] over [rows] of the window. */
    private fun meanColour(bitmap: Bitmap, rows: IntRange, columns: IntArray): FloatArray {
        val sum = FloatArray(3)
        val line = IntArray(bitmap.width)
        for (y in rows) {
            bitmap.getPixels(line, 0, line.size, 0, y, line.size, 1)
            for (c in columns) {
                sum[0] += android.graphics.Color.red(line[c])
                sum[1] += android.graphics.Color.green(line[c])
                sum[2] += android.graphics.Color.blue(line[c])
            }
        }
        val n = (rows.count() * columns.size).toFloat()
        return FloatArray(3) { sum[it] / n }
    }

    /** Returns a description of the hardware path used, or null if it failed. */
    private fun hardwareScreenshot(view: View, bitmap: Bitmap): String? {
        return try {
            val cls = Class.forName("org.robolectric.shadows.HardwareRenderingScreenshot")
            val can = cls.getDeclaredMethod("canTakeScreenshot", View::class.java).apply { isAccessible = true }
            val take = cls.getDeclaredMethod("takeScreenshot", View::class.java, Bitmap::class.java)
                .apply { isAccessible = true }
            val available = can.invoke(null, view) as Boolean
            take.invoke(null, view, bitmap)
            "hardware(canTake=$available)"
        } catch (t: Throwable) {
            println("LG_RENDER hardware capture failed: $t")
            null
        }
    }
}

/** What lies on the page, behind and around the bar. */
private enum class Page { Content, Cards, RampY, RampX, Markers, Tint }

/**
 * The page of sample content that runs UNDER and BELOW the bar (text lines,
 * colour blocks and a glass card), so the panes have something real to
 * frost and bend — the bar AND a card of the whole-design material in the
 * same render.
 */
@Composable
private fun ContentPage() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("page")
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        // a card of the same material (glassSurface) over the stripes:
        // the whole-design pane, checked in the same render
        SectionCard(
            title = "Liquid Glass card",
            modifier = Modifier.reportBounds("content_card"),
        ) {
            Text(
                "Frosted and bent by the same engine as the menu.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val stripes = listOf(
            Color(0xFFE040FB), Color(0xFF00E5FF), Color(0xFFFFC107), Color(0xFF69F0AE),
        )
        repeat(16) { i ->
            Text(
                "Line ${i + 1} — the quick brown fox jumps",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (i % 2 == 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(stripes[(i / 2) % stripes.size], RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

/** A settings-like page: twelve glass cards, most of them on screen at once. */
@Composable
private fun CardsPage() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .testTag("page")
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        repeat(12) { i ->
            SectionCard(
                title = "Card ${i + 1}",
                modifier = if (i == 0) Modifier.reportBounds("cards_card0") else Modifier,
            ) {
                Text(
                    "Setting ${i + 1} of 12 — a row of body text inside the glass.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * A page whose colours encode position along one axis, over the WHOLE screen:
 * decoding a pixel gives the page coordinate it was sampled from.
 *
 * The code is the phase of a sine with a period of a few dozen dp, because a
 * ramp over the whole page resolves a position only to ±10px once a pane has
 * tinted it (one 8-bit level per several pixels), while a phase resolves it
 * to about a pixel. Left of [RAMP_SPLIT] lies code A, red with the sine in
 * its green; right of it code B, blue with the cosine in its green. A pane
 * is decoded from a code A pixel and a code B pixel that show the SAME page
 * coordinate: along Y that is any two columns of one row, along X two
 * columns mirrored about the pane's centre (a pane magnifying about its
 * centre maps them symmetrically). The two pixels differ only by what the
 * pane laid over them, one affine map, which cancels; only the vibrancy's
 * saturation has to be undone explicitly. The period divides the bar's
 * width (X) so that the mirrored columns carry the same phase.
 *
 * [RAMP_SPLIT] cuts the lens of slot 2 through its middle, so the lens has a
 * strip of each code beside its icon. In RTL the whole page is mirrored
 * together with the slots.
 *
 * With [scrollPx] the page is a screen-full of code followed by blank field,
 * moved up (or left) by that much, the way a scrolled list is: the code's far
 * edge then crosses the bar, where it must land exactly where it is.
 */
@Composable
private fun RampPage(horizontal: Boolean, scrollPx: Int, rtl: Boolean) {
    val density = LocalDensity.current
    val scrollDp = with(density) { scrollPx.toDp() }
    val widthPx = with(density) { 360.dp.toPx() }
    val heightPx = with(density) { 760.dp.toPx() }
    val splitPx = with(density) { RAMP_SPLIT.toPx() }
    // 7 periods across the width, 9.5 down the height
    val length = if (horizontal) widthPx else heightPx
    val period = if (horizontal) widthPx / 7f else heightPx / 9.5f
    val codeA = remember(horizontal) {
        val stops = codeStops(codeA = true, length, period)
        if (horizontal) Brush.horizontalGradient(*stops, startX = 0f, endX = widthPx)
        else Brush.verticalGradient(*stops, startY = 0f, endY = heightPx)
    }
    // code B's box starts at the split: its local x = page x - split
    val codeB = remember(horizontal) {
        val stops = codeStops(codeA = false, length, period)
        if (horizontal) Brush.horizontalGradient(*stops, startX = -splitPx, endX = widthPx - splitPx)
        else Brush.verticalGradient(*stops, startY = 0f, endY = heightPx)
    }
    val codes = @Composable {
        Row {
            Box(Modifier.width(RAMP_SPLIT).fillMaxHeight().background(codeA))
            Box(Modifier.width(360.dp - RAMP_SPLIT).fillMaxHeight().background(codeB))
        }
    }
    val mirror = if (rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier
    if (horizontal) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .then(mirror)
                .graphicsLayer { translationX = -scrollPx.toFloat() },
        ) {
            Row(Modifier.width(360.dp).fillMaxHeight()) { codes() }
            Spacer(Modifier.width(scrollDp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(mirror)
                .graphicsLayer { translationY = -scrollPx.toFloat() },
        ) {
            Row(Modifier.fillMaxWidth().height(760.dp)) { codes() }
            Spacer(Modifier.height(scrollDp))
        }
    }
}

/**
 * Colour stops of one code along [length]: the fraction of the page paired
 * with its colour. Code A is (255, g, 0), code B (0, g, 255), g the sine
 * (A) or the cosine (B) of the position's phase, 48 stops to a period.
 */
private fun codeStops(codeA: Boolean, length: Float, period: Float): Array<Pair<Float, Color>> {
    val n = ceil(length / period).toInt() * 48
    return Array(n + 1) { i ->
        val phase = 2.0 * PI * (length * i / n) / period
        val g = (0.5 + 0.5 * (if (codeA) sin(phase) else cos(phase))).toFloat()
        val colour = if (codeA) Color(1f, g, 0f) else Color(0f, g, 1f)
        i.toFloat() / n to colour
    }
}

/** Where the two codes meet: the middle of slot 2's lens (153dp of the 360dp harness width). */
private val RAMP_SPLIT = 153.dp

/**
 * A flat, neutral page carrying the two probe lines. One line lies just
 * INSIDE the bar's bottom rim and must appear where it is; the other lies in
 * the 12dp gap BELOW the bar and must never appear inside the bar or the
 * lens. The lower line is BLUE and the page holds no other blue (the field
 * is grey, the inner line black or white), so the leak is caught by colour
 * even where the pane's own light would hide a dip in luminance.
 */
@Composable
private fun MarkerPage(scrollPx: Int, dark: Boolean) {
    val scrollDp = with(LocalDensity.current) { scrollPx.toDp() }
    val ink = if (dark) Color.White else Color.Black
    val gapInk = if (dark) Color(0xFF8C8CFF) else Color(0xFF0000C8)
    Column(modifier = Modifier.graphicsLayer { translationY = -scrollPx.toFloat() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(760.dp)
                .background(if (dark) Color(0xFF3A3A3A) else Color(0xFFD8D8D8))
                .drawWithContent {
                    drawContent()
                    drawMarkers(ink, gapInk)
                },
        )
        Spacer(Modifier.height(scrollDp))
    }
}

/**
 * A flat page, mid-grey on the left half and teal on the right. Over a flat
 * page a pane changes nothing but by its own wash, so the bar's colour here
 * is the wash's share of it — transparency as one number.
 */
@Composable
private fun TintPage(scrollPx: Int) {
    val scrollDp = with(LocalDensity.current) { scrollPx.toDp() }
    Column(modifier = Modifier.graphicsLayer { translationY = -scrollPx.toFloat() }) {
        Row(Modifier.fillMaxWidth().height(760.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF808080)))
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF009688)))
        }
        Spacer(Modifier.height(scrollDp))
    }
}

/** The probe lines of [MarkerPage], in page pixels: 4dp thick at 6dp above the bar's bottom rim, 8dp thick in the gap. */
private val MARKER_INSIDE_CENTRE = 742.dp
private val MARKER_INSIDE_HEIGHT = 4.dp
private val MARKER_GAP_CENTRE = 754.dp
private val MARKER_GAP_HEIGHT = 8.dp

private fun DrawScope.drawMarkers(ink: Color, gapInk: Color) {
    drawRect(
        ink,
        topLeft = Offset(0f, (MARKER_INSIDE_CENTRE - MARKER_INSIDE_HEIGHT / 2).toPx()),
        size = Size(size.width, MARKER_INSIDE_HEIGHT.toPx()),
    )
    drawRect(
        gapInk,
        topLeft = Offset(0f, (MARKER_GAP_CENTRE - MARKER_GAP_HEIGHT / 2).toPx()),
        size = Size(size.width, MARKER_GAP_HEIGHT.toPx()),
    )
}

/**
 * The root scaffolding — the PRODUCTION root of [TurnyptApp]: the window's
 * [com.turnypt.app.ui.theme.LiquidGlassScene] (field, recorded page, tier)
 * handed down through the scene local, the page source around the content,
 * and the floating bar inset above the navigation strip. The same code the
 * app runs, bar the NavHost. [scrollPx] moves the page under the bar.
 */
@Composable
private fun LiquidGlassHarness(selected: Int, scrollPx: Int, page: Page, dark: Boolean, rtl: Boolean) {
    scrollPxNow = scrollPx
    val glass = LocalGlassTokens.current
    val scene = rememberLiquidGlassScene(enabled = true)
    SideEffect { glassColorsNow = scene?.colors }
    CompositionLocalProvider(LocalLiquidGlassScene provides scene) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scene != null) Modifier.liquidGlassField(scene)
                    else Modifier.glassBackdrop(glass),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scene != null) Modifier.liquidGlassSource(scene.page) else Modifier,
                    )
                    .imePadding(),
            ) {
                when (page) {
                    Page.RampY -> RampPage(horizontal = false, scrollPx = scrollPx, rtl = rtl)
                    Page.RampX -> RampPage(horizontal = true, scrollPx = scrollPx, rtl = rtl)
                    Page.Markers -> MarkerPage(scrollPx = scrollPx, dark = dark)
                    Page.Tint -> TintPage(scrollPx = scrollPx)
                    Page.Content, Page.Cards -> Box(
                        Modifier.graphicsLayer { translationY = -scrollPx.toFloat() },
                    ) {
                        if (page == Page.Content) ContentPage() else CardsPage()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().reportBounds("bar")) {
                    FloatingGlassNavBar(
                        destinations = NavDestinations,
                        selectedRoute = { destination -> NavDestinations.indexOf(destination) == selected },
                        onSelect = {},
                        scene = scene,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Writes the node's rect, in window pixels, into `<prefix>_bounds.txt` as
 * `name left top width height` — one line per name, rewritten in place, so
 * the measuring scripts read the geometry from the layout instead of
 * assuming it. Nothing is reported for a scrolled page: its rects are the
 * unscrolled ones minus the scroll.
 */
private fun Modifier.reportBounds(name: String): Modifier =
    onGloballyPositioned { coordinates ->
        val at = coordinates.positionInRoot()
        val size = coordinates.size
        boundsNow[name] = intArrayOf(at.x.roundToInt(), at.y.roundToInt(), size.width, size.height)
        val bounds = boundsFile ?: return@onGloballyPositioned
        if (scrollPxNow != 0) return@onGloballyPositioned
        val line = "$name ${at.x.roundToInt()} ${at.y.roundToInt()} ${size.width} ${size.height}"
        val lines = (if (bounds.exists()) bounds.readLines() else emptyList())
            .filter { it.isNotEmpty() && it.substringBefore(' ') != name } + line
        bounds.writeText(lines.joinToString("\n", postfix = "\n"))
    }

/** Where [LiquidGlassHarness] reports the rects of its panes; set by the test. */
private var boundsFile: File? = null

/** The last rect reported for each name — `left, top, width, height` — for the assertions read off a frame. */
private val boundsNow = mutableMapOf<String, IntArray>()

/** The glass colours of the composition being shown (the washes the assertions resolve). */
private var glassColorsNow: LiquidGlassColors? = null

/** How far the page is scrolled in the composition being placed (see [reportBounds]). */
private var scrollPxNow = 0
