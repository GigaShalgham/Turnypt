package com.turnypt.keyboard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.turnypt.keyboard.R

// Compact Material palette local to the IME (the keyboard module keeps its
// own theme so it never depends on the app module). v1.5.0 Turnip: the
// keyboard follows the app's purple-dominant identity — plum field, lavender
// primary, green reserved (not used here; the app keeps the one green
// channel for the success semantic).
private val ImeDark = darkColorScheme(
    primary = Color(0xFFB18AFB),        // Turnip Primary
    onPrimary = Color(0xFF2A1E4F),
    background = Color(0xFF12101A),      // Turnip Background
    onBackground = Color(0xFFF1EDF8),    // Turnip Primary Text
    surface = Color(0xFF1C1928),        // Turnip Surface
    onSurface = Color(0xFFF1EDF8),
    surfaceVariant = Color(0xFF292338), // Turnip Elevated Surface
    onSurfaceVariant = Color(0xFFC3BDD2),
    outline = Color(0xFF353044),        // Turnip Dividers
    error = Color(0xFFE25B5B),
)

private val ImeLight = lightColorScheme(
    primary = Color(0xFF8256C7),        // Turnip Primary
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FD),    // Turnip Background
    onBackground = Color(0xFF24212C),  // Turnip Primary Text
    surface = Color(0xFFFFFFFF),       // Turnip Surface
    onSurface = Color(0xFF24212C),
    surfaceVariant = Color(0xFFF0EAF9), // Turnip Elevated Surface
    onSurfaceVariant = Color(0xFF4E4A5A),
    outline = Color(0xFFE8E2EF),       // Turnip Dividers
    error = Color(0xFFB23B3B),
)

/**
 * v1.3.1: Vazirmatn (SIL OFL 1.1) for the IME's own UI strings — the
 * module's own res/font copy (Regular/Medium), so the keyboard surfaces
 * render Persian labels in proper Naskh exactly like the app does. The
 * module never depends on the app module; the faces it needs travel here.
 */
private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
)

/**
 * The compact IME type scale: the IME shows few words (row labels, dialog
 * titles and body text, notices), so only the styles it actually uses get
 * the family — the rest inherit the M3 defaults composed with the family
 * below.
 */
private val ImeTypography = Typography(
    titleSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun KeyboardTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) ImeDark else ImeLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ImeTypography,
    ) {
        // v1.5.3: the same LocalContentColor gap the app theme had —
        // MaterialTheme does not provide it, so colour-less keys/labels
        // fell back to Color.Black. Provided here with the IME's own
        // onSurface, mirroring TurnyptTheme's root fix.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
        ) {
            content()
        }
    }
}
