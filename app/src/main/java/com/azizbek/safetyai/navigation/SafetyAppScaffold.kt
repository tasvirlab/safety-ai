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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.azizbek.safetyai.SafetyAIApplication
import com.azizbek.safetyai.assessment.ASSESSMENT_DETAILS_ROUTE_PATTERN
import com.azizbek.safetyai.assessment.ASSESSMENT_SOURCE_ARG
import com.azizbek.safetyai.assessment.AssessmentDetailsRoute
import com.azizbek.safetyai.assessment.AssessmentFormViewModel
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CAMERA_GUIDANCE_ROUTE
import com.azizbek.safetyai.assessment.CAMERA_RECORDING_ROUTE
import com.azizbek.safetyai.assessment.NEW_ASSESSMENT_FLOW_ROUTE
import com.azizbek.safetyai.assessment.NEW_ASSESSMENT_FLOW_ROUTES
import com.azizbek.safetyai.assessment.NewAssessmentFlowViewModel
import com.azizbek.safetyai.assessment.REVIEW_ASSESSMENT_ROUTE
import com.azizbek.safetyai.assessment.VIDEO_PREVIEW_ROUTE
import com.azizbek.safetyai.assessment.VIDEO_UPLOAD_ROUTE
import com.azizbek.safetyai.assessment.assessmentDetailsRoute
import com.azizbek.safetyai.capture.CameraGuidanceScreen
import com.azizbek.safetyai.capture.CameraRecordingScreen
import com.azizbek.safetyai.detail.AssessmentDetailScreen
import com.azizbek.safetyai.detail.EditAssessmentScreen
import com.azizbek.safetyai.notifications.NotificationsScreen
import com.azizbek.safetyai.preview.VideoPreviewScreen
import com.azizbek.safetyai.reports.ReportsScreen
import com.azizbek.safetyai.review.ReviewAssessmentScreen
import com.azizbek.safetyai.screens.HomeScreen
import com.azizbek.safetyai.screens.SessionsScreen
import com.azizbek.safetyai.settings.SettingsScreen
import com.azizbek.safetyai.ui.components.SafetyHeader
import com.azizbek.safetyai.upload.VideoUploadScreen
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
    val container = (LocalContext.current.applicationContext as SafetyAIApplication).container

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Every screen in the New Assessment flow, plus assessment detail/edit/replace-video,
    // notifications and settings, are full-screen — no drawer, no bottom nav.
    val isChromeless = currentRoute != null && (
        NEW_ASSESSMENT_FLOW_ROUTES.any { currentRoute.startsWith(it) } ||
            CHROMELESS_ROUTE_PREFIXES.any { currentRoute.startsWith(it) }
        )

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateTo(destination: SafetyDestination) {
        navController.navigateToDestination(destination)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isChromeless,
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
                if (!isChromeless) {
                    SafetyTopBar(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNotificationsClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                        onProfileClick = { navController.navigate(SETTINGS_ROUTE) }
                    )
                }
            },
            bottomBar = {
                if (!isChromeless) {
                    SafetyBottomBar(currentRoute = currentRoute, onNavigate = ::navigateTo)
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = SafetyDestination.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(SafetyDestination.Home.route) {
                    HomeScreen(
                        repository = container.assessmentRepository,
                        onRecordTask = {
                            navController.navigate(assessmentDetailsRoute(AssessmentSource.CAMERA))
                        },
                        onUploadVideo = {
                            navController.navigate(assessmentDetailsRoute(AssessmentSource.VIDEO_UPLOAD))
                        },
                        onViewAllAssessments = { navigateTo(SafetyDestination.Sessions) },
                        onOpenAssessment = { id -> navController.navigate(assessmentDetailRoute(id)) }
                    )
                }
                composable(SafetyDestination.Sessions.route) {
                    SessionsScreen(
                        repository = container.assessmentRepository,
                        onOpenAssessment = { id -> navController.navigate(assessmentDetailRoute(id)) },
                        onStartAssessment = { navController.navigate(assessmentDetailsRoute(AssessmentSource.CAMERA)) }
                    )
                }
                composable(SafetyDestination.Reports.route) {
                    ReportsScreen(
                        repository = container.assessmentRepository,
                        onOpenReport = { /* unreachable: no assessment can be COMPLETED in this build */ }
                    )
                }

                // The whole New Assessment flow — one NewAssessmentFlowViewModel instance,
                // scoped to this nested graph, shared by every screen inside it.
                navigation(startDestination = ASSESSMENT_DETAILS_ROUTE_PATTERN, route = NEW_ASSESSMENT_FLOW_ROUTE) {
                    composable(
                        route = ASSESSMENT_DETAILS_ROUTE_PATTERN,
                        arguments = listOf(navArgument(ASSESSMENT_SOURCE_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val formViewModel: AssessmentFormViewModel = viewModel(entry)
                        val flowViewModel = rememberFlowViewModel(navController, container)
                        AssessmentDetailsRoute(
                            viewModel = formViewModel,
                            onBack = { navController.popBackStack() },
                            onContinue = { draft ->
                                flowViewModel.setDraft(draft)
                                val next = if (draft.source == AssessmentSource.CAMERA) CAMERA_GUIDANCE_ROUTE else VIDEO_UPLOAD_ROUTE
                                navController.navigate(next)
                            }
                        )
                    }
                    composable(CAMERA_GUIDANCE_ROUTE) {
                        CameraGuidanceScreen(
                            onBack = { navController.popBackStack() },
                            onOpenCamera = { navController.navigate(CAMERA_RECORDING_ROUTE) }
                        )
                    }
                    composable(CAMERA_RECORDING_ROUTE) {
                        val flowViewModel = rememberFlowViewModel(navController, container)
                        val coroutineScope = rememberCoroutineScope()
                        CameraRecordingScreen(
                            videoFileStore = container.videoFileStore,
                            onBack = { navController.popBackStack() },
                            onRecorded = { file ->
                                coroutineScope.launch {
                                    val metadata = container.videoFileStore.readMetadata(file)
                                    flowViewModel.setVideo(file.absolutePath, metadata)
                                    navController.navigate(VIDEO_PREVIEW_ROUTE)
                                }
                            }
                        )
                    }
                    composable(VIDEO_UPLOAD_ROUTE) {
                        val flowViewModel = rememberFlowViewModel(navController, container)
                        VideoUploadScreen(
                            videoFileStore = container.videoFileStore,
                            onBack = { navController.popBackStack() },
                            onVideoReady = { path, metadata ->
                                flowViewModel.setVideo(path, metadata)
                                navController.navigate(VIDEO_PREVIEW_ROUTE)
                            }
                        )
                    }
                    composable(VIDEO_PREVIEW_ROUTE) {
                        val flowViewModel = rememberFlowViewModel(navController, container)
                        val state by flowViewModel.state.collectAsStateWithLifecycle()
                        val path = state.videoFilePath
                        val metadata = state.videoMetadata
                        if (path != null && metadata != null) {
                            VideoPreviewScreen(
                                source = state.draft?.source ?: AssessmentSource.CAMERA,
                                videoFilePath = path,
                                metadata = metadata,
                                onDiscard = {
                                    flowViewModel.discardVideo()
                                    navController.popBackStack()
                                },
                                onUseThisVideo = { navController.navigate(REVIEW_ASSESSMENT_ROUTE) }
                            )
                        }
                    }
                    composable(REVIEW_ASSESSMENT_ROUTE) {
                        val flowViewModel = rememberFlowViewModel(navController, container)
                        val state by flowViewModel.state.collectAsStateWithLifecycle()
                        val draft = state.draft
                        val metadata = state.videoMetadata
                        if (draft != null && metadata != null) {
                            ReviewAssessmentScreen(
                                draft = draft,
                                videoMetadata = metadata,
                                isSaving = state.isSaving,
                                onBack = { navController.popBackStack() },
                                onEditDetails = {
                                    navController.popBackStack(route = assessmentDetailsRoute(draft.source), inclusive = false)
                                },
                                onChangeVideo = { navController.popBackStack() },
                                onSave = {
                                    flowViewModel.save { assessmentId ->
                                        navController.navigate(assessmentDetailRoute(assessmentId)) {
                                            popUpTo(NEW_ASSESSMENT_FLOW_ROUTE) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                composable(
                    route = ASSESSMENT_DETAIL_ROUTE_PATTERN,
                    arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("assessmentId").orEmpty()
                    AssessmentDetailScreen(
                        repository = container.assessmentRepository,
                        analysisEngine = container.analysisEngine,
                        assessmentId = id,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(editAssessmentRoute(it)) },
                        onReplaceVideo = { navController.navigate(replaceVideoRoute(it)) },
                        onDeleted = { navController.popBackStack() },
                        onViewReport = { /* unreachable: no assessment can be COMPLETED in this build */ }
                    )
                }
                composable(
                    route = EDIT_ASSESSMENT_ROUTE_PATTERN,
                    arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("assessmentId").orEmpty()
                    EditAssessmentScreen(
                        repository = container.assessmentRepository,
                        assessmentId = id,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable(
                    route = REPLACE_VIDEO_ROUTE_PATTERN,
                    arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("assessmentId").orEmpty()
                    val coroutineScope = rememberCoroutineScope()
                    VideoUploadScreen(
                        videoFileStore = container.videoFileStore,
                        onBack = { navController.popBackStack() },
                        onVideoReady = { path, metadata ->
                            coroutineScope.launch {
                                val existing = container.assessmentRepository.getById(id)
                                if (existing != null) {
                                    container.videoFileStore.delete(existing.video.filePath)
                                    container.assessmentRepository.save(
                                        existing.copy(video = metadata, updatedAtEpochMs = System.currentTimeMillis())
                                    )
                                } else {
                                    container.videoFileStore.delete(path)
                                }
                                navController.popBackStack()
                            }
                        }
                    )
                }
                composable(NOTIFICATIONS_ROUTE) {
                    NotificationsScreen(repository = container.notificationRepository, onBack = { navController.popBackStack() })
                }
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(
                        themePreferencesRepository = container.themePreferencesRepository,
                        assessmentRepository = container.assessmentRepository,
                        videoFileStore = container.videoFileStore,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * The one [NewAssessmentFlowViewModel] instance shared by every screen in the
 * New Assessment nested graph — scoped to that graph's own back-stack entry
 * so it's cleared as soon as the whole flow finishes or is abandoned.
 */
@Composable
private fun rememberFlowViewModel(
    navController: NavHostController,
    container: com.azizbek.safetyai.AppContainer
): NewAssessmentFlowViewModel {
    val parentEntry: NavBackStackEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(NEW_ASSESSMENT_FLOW_ROUTE)
    }
    return viewModel(
        parentEntry,
        factory = NewAssessmentFlowViewModel.factory(container.assessmentRepository, container.videoFileStore)
    )
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
private fun SafetyTopBar(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SafetyHeader(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            onMenuClick = onMenuClick,
            onNotificationsClick = onNotificationsClick,
            onProfileClick = onProfileClick
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
