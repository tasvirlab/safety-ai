package com.azizbek.safetyai.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.azizbek.safetyai.assessment.AssessmentFormViewModel
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CAMERA_GUIDANCE_ROUTE
import com.azizbek.safetyai.assessment.CAMERA_RECORDING_ROUTE
import com.azizbek.safetyai.assessment.JobDetailsRoute
import com.azizbek.safetyai.assessment.LOAD_COUPLING_ROUTE_PATTERN
import com.azizbek.safetyai.assessment.LoadCouplingRoute
import com.azizbek.safetyai.assessment.NEW_ASSESSMENT_FLOW_ROUTE
import com.azizbek.safetyai.assessment.NEW_ASSESSMENT_FLOW_ROUTES
import com.azizbek.safetyai.assessment.NewAssessmentFlowViewModel
import com.azizbek.safetyai.assessment.REVIEW_ASSESSMENT_ROUTE
import com.azizbek.safetyai.assessment.VIDEO_UPLOAD_ROUTE
import com.azizbek.safetyai.assessment.assessmentDetailsRoute
import com.azizbek.safetyai.assessment.loadCouplingRoute
import com.azizbek.safetyai.capture.CameraGuidanceScreen
import com.azizbek.safetyai.capture.CameraRecordingScreen
import com.azizbek.safetyai.detail.AssessmentDetailScreen
import com.azizbek.safetyai.detail.EditAssessmentScreen
import com.azizbek.safetyai.notifications.NotificationsScreen
import com.azizbek.safetyai.reports.ReportDetailScreen
import com.azizbek.safetyai.reports.ReportsScreen
import com.azizbek.safetyai.review.ReviewAssessmentScreen
import com.azizbek.safetyai.screens.HomeScreen
import com.azizbek.safetyai.screens.SessionsScreen
import com.azizbek.safetyai.settings.SettingsScreen
import com.azizbek.safetyai.settings.FaqScreen
import com.azizbek.safetyai.ui.components.SafetyHeader
import com.azizbek.safetyai.upload.VideoUploadScreen
import kotlinx.coroutines.launch
private const val REPORT_DETAIL_ROUTE_PATTERN =
    "report/{assessmentId}"

private fun reportDetailRoute(
    assessmentId: String
): String = "report/$assessmentId"

/**
 * App-wide Scaffold hosting the top bar, bottom nav, and NavHost. No
 * navigation drawer â€” bottom navigation already covers Home/Assessments/
 * Reports, and a drawer duplicating it was removed.
 */
