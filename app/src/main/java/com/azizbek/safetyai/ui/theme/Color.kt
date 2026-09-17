package com.azizbek.safetyai.ui.theme

import androidx.compose.ui.graphics.Color

// Safety AI brand palette — matches the Claude Design handoff exactly.
// Safety yellow for primary actions, risk colors for warning/high-risk
// accents only. No teal, no purple, no gradients.
val SafetyYellow = Color(0xFFFFD54F)
val SafetyCharcoal = Color(0xFF101519) // on-yellow text + light-theme foreground
val SafetySteel = Color(0xFF5C666E) // light-theme secondary text

val SafetyOffWhite = Color(0xFFF4F6F7) // light bg
val SafetyCardWhite = Color(0xFFFFFFFF) // light surface
val SafetyBorder = Color(0xFFD3D9DD) // light outline

// Dark theme surfaces.
val SafetyDarkBackground = Color(0xFF101519)
val SafetyDarkCard = Color(0xFF1C242B)
val SafetyDarkBorder = Color(0xFF35414A)
val SafetyDarkTextPrimary = Color(0xFFF4F6F7)
val SafetyDarkTextSecondary = Color(0xFFA7B0B7)

// Risk-communication fill colors — identical in both themes, always paired
// with a text/icon label, never color alone. Used for solid chip/bar fills.
val RiskLow = Color(0xFF66BB6A)
val RiskMedium = Color(0xFFFFD54F)
val RiskHigh = Color(0xFFFB8C00)
val RiskVeryHigh = Color(0xFFE64A19)

// Risk "ink" colors — theme-adjusted so risk text/borders/icons stay AA-legible
// on their background: darker on light surfaces, lighter on dark surfaces.
val RiskLowInkLight = Color(0xFF2E7D32)
val RiskMediumInkLight = Color(0xFF7A5600)
val RiskHighInkLight = Color(0xFFB34700)
val RiskVeryHighInkLight = Color(0xFFB3341A)

val RiskLowInkDark = Color(0xFF66BB6A)
val RiskMediumInkDark = Color(0xFFFFD54F)
val RiskHighInkDark = Color(0xFFFB8C00)
val RiskVeryHighInkDark = Color(0xFFFF7043)

// Light theme's surface-container tonal scale — used by stock components
// (ModalBottomSheet, dialogs, menus) that don't take an explicit color.
// Pinned explicitly so none of them ever fall back to Material's purple
// baseline.
val SafetySurfaceDim = Color(0xFFE7E9EB)
val SafetySurfaceContainerLowest = Color(0xFFFFFFFF)
val SafetySurfaceContainerLow = Color(0xFFFAFBFB)
val SafetySurfaceContainer = Color(0xFFF1F3F4)
val SafetySurfaceContainerHigh = Color(0xFFEBEDEF)
val SafetySurfaceContainerHighest = Color(0xFFE4E7E9)

// Dark theme's surface-container tonal scale — same charcoal hue family as
// SafetyDarkCard, stepped lighter.
val SafetyDarkSurfaceDim = Color(0xFF0D1114)
val SafetyDarkSurfaceContainerLowest = Color(0xFF0D1216)
val SafetyDarkSurfaceContainerLow = Color(0xFF171E24)
val SafetyDarkSurfaceContainerHigh = Color(0xFF242D35)
val SafetyDarkSurfaceContainerHighest = Color(0xFF2B353D)
