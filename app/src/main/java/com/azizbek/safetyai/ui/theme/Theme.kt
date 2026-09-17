package com.azizbek.safetyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Every container/tint slot is pinned explicitly below — Material3's
// lightColorScheme()/darkColorScheme() fall back to their purple baseline
// palette for any slot left unset, and Safety AI must never leak that
// default purple/lavender.
//
// Role mapping (kept deliberately narrow):
//  primary   = Safety Yellow, identical in both themes, always paired with
//              charcoal text on top of it.
//  tertiary  = the high-risk ink color, reserved for warning / high-risk
//              accents only — never used as a general interactive color.
//  error     = the very-high-risk ink color, shared with RiskChip's "Very
//              high risk" tone, for destructive actions and severe risk alike.
private val SafetyYellowTint = SafetyYellow.copy(alpha = 0.16f)
private val SafetyCharcoalTint = SafetyCharcoal.copy(alpha = 0.06f)
private val SafetyHighTintLight = RiskHighInkLight.copy(alpha = 0.14f)
private val SafetyErrorTintLight = RiskVeryHighInkLight.copy(alpha = 0.12f)

private val SafetyLightColorScheme = lightColorScheme(
    primary = SafetyYellow,
    onPrimary = SafetyCharcoal,
    primaryContainer = SafetyYellowTint,
    onPrimaryContainer = SafetyCharcoal,
    secondary = SafetyCharcoal,
    onSecondary = Color.White,
    secondaryContainer = SafetyCharcoalTint,
    onSecondaryContainer = SafetyCharcoal,
    tertiary = RiskHighInkLight,
    onTertiary = Color.White,
    tertiaryContainer = SafetyHighTintLight,
    onTertiaryContainer = RiskHighInkLight,
    error = RiskVeryHighInkLight,
    onError = Color.White,
    errorContainer = SafetyErrorTintLight,
    onErrorContainer = RiskVeryHighInkLight,
    background = SafetyOffWhite,
    onBackground = SafetyCharcoal,
    surface = SafetyCardWhite,
    onSurface = SafetyCharcoal,
    surfaceVariant = SafetyOffWhite,
    onSurfaceVariant = SafetySteel,
    outline = SafetyBorder,
    outlineVariant = SafetyBorder,
    surfaceTint = Color.Transparent,
    // Surface-container tonal scale — pinned so stock components (bottom
    // sheets, dialogs, menus) never fall back to Material's purple default.
    surfaceDim = SafetySurfaceDim,
    surfaceBright = SafetyCardWhite,
    surfaceContainerLowest = SafetySurfaceContainerLowest,
    surfaceContainerLow = SafetySurfaceContainerLow,
    surfaceContainer = SafetySurfaceContainer,
    surfaceContainerHigh = SafetySurfaceContainerHigh,
    surfaceContainerHighest = SafetySurfaceContainerHighest,
    // Inverse roles (e.g. Snackbar) and the modal scrim.
    inverseSurface = SafetyCharcoal,
    inverseOnSurface = SafetyDarkTextPrimary,
    inversePrimary = SafetyYellow,
    scrim = Color.Black
)

private val SafetyDarkYellowTint = SafetyYellow.copy(alpha = 0.16f)
private val SafetyDarkCharcoalTint = SafetyDarkTextPrimary.copy(alpha = 0.08f)
private val SafetyDarkHighTint = RiskHighInkDark.copy(alpha = 0.20f)
private val SafetyDarkErrorTint = RiskVeryHighInkDark.copy(alpha = 0.20f)

private val SafetyDarkColorScheme = darkColorScheme(
    primary = SafetyYellow,
    onPrimary = SafetyCharcoal,
    primaryContainer = SafetyDarkYellowTint,
    onPrimaryContainer = SafetyYellow,
    secondary = SafetyDarkTextPrimary,
    onSecondary = SafetyCharcoal,
    secondaryContainer = SafetyDarkCharcoalTint,
    onSecondaryContainer = SafetyDarkTextPrimary,
    tertiary = RiskHighInkDark,
    onTertiary = SafetyCharcoal,
    tertiaryContainer = SafetyDarkHighTint,
    onTertiaryContainer = RiskHighInkDark,
    error = RiskVeryHighInkDark,
    onError = SafetyCharcoal,
    errorContainer = SafetyDarkErrorTint,
    onErrorContainer = RiskVeryHighInkDark,
    background = SafetyDarkBackground,
    onBackground = SafetyDarkTextPrimary,
    surface = SafetyDarkCard,
    onSurface = SafetyDarkTextPrimary,
    surfaceVariant = SafetyDarkCard,
    onSurfaceVariant = SafetyDarkTextSecondary,
    outline = SafetyDarkBorder,
    outlineVariant = SafetyDarkBorder,
    surfaceTint = Color.Transparent,
    // Same charcoal hue family as `surface`, stepped lighter — this is what
    // fixes the purple/brown default tint on the dark ModalBottomSheet
    // (it draws from surfaceContainerLow).
    surfaceDim = SafetyDarkSurfaceDim,
    surfaceBright = SafetyDarkSurfaceContainerHighest,
    surfaceContainerLowest = SafetyDarkSurfaceContainerLowest,
    surfaceContainerLow = SafetyDarkSurfaceContainerLow,
    surfaceContainer = SafetyDarkCard,
    surfaceContainerHigh = SafetyDarkSurfaceContainerHigh,
    surfaceContainerHighest = SafetyDarkSurfaceContainerHighest,
    // Inverse roles (e.g. Snackbar) and the modal scrim.
    inverseSurface = SafetyOffWhite,
    inverseOnSurface = SafetyCharcoal,
    inversePrimary = SafetyYellow,
    scrim = Color.Black
)

/**
 * Whether the app is currently in dark theme. Exposed alongside
 * [MaterialTheme] for the rare case where a color is a fixed brand choice
 * per theme rather than a semantic [MaterialTheme] ColorScheme role — e.g.
 * picking between [RiskLowInkLight]/[RiskLowInkDark] and friends.
 */
val LocalSafetyDarkTheme = staticCompositionLocalOf { false }

/**
 * Safety AI's Material 3 theme. Dynamic color is intentionally left off: the
 * app ships a fixed brand palette rather than adapting to the device
 * wallpaper (ponytail: fixed brand identity over Android 12+ dynamic color).
 */
@Composable
fun SafetyAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SafetyDarkColorScheme else SafetyLightColorScheme
    CompositionLocalProvider(LocalSafetyDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
