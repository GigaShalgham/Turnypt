package com.turnypt.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turnypt.app.ui.theme.LocalGlassTokens
import com.turnypt.app.ui.theme.LocalLiquidGlassScene
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

/**
 * v1.6.0-beta.13: the desktop render harness.
 *
 * Draws the REAL composables through Robolectric's native graphics (the real
 * hwui + Skia, not a hand-written imitation) and writes PNGs, so the glass is
 * SEEN before it ships. Every earlier "fix" was checked against a Python
 * re-implementation of the material, which is exactly why device-only bugs
 * (mask filters the hardware canvas ignores, hard edges, modifier order)
 * reached the user three releases in a row.
 *
 * Opt-in only — see the lgRender block in app/build.gradle.kts:
 *
 *   ./gradlew :app:testDebugUnitTest -PlgRender --tests "*LiquidGlassRenderTest*"
 *
 * Output: -PlgRenderOut=<dir> (default /tmp/lg-render); -PlgRenderOnly=light,dark
 * narrows the cases.
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

    private data class Case(val name: String, val dark: Boolean, val rtl: Boolean, val selected: Int)

    @Test
    fun rendersTheBarAndTheSurfaces() {
        val out = File(System.getProperty("turnypt.lgRenderOut") ?: "/tmp/lg-render")
        check(out.isDirectory || out.mkdirs()) { "cannot create $out" }
        val only = (System.getProperty("turnypt.lgRenderOnly") ?: "")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val prefix = System.getProperty("turnypt.lgRenderPrefix") ?: "render"
        val cases = listOf(
            Case("light", dark = false, rtl = false, selected = 2),
            Case("dark", dark = true, rtl = false, selected = 2),
            Case("dark_rtl", dark = true, rtl = true, selected = 4),
            Case("light_rtl", dark = false, rtl = true, selected = 0),
        )
        var current by mutableStateOf(cases.first())
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view }
            val c = current
            key(c.name) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (c.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    TurnyptTheme(darkTheme = c.dark) {
                        LiquidGlassHarnessPage(selected = c.selected)
                    }
                }
            }
        }
        for (c in cases) {
            if (only.isNotEmpty() && c.name !in only) continue
            current = c
            compose.waitForIdle()
            // springs settle (the selection lens flight, icon scale)
            compose.mainClock.advanceTimeBy(1_200)
            compose.waitForIdle()
            val bitmap = screenshot()
            val file = File(out, "${prefix}_${c.name}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("LG_RENDER ${file.path} ${bitmap.width}x${bitmap.height}")
        }
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
    private fun screenshot(): Bitmap {
        val view = checkNotNull(rootView) { "the composition never reported its view" }
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val mode = hardwareScreenshot(view, bitmap)
        if (mode == null) {
            view.draw(Canvas(bitmap))
            println("LG_RENDER capture=software")
        } else {
            println("LG_RENDER capture=$mode")
        }
        return bitmap
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

/**
 * The harness page: the app root's glass scaffolding around sample content
 * that runs UNDER the bar (text lines + colour blocks + a glass card), so
 * the panes have something real to frost and bend — the bar AND a card of
 * the whole-design material in the same render.
 */
@Composable
internal fun LiquidGlassHarnessPage(selected: Int) {
    LiquidGlassHarness(selected = selected) {
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
            SectionCard(title = "Liquid Glass card") {
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
}

/**
 * The root scaffolding — the PRODUCTION root of [TurnyptApp]: the window's
 * [com.turnypt.app.ui.theme.LiquidGlassScene] (field, recorded page, tier)
 * handed down through the scene local, the page source around the content,
 * and the floating bar inset above the navigation strip. The same code the
 * app runs, bar the NavHost.
 */
@Composable
private fun LiquidGlassHarness(selected: Int, page: @Composable () -> Unit) {
    val glass = LocalGlassTokens.current
    val scene = rememberLiquidGlassScene(enabled = true)
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
                page()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
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
