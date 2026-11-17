package com.turnypt.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v1.5.0 palette — the Turnip identity (user brief 2026-11-17).
 *
 * Purple-dominant with a natural green counterpoint, premium and
 * restrained: dark theme sits on plum-black (#12101A / #1C1928 / #292338),
 * light on violet-warmed white (#FAF8FD / #FFFFFF / #F0EAF9). Primary is
 * #B18AFB (dark) / #8256C7 (light); Secondary Purple and Secondary Green
 * keep green OCCASIONAL — never competing with purple; the accent
 * (#DCCAFF / #E8DDF9) tints highlights and the selection surfaces.
 *
 * Every exact brief hex anchors its named role; the remaining roles
 * derive around those anchors so every on/container pair holds ≥4.5:1
 * (audited in scripts/contrast_audit_v15.py — both themes, worst-case
 * composited over the backdrop bounds). The ochre tertiary is retired:
 * "fair" passphrase hints now read warm-violet, not alarming.
 */

// ---- Light: violet-warmed white field, deep-purple brand ----
val LightPrimary = Color(0xFF8256C7)               // Primary
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8DDF9)      // Accent (as container tint)
val LightOnPrimaryContainer = Color(0xFF2C1A55)
val LightSecondary = Color(0xFF6B4F9E)             // Secondary Purple, darkened for white-on contrast
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEFEBF6)
val LightOnSecondaryContainer = Color(0xFF2A2537)
val LightTertiary = Color(0xFF55703F)              // Secondary Green, darkened for white-on
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDDE7CC)
val LightOnTertiaryContainer = Color(0xFF1E2A11)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFFAF8FD)            // Background
val LightOnBackground = Color(0xFF24212C)          // Primary Text
val LightSurface = Color(0xFFFFFFFF)               // Surface
val LightOnSurface = Color(0xFF24212C)             // Primary Text
val LightSurfaceVariant = Color(0xFFEDE8F2)
val LightOnSurfaceVariant = Color(0xFF4E4A5A)     // Secondary Text darkened to ≥4.5:1
val LightOutline = Color(0xFF777381)               // Secondary Text (as outline)
val LightOutlineVariant = Color(0xFFE8E2EF)        // Dividers
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF5F1FA)
val LightSurfaceContainer = Color(0xFFF0EAF9)      // Elevated Surface
val LightSurfaceContainerHigh = Color(0xFFEAE4F5)
val LightSurfaceContainerHighest = Color(0xFFE4DEF1)
val LightSurfaceDim = Color(0xFFDBD5E5)
val LightSurfaceBright = Color(0xFFFCFAFE)
val LightInverseSurface = Color(0xFF2A2733)
val LightInverseOnSurface = Color(0xFFF1EDF8)
val LightInversePrimary = Color(0xFFDCCAFF)
val LightScrim = Color(0xFF000000)

// ---- Dark: plum-black field, luminous lavender brand ----
val DarkPrimary = Color(0xFFB18AFB)                // Primary
val DarkOnPrimary = Color(0xFF2A1E4F)
val DarkPrimaryContainer = Color(0xFF4F3D82)
val DarkOnPrimaryContainer = Color(0xFFDCCAFF)     // Accent (as on-color)
val DarkSecondary = Color(0xFF9470D8)              // Secondary Purple
val DarkOnSecondary = Color(0xFF211838)
val DarkSecondaryContainer = Color(0xFF3D3560)
val DarkOnSecondaryContainer = Color(0xFFDCD2F0)
val DarkTertiary = Color(0xFFA3B98A)              // Secondary Green
val DarkOnTertiary = Color(0xFF1A2312)
val DarkTertiaryContainer = Color(0xFF39482A)
val DarkOnTertiaryContainer = Color(0xFFD5E3C0)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF12101A)            // Background
val DarkOnBackground = Color(0xFFF1EDF8)          // Primary Text
val DarkSurface = Color(0xFF1C1928)               // Surface
val DarkOnSurface = Color(0xFFF1EDF8)             // Primary Text
val DarkSurfaceVariant = Color(0xFF2E2A3C)
val DarkOnSurfaceVariant = Color(0xFFC3BDD2)      // Secondary Text, lifted to ≥4.5:1
val DarkOutline = Color(0xFFA6A0B3)               // Secondary Text (as outline)
val DarkOutlineVariant = Color(0xFF353044)        // Dividers
val DarkSurfaceContainerLowest = Color(0xFF0D0B14)
val DarkSurfaceContainerLow = Color(0xFF1B1826)
val DarkSurfaceContainer = Color(0xFF1F1C2D)
val DarkSurfaceContainerHigh = Color(0xFF292338)  // Elevated Surface
val DarkSurfaceContainerHighest = Color(0xFF332E43)
val DarkSurfaceDim = Color(0xFF12101A)
val DarkSurfaceBright = Color(0xFF453F55)
val DarkInverseSurface = Color(0xFFF1EDF8)
val DarkInverseOnSurface = Color(0xFF2A2733)
val DarkInversePrimary = Color(0xFF8256C7)
val DarkScrim = Color(0xFF000000)

/**
 * The v0.5 semantic extension: a SUCCESS tone, which Material 3 lacks.
 *
 * v1.5.0 recolors it to the Turnip Secondary Green — the one green
 * channel in the identity, reserved for the unlocked/secure state
 * (chips, avatars, banners); purple never has to mean two things.
 * Pairs audited ≥4.5:1 in both themes (scripts/contrast_audit_v15.py).
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

val LightSemantic = SemanticColors(
    success = Color(0xFF55703F),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDDE7CC),
    onSuccessContainer = Color(0xFF1E2A11),
)

val DarkSemantic = SemanticColors(
    success = Color(0xFFA3B98A),
    onSuccess = Color(0xFF1A2312),
    successContainer = Color(0xFF39482A),
    onSuccessContainer = Color(0xFFD5E3C0),
)
