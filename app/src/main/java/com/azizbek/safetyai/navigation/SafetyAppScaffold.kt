package com.azizbek.safetyai.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.azizbek.safetyai.screens.HomeScreen
import com.azizbek.safetyai.screens.ReportsScreen
import com.azizbek.safetyai.screens.SessionsScreen
import com.azizbek.safetyai.ui.components.SafetyHeader
import kotlinx.coroutines.launch

/** App-wide Scaffold hosting the navigation drawer, bottom bar, and NavHost — the drawer and bottom bar share one NavController and destination state. */
@Composable
fun SafetyAppScaffold(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateTo(destination: SafetyDestination) {
        navController.navigateToDestination(destination)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SafetyDrawerContent(
                destinations = SafetyDestination.bottomNavItems,
                currentRoute = currentRoute,
                onDestinationClick = { destination ->
                    navigateTo(destination)
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } },
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode
            )
        }
    ) {
        Scaffold(
            topBar = {
                SafetyTopBar(onMenuClick = { scope.launch { drawerState.open() } })
            },
            bottomBar = { SafetyBottomBar(currentRoute = currentRoute, onNavigate = ::navigateTo) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = SafetyDestination.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(SafetyDestination.Home.route) {
                    HomeScreen(
                        onViewAllAssessments = { navigateTo(SafetyDestination.Sessions) }
                    )
                }
                composable(SafetyDestination.Sessions.route) { SessionsScreen() }
                composable(SafetyDestination.Reports.route) { ReportsScreen() }
            }
        }
    }
}

/** Navigates without piling up duplicate back-stack entries — shared by the drawer and the bottom bar. */
private fun NavHostController.navigateToDestination(destination: SafetyDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Shared across every screen — not just Home — so the menu/notifications/profile actions are always reachable. */
@Composable
private fun SafetyTopBar(onMenuClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SafetyHeader(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            onMenuClick = onMenuClick
        )
    }
}

@Composable
private fun SafetyBottomBar(currentRoute: String?, onNavigate: (SafetyDestination) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            SafetyDestination.bottomNavItems.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    // contentDescription is null: the always-visible label
                    // below already gives this item its accessible name, so
                    // TalkBack isn't made to read it twice.
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
