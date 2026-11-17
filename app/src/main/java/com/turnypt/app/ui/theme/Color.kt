package com.turnypt.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v0.5.0 palette ("Calm Vault", see docs/DESIGN.md).
 *
 * v0.4.1 defined the core indigo/slate/ochre scheme; v0.5 completes the
 * Material 3 role set — the surface-container tier, inverse roles,
 * outlineVariant and scrim — so grouped surfaces are built from tonal
 * containers instead of borders, exactly as the M3 role system prescribes
 * (roles are only ever used in their accessible pairs).
 *
 * The ochre tertiary stays: it renders the "fair" passphrase hint, so
 * "could be stronger" keeps reading warm rather than alarming.
 */

// Light — v1.4.0 Liquid Glass: containers stay close to the v0.5 pairs
// (every on-color keeps its contrast), surfaces cool a step so the
// frost-white glass tints read as a LAYER above them, not a match.
val LightPrimary = Color(0xFF4A5FC1)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDEE3FF)
val LightOnPrimaryContainer = Color(0xFF0D1B66)
val LightSecondary = Color(0xFF5B7390)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDBE2F0)
val LightOnSecondaryContainer = Color(0xFF1A2A40)
val LightTertiary = Color(0xFF7C5800)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDF9E)
val LightOnTertiaryContainer = Color(0xFF261A00)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFEEF1FA)
val LightOnBackground = Color(0xFF1A1B20)
val LightSurface = Color(0xFFEFF1F9)
val LightOnSurface = Color(0xFF1A1B20)
val LightSurfaceVariant = Color(0xFFE1E4EE)
val LightOnSurfaceVariant = Color(0xFF46474F)
val LightOutline = Color(0xFF777782)
val LightOutlineVariant = Color(0xFFC6C6D1)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF4F6FB)
val LightSurfaceContainer = Color(0xFFEDF0F7)
val LightSurfaceContainerHigh = Color(0xFFE7EAF2)
val LightSurfaceContainerHighest = Color(0xFFE0E4ED)
val LightSurfaceDim = Color(0xFFDCDCE4)
val LightSurfaceBright = Color(0xFFFCFCFE)
val LightInverseSurface = Color(0xFF2F3036)
val LightInverseOnSurface = Color(0xFFF1F0F7)
val LightInversePrimary = Color(0xFFBAC3FF)
val LightScrim = Color(0xFF000000)

// Dark — v1.4.0 Liquid Glass: the night field deepens one step (the glass
// tints are smoky translucency; they need a genuinely dark field beneath
// to read as depth). On-colors unchanged: every pair keeps its contrast.
val DarkPrimary = Color(0xFFBAC3FF)
val DarkOnPrimary = Color(0xFF1B2A6B)
val DarkPrimaryContainer = Color(0xFF334199)
val DarkOnPrimaryContainer = Color(0xFFDEE3FF)
val DarkSecondary = Color(0xFFBFC7DC)
val DarkOnSecondary = Color(0xFF2A3142)
val DarkSecondaryContainer = Color(0xFF404759)
val DarkOnSecondaryContainer = Color(0xFFDBE2F0)
val DarkTertiary = Color(0xFFF2BE4A)
val DarkOnTertiary = Color(0xFF402D00)
val DarkTertiaryContainer = Color(0xFF5C4200)
val DarkOnTertiaryContainer = Color(0xFFFFDF9E)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF0C0E18)
val DarkOnBackground = Color(0xFFE3E2E8)
val DarkSurface = Color(0xFF101320)
val DarkOnSurface = Color(0xFFE3E2E8)
val DarkSurfaceVariant = Color(0xFF45474F)
val DarkOnSurfaceVariant = Color(0xFFC6C5CE)
val DarkOutline = Color(0xFF90909B)
val DarkOutlineVariant = Color(0xFF44464E)
val DarkSurfaceContainerLowest = Color(0xFF07080F)
val DarkSurfaceContainerLow = Color(0xFF151826)
val DarkSurfaceContainer = Color(0xFF191C2C)
val DarkSurfaceContainerHigh = Color(0xFF232639)
val DarkSurfaceContainerHighest = Color(0xFF2E3144)
val DarkSurfaceDim = Color(0xFF0C0E18)
val DarkSurfaceBright = Color(0xFF38393F)
val DarkInverseSurface = Color(0xFFE3E2E8)
val DarkInverseOnSurface = Color(0xFF2F3036)
val DarkInversePrimary = Color(0xFF334199)
val DarkScrim = Color(0xFF000000)

/**
 * v0.5.0 semantic extension: a SUCCESS tone, which Material 3 lacks.
 *
 * Used ONLY for the unlocked/secure state (chips, avatars, banners) — the
 * vault-app pattern: the brand color never has to mean two things, and
 * "unlocked" gets its own unmistakable channel. Pairs are hand-checked
 * for contrast in both themes (docs/DESIGN.md §3.1).
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

val LightSemantic = SemanticColors(
    success = Color(0xFF1F6D33),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC9EED2),
    onSuccessContainer = Color(0xFF0A2E14),
)

val DarkSemantic = SemanticColors(
    success = Color(0xFF8BD9A0),
    onSuccess = Color(0xFF00391B),
    successContainer = Color(0xFF1E5B34),
    onSuccessContainer = Color(0xFFC9EED2),
)
