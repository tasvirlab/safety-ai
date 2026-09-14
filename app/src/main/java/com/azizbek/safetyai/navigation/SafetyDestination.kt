package com.azizbek.safetyai.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/** The app's top-level, bottom-nav-reachable screens. */
sealed class SafetyDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : SafetyDestination("home", "Home", Icons.Filled.Home)
    // Route stays "sessions" — only the user-facing label changes to
    // "Assessments" — to avoid an unnecessary navigation-graph rename.
    data object Sessions : SafetyDestination("sessions", "Assessments", Icons.Filled.History)
    data object Reports : SafetyDestination("reports", "Reports", Icons.Filled.Assessment)

    companion object {
        val bottomNavItems = listOf(Home, Sessions, Reports)
    }
}
