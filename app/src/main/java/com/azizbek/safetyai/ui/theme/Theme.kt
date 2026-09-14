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
private val SafetyTealTint = SafetyTeal.copy(alpha = 0.14f)

private val SafetyLightColorScheme = lightColorScheme(
    primary = SafetyTeal,
    onPrimary = Color.White,
    primaryContainer = SafetyTealTint,
    onPrimaryContainer = SafetyNavy,
    secondary = SafetyNavy,
    onSecondary = Color.White,
    secondaryContainer = SafetyTealTint,
    onSecondaryContainer = SafetyNavy,
    tertiary = SafetyTeal,
    onTertiary = Color.White,
    tertiaryContainer = SafetyTealTint,
    onTertiaryContainer = SafetyNavy,
    background = SafetyBackground,
    onBackground = SafetyNavy,
    surface = SafetyCardWhite,
    onSurface = SafetyNavy,
    surfaceVariant = SafetyBackground,
    onSurfaceVariant = SafetySlate,
    outline = SafetyOutline,
    outlineVariant = SafetyOutline,
    surfaceTint = SafetyTeal,
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
    inverseSurface = SafetyNavy,
    inverseOnSurface = SafetyDarkTextPrimary,
    inversePrimary = SafetyTealBright,
    scrim = Color.Black
)

// Dark palette: near-navy background, a lighter-navy tone for regular cards,
// bright accessible teal for interactive elements (with dark text on top of
// it, since teal this bright reads better with a dark foreground than
// white), and dark-teal-tinted containers — never a straight inversion of
// the light scheme.
private val SafetyDarkTealTint = SafetyTealBright.copy(alpha = 0.16f)

private val SafetyDarkColorScheme = darkColorScheme(
    primary = SafetyTealBright,
    onPrimary = SafetyDarkBackground,
    primaryContainer = SafetyDarkTealTint,
    onPrimaryContainer = SafetyDarkTextPrimary,
    secondary = SafetyDarkTextPrimary,
    onSecondary = SafetyDarkBackground,
    secondaryContainer = SafetyDarkTealTint,
    onSecondaryContainer = SafetyDarkTextPrimary,
    tertiary = SafetyTealBright,
    onTertiary = SafetyDarkBackground,
    tertiaryContainer = SafetyDarkTealTint,
    onTertiaryContainer = SafetyDarkTextPrimary,
    background = SafetyDarkBackground,
    onBackground = SafetyDarkTextPrimary,
    surface = SafetyDarkSurface,
    onSurface = SafetyDarkTextPrimary,
    surfaceVariant = SafetyDarkSurface,
    onSurfaceVariant = SafetyDarkTextSecondary,
    outline = SafetyDarkOutline,
    outlineVariant = SafetyDarkOutline,
    surfaceTint = SafetyTealBright,
    // Same navy hue family as `surface`, stepped lighter — this is what
    // fixes the purple/brown default tint on the dark ModalBottomSheet
    // (it draws from surfaceContainerLow).
    surfaceDim = SafetyDarkSurfaceDim,
    surfaceBright = SafetyDarkSurfaceContainerHighest,
    surfaceContainerLowest = SafetyDarkSurfaceContainerLowest,
    surfaceContainerLow = SafetyDarkSurfaceContainerLow,
    surfaceContainer = SafetyDarkSurface,
    surfaceContainerHigh = SafetyDarkSurfaceContainerHigh,
    surfaceContainerHighest = SafetyDarkSurfaceContainerHighest,
    // Inverse roles (e.g. Snackbar) and the modal scrim.
    inverseSurface = SafetyBackground,
    inverseOnSurface = SafetyDarkBackground,
    inversePrimary = SafetyTeal,
    scrim = Color.Black
)

/**
 * Whether the app is currently in dark theme. Exposed alongside
 * [MaterialTheme] for the rare case (the Home hero card) where a color is a
 * fixed brand choice per theme rather than a semantic [MaterialTheme]
 * ColorScheme role.
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
