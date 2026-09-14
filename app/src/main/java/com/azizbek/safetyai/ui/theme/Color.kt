package com.azizbek.safetyai.ui.theme

import androidx.compose.ui.graphics.Color

// Safety AI brand palette (light theme).
val SafetyTeal = Color(0xFF147E70)
val SafetyNavy = Color(0xFF182B3B)
val SafetyBackground = Color(0xFFF6F8FA)
val SafetyCardWhite = Color(0xFFFFFFFF)
val SafetyOutline = Color(0xFFE3E9ED)
val SafetySlate = Color(0xFF64748B)

// Reserved for genuine ergonomic warning / risk states (e.g. a REBA score
// badge once real assessments exist) — never used as decoration, in either
// theme.
val SafetyRiskAmber = Color(0xFFF59E0B)

// Light theme's surface-container tonal scale — used by stock components
// (ModalBottomSheet, dialogs, menus) that don't take an explicit color.
// Pinned explicitly so none of them ever fall back to Material's purple
// baseline. A subtle navy-neutral ramp, lowest (dimmest) to highest
// (brightest); "lowest"/"bright" both land on white, same as stock schemes.
val SafetySurfaceDim = Color(0xFFD8DEE3)
val SafetySurfaceContainerLowest = Color(0xFFFFFFFF)
val SafetySurfaceContainerLow = Color(0xFFFBFCFD)
val SafetySurfaceContainer = Color(0xFFF3F5F7)
val SafetySurfaceContainerHigh = Color(0xFFEDF0F3)
val SafetySurfaceContainerHighest = Color(0xFFE7EBEE)

// Dark theme palette — deliberately tuned for a dark navy product, not a
// mechanical inversion of the light palette.
val SafetyDarkBackground = Color(0xFF0F1D27) // near-navy app background
val SafetyDarkSurface = Color(0xFF1B2E3B) // slightly lighter navy: cards
val SafetyDarkPrimaryCard = Color(0xFF0B2C2C) // deep teal-navy: the one hero card
val SafetyDarkTextPrimary = Color(0xFFF1F5F8) // high-contrast off-white
val SafetyDarkTextSecondary = Color(0xFF9DAFBE) // muted blue-grey secondary text
val SafetyTealBright = Color(0xFF3FD6C0) // brighter, accessible teal for dark backgrounds
val SafetyDarkOutline = Color(0xFF2E4552) // subtle but visible border

// Dark theme's surface-container tonal scale — the same navy hue family as
// SafetyDarkSurface, stepped lighter. This is what fixes the purple/brown
// default tint on the dark ModalBottomSheet (it uses surfaceContainerLow).
val SafetyDarkSurfaceDim = Color(0xFF0A1620)
val SafetyDarkSurfaceContainerLowest = Color(0xFF0B1B24)
val SafetyDarkSurfaceContainerLow = Color(0xFF16262F)
val SafetyDarkSurfaceContainerHigh = Color(0xFF223846)
val SafetyDarkSurfaceContainerHighest = Color(0xFF294252)
