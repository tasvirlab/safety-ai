package com.azizbek.safetyai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.azizbek.safetyai.R

/** The condensed display voice — headings, section labels, scores, and (via [Typography.labelLarge]) every button. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

/** The readable workhorse — body copy, labels, captions. */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold)
)

// Matches the Claude Design handoff's Android type scale: display/title/
// section in Barlow Condensed with wide tracking, body/label/caption in
// Barlow. labelLarge drives every stock Button/TextButton's text, which is
// how buttons pick up the condensed CTA look with no per-call changes.
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = 0.38.sp),
    titleLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.88.sp),
    titleSmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.34.sp),
    labelLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.9.sp),
    labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
)