@Composable
fun SafetyAppScaffold() {
    val navController = rememberNavController()
    val container = (LocalContext.current.applicationContext as SafetyAIApplication).container

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Every screen in the New Assessment flow, plus assessment detail/edit/replace-video,
    // notifications and settings, are full-screen â€” no bottom nav.
    val isChromeless = currentRoute != null && (
        NEW_ASSESSMENT_FLOW_ROUTES.any { currentRoute.startsWith(it) } ||
            CHROMELESS_ROUTE_PREFIXES.any { currentRoute.startsWith(it) }
        )

    fun navigateTo(destination: SafetyDestination) {
        navController.navigateToDestination(destination)
    }

    Scaffold(
        topBar = {
            if (!isChromeless) {
                SafetyTopBar(
                    onNotificationsClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                    onSettingsClick = { navController.navigate(SETTINGS_ROUTE) }
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
                        onOpenReport = { id -> navController.navigate(reportDetailRoute(id)) }
                    )
                }

                // The whole New Assessment flow â€” one NewAssessmentFlowViewModel instance,
                // scoped to this nested graph, shared by every screen inside it.
                navigation(startDestination = ASSESSMENT_DETAILS_ROUTE_PATTERN, route = NEW_ASSESSMENT_FLOW_ROUTE) {
                    composable(
                        route = ASSESSMENT_DETAILS_ROUTE_PATTERN,
                        arguments = listOf(navArgument(ASSESSMENT_SOURCE_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val source = entry.arguments.toAssessmentSource()
                        val formViewModel: AssessmentFormViewModel = viewModel(entry)
                        JobDetailsRoute(
                            viewModel = formViewModel,
                            onBack = { navController.popBackStack() },
                            onContinue = { navController.navigate(loadCouplingRoute(source)) }
                        )
                    }
                    composable(
                        route = LOAD_COUPLING_ROUTE_PATTERN,
                        arguments = listOf(navArgument(ASSESSMENT_SOURCE_ARG) { type = NavType.StringType })
                    ) { entry ->
                        val source = entry.arguments.toAssessmentSource()
                        // Shares the exact AssessmentFormViewModel instance step 1 created,
                        // by looking up that same back-stack entry via its route.
                        val jobDetailsEntry = remember(entry) {
                            navController.getBackStackEntry(assessmentDetailsRoute(source))
                        }
                        val formViewModel: AssessmentFormViewModel = viewModel(jobDetailsEntry)
                        val flowViewModel = rememberFlowViewModel(navController, entry, container)
                        LoadCouplingRoute(
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
                    composable(CAMERA_RECORDING_ROUTE) { entry ->
                        val flowViewModel = rememberFlowViewModel(navController, entry, container)
                        val coroutineScope = rememberCoroutineScope()
                        CameraRecordingScreen(
                            videoFileStore = container.videoFileStore,
                            onBack = { navController.popBackStack() },
                            onRecorded = { file ->
                                coroutineScope.launch {
                                    val metadata = container.videoFileStore.readMetadata(file)
                                    flowViewModel.setVideo(file.absolutePath, metadata)
                                    navController.navigate(REVIEW_ASSESSMENT_ROUTE)
                                }
                            }
                        )
                    }
                    composable(VIDEO_UPLOAD_ROUTE) { entry ->
                        val flowViewModel = rememberFlowViewModel(navController, entry, container)
                        VideoUploadScreen(
                            videoFileStore = container.videoFileStore,
                            onBack = { navController.popBackStack() },
                            onVideoReady = { path, metadata ->
                                flowViewModel.setVideo(path, metadata)
                                navController.navigate(REVIEW_ASSESSMENT_ROUTE)
                            }
                        )
                    }
                    composable(REVIEW_ASSESSMENT_ROUTE) { entry ->
                        val flowViewModel = rememberFlowViewModel(navController, entry, container)
                        val state by flowViewModel.state.collectAsStateWithLifecycle()
                        val draft = state.draft
                        val path = state.videoFilePath
                        val metadata = state.videoMetadata
                        if (draft != null && path != null && metadata != null) {
                            ReviewAssessmentScreen(
                                draft = draft,
                                videoFilePath = path,
                                videoMetadata = metadata,
                                isSaving = state.isSaving,
                                onDiscardVideo = {
                                    flowViewModel.discardVideo()
                                    navController.popBackStack()
                                },
                                onEditDetails = {
                                    navController.popBackStack(route = assessmentDetailsRoute(draft.source), inclusive = false)
                                },
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
                        analysisResultStore = container.analysisResultStore,
                        assessmentId = id,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(editAssessmentRoute(it)) },
                        onReplaceVideo = { navController.navigate(replaceVideoRoute(it)) },
                        onDeleted = { navController.popBackStack() },
                        onViewReport = { id -> navController.navigate(reportDetailRoute(id)) }
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
                        title = "Replace video",
                        stepLabel = null,
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
                composable(
                    route = REPORT_DETAIL_ROUTE_PATTERN,
                    arguments = listOf(
                        navArgument("assessmentId") {
                            type = NavType.StringType
                        }
                    )
                ) { entry ->
                    val id = entry.arguments
                        ?.getString("assessmentId")
                        .orEmpty()

                    ReportDetailScreen(
                        repository =
                            container.assessmentRepository,
                        resultStore =
                            container.analysisResultStore,
                        assessmentId = id,
                        onBack = {
                            navController.popBackStack()
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
                        onBack = { navController.popBackStack() },
                        onFaq = { navController.navigate(FAQ_ROUTE) }
                    )
                }
                composable(FAQ_ROUTE) {
                    FaqScreen(onBack = { navController.popBackStack() })
                }
            }
        }
}

/**
 * The one [NewAssessmentFlowViewModel] instance shared by every screen in the
 * New Assessment nested graph â€” scoped to that graph's own back-stack entry
 * so it's cleared as soon as the whole flow finishes or is abandoned.
 */
@Composable
private fun rememberFlowViewModel(
    navController: NavHostController,
    entry: NavBackStackEntry,
    container: com.azizbek.safetyai.AppContainer
): NewAssessmentFlowViewModel {
    val parentEntry: NavBackStackEntry = remember(entry) {
        navController.getBackStackEntry(NEW_ASSESSMENT_FLOW_ROUTE)
    }
    return viewModel(
        parentEntry,
        factory = NewAssessmentFlowViewModel.factory(container.assessmentRepository, container.videoFileStore)
    )
}

/** Reads the `{source}` nav argument shared by every step-1/step-2 wizard route. */
private fun Bundle?.toAssessmentSource(): AssessmentSource =
    this?.getString(ASSESSMENT_SOURCE_ARG)
        ?.let { runCatching { AssessmentSource.valueOf(it) }.getOrNull() }
        ?: AssessmentSource.CAMERA

/** Navigates without piling up duplicate back-stack entries â€” shared by the bottom bar. */
private fun NavHostController.navigateToDestination(destination: SafetyDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Shared across every top-level screen so Notifications/Settings are always reachable. */
@Composable
private fun SafetyTopBar(
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SafetyHeader(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            onNotificationsClick = onNotificationsClick,
            onSettingsClick = onSettingsClick
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
                    // Selected icon/label stay charcoal (bold, always readable) â€” the
                    // yellow lives in the pill behind them, never as yellow text.
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
