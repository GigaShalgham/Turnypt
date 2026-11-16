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
// own theme so it never depends on the app module).
private val ImeDark = darkColorScheme(
    primary = Color(0xFF5B8DEF),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF16161C),
    onBackground = Color(0xFFE4E4EC),
    surface = Color(0xFF1C1C24),
    onSurface = Color(0xFFE4E4EC),
    surfaceVariant = Color(0xFF2A2A34),
    onSurfaceVariant = Color(0xFFE4E4EC),
    outline = Color(0xFF3A3A46),
    error = Color(0xFFE25B5B),
)

private val ImeLight = lightColorScheme(
    primary = Color(0xFF2E4A80),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F3F8),
    onBackground = Color(0xFF1A1A22),
    surface = Color(0xFFEDEFF5),
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFFDDE1EA),
    onSurfaceVariant = Color(0xFF1A1A22),
    outline = Color(0xFFC6CAD6),
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
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ImeDark else ImeLight,
        typography = ImeTypography,
        content = content,
    )
}
