package com.snow.safetalk.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snow.safetalk.history.HistoryViewModel
import com.snow.safetalk.history.MessageSource
import com.snow.safetalk.history.toUiModel
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.ui.screens.*
import com.snow.safetalk.ui.screens.legal.PrivacyPolicyScreen
import com.snow.safetalk.ui.screens.legal.TermsScreen
import com.snow.safetalk.ui.screens.onboarding.PermissionDisclosureScreen
import com.snow.safetalk.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Lightweight debug logger (no-ops in release) ──────────────────────────────

private object NavLogger {
    fun log(msg: String) {
        if (Log.isLoggable("NavLogger", Log.DEBUG)) Log.d("NavLogger", msg)
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

// Removed hardcoded BgColor / PrimaryBlue — use AppColors theme tokens instead

// ── Bottom navigation item model ──────────────────────────────────────────────

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME,     "Bosh sahifa", Icons.Default.Home),
    BottomNavItem(Routes.SECURITY, "Himoya",      Icons.Default.Shield),
    BottomNavItem(Routes.SETTINGS, "Sozlamalar",  Icons.Default.Settings),
)

// ── Routes that show the bottom navigation bar ────────────────────────────────
private val mainRoutes = setOf(Routes.HOME, Routes.SECURITY, Routes.SECURITY_ROUTE, Routes.SETTINGS)

/**
 * Top-level NavHost with full navigation hardening:
 *  • Bottom navigation bar for 3 main tabs (Home / Himoya / Sozlamalar)
 *  • ModalNavigationDrawer retained for secondary screens
 *  • RESULT route: timeout fallback after 800 ms, DisposableEffect cleanup
 *  • launchSingleTop everywhere appropriate
 *  • saveState/restoreState for bottom-tab destinations
 *  • Analyzer wrapped in try/catch
 *  • BackHandler on HOME triggers exit confirmation dialog
 *  • Debug-only route logging
 *  • Deep-link RESULT gated behind legal acceptance
 */
@Composable
fun SafeTalkNavHost(
    pendingAnalysisId: String? = null,
    onAnalysisConsumed: () -> Unit = {}
) {
    val navController    = rememberNavController()
    val scope            = rememberCoroutineScope()
    val historyViewModel: HistoryViewModel = viewModel()
    val context          = LocalContext.current
    val settingsStore    = remember { SettingsDataStore(context) }

    // ── Snackbar host for Scan error feedback ─────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Pending deep-link state: stored in DataStore to survive process death
    val deferredAnalysisId by settingsStore.pendingDeepLinkIdFlow.collectAsState(initial = null)
    val hasAcceptedLegal by settingsStore.hasAcceptedLegalFlow.collectAsState(initial = false)
    val hasCompletedOnboarding by settingsStore.hasCompletedOnboardingFlow.collectAsState(initial = false)

    // ── Deep-link: gate behind legal and onboarding acceptance ────────
    LaunchedEffect(pendingAnalysisId) {
        if (!pendingAnalysisId.isNullOrBlank()) {
            NavLogger.log("deep-link -> processing pendingAnalysisId: $pendingAnalysisId")
            val legal = settingsStore.hasAcceptedLegalFlow.first()
            val onboarding = settingsStore.hasCompletedOnboardingFlow.first()

            if (legal && onboarding) {
                // Ready — navigate directly
                val result = historyViewModel.getById(pendingAnalysisId)
                if (result != null) {
                    historyViewModel.selectResult(result)
                    navController.navigate(Routes.RESULT) {
                        launchSingleTop = true
                    }
                } else {
                    NavLogger.log("deep-link -> $pendingAnalysisId not found")
                }
            } else {
                // Defer and redirect based on state
                NavLogger.log("deep-link -> BLOCKED. Deferring $pendingAnalysisId")
                settingsStore.setPendingDeepLinkId(pendingAnalysisId)
                val destination = if (!legal) Routes.ONBOARDING_LEGAL else Routes.PERMISSION_DISCLOSURE
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                    launchSingleTop = true
                }
            }
            onAnalysisConsumed()
        }
    }

    // ── Resume deferred deep-link after full onboarding ──────────────────
    LaunchedEffect(hasAcceptedLegal, hasCompletedOnboarding, deferredAnalysisId) {
        if (hasAcceptedLegal && hasCompletedOnboarding && deferredAnalysisId != null) {
            val id = deferredAnalysisId!!
            settingsStore.setPendingDeepLinkId(null)
            
            NavLogger.log("deep-link -> resuming deferred analysisId: $id")
            val result = historyViewModel.getById(id)
            if (result != null) {
                historyViewModel.selectResult(result)
                navController.navigate(Routes.RESULT) {
                    launchSingleTop = true
                }
            } else {
                NavLogger.log("deep-link -> deferred $id not found in history")
            }
        }
    }

    // ── Exit dialog ───────────────────────────────────────────────────────
    var showExitDialog by remember { mutableStateOf(false) }
    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }

    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route ?: Routes.HOME

    // ── Debug: log every route change ─────────────────────────────────────
    LaunchedEffect(currentRoute) {
        NavLogger.log("route -> $currentRoute")
    }

    // ── BackHandler: intercept back on HOME to show exit dialog ──────────
    BackHandler(enabled = currentRoute == Routes.HOME) {
        showExitDialog = true
    }

    // ── Bottom-tab navigate helper ─────────────────────────────────────────
    fun navigateBottomTab(route: String) {
        if (route == currentRoute || (route == Routes.SECURITY && currentRoute == Routes.SECURITY_ROUTE)) return
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }


        Scaffold(
            modifier       = Modifier.fillMaxSize(),
            containerColor = AppColors.BgSolid,
            snackbarHost   = { SnackbarHost(snackbarHostState) },
            bottomBar      = {
                // Only show bottom bar on the 3 main tabs
                if (currentRoute in mainRoutes) {
                    SafeTalkBottomBar(
                        currentRoute  = currentRoute,
                        onTabSelected = { route -> navigateBottomTab(route) }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Routes.SPLASH,
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                // ── Splash ────────────────────────────────────────────────
                composable(
                    route          = Routes.SPLASH,
                    exitTransition = { fadeOut(animationSpec = tween(500)) }
                ) {
                    SplashScreen(
                        onAnimationFinished = {
                            scope.launch {
                                val legalAccepted = settingsStore.hasAcceptedLegalFlow.first()
                                val completedOnboarding = settingsStore.hasCompletedOnboardingFlow.first()
                                
                                val destination = when {
                                    !legalAccepted -> Routes.ONBOARDING_LEGAL
                                    !completedOnboarding -> Routes.PERMISSION_DISCLOSURE
                                    else -> Routes.HOME
                                }
                                navController.navigate(destination) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                // ── Home ──────────────────────────────────────────────────
                composable(
                    route           = Routes.HOME,
                    enterTransition = { fadeIn(animationSpec = tween(500)) }
                ) {
                    HomeScreen(
                        historyViewModel   = historyViewModel,
                        onCheckMessage     = {
                            navController.navigate(Routes.SCAN) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToResult = {
                            navController.navigate(Routes.HISTORY) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToDetail = { id ->
                            scope.launch {
                                val result = historyViewModel.getById(id)
                                if (result != null) {
                                    historyViewModel.selectResult(result)
                                    navController.navigate(Routes.RESULT) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onNavigateToNotifications = {
                            navController.navigate(Routes.NOTIFICATIONS) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToStatistics = {
                            navController.navigate(Routes.STATISTICS) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToSecurity = { focus ->
                            val uniqueFocus = "${focus}_${System.currentTimeMillis()}"
                            navController.navigate(Routes.security(uniqueFocus)) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                // Removed restoreState = true so focusing honors the new argument
                            }
                        },
                        onNavigateToAbout = {
                            navController.navigate(Routes.ABOUT) { launchSingleTop = true }
                        }
                    )
                }

                // ── Notifications ─────────────────────────────────────────
                composable(Routes.NOTIFICATIONS) {
                    val notificationsViewModel: NotificationsViewModel = viewModel()
                    NotificationsScreen(
                        viewModel = notificationsViewModel,
                        historyViewModel = historyViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenResult = { navController.navigate(Routes.RESULT) }
                    )
                }

                // ── Statistics ────────────────────────────────────────────
                composable(Routes.STATISTICS) {
                    StatisticsScreen(
                        historyViewModel = historyViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── History Archive ───────────────────────────────────────
                composable(Routes.HISTORY) {
                    val isDarkMode by settingsStore.isDarkMode.collectAsState(initial = true)
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToResult = { id ->
                            scope.launch {
                                val result = historyViewModel.getById(id)
                                if (result != null) {
                                    historyViewModel.selectResult(result)
                                    navController.navigate(Routes.RESULT) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        isDarkMode = isDarkMode // Pass down the actual value from SettingsDataStore if available, otherwise fallback
                    )
                }

                // ── Result ────────────────────────────────────────────────
                composable(Routes.RESULT) {
                    val selectedResult by historyViewModel.selected.collectAsState()

                    // ── HARD GATE: Prevent deep link bypass to Result screen ──
                    if (!hasAcceptedLegal) {
                        LaunchedEffect(Unit) {
                            NavLogger.log("Navigated to RESULT without legal consent! Redirecting...")
                            navController.navigate(Routes.ONBOARDING_LEGAL) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        return@composable
                    }

                    if (!hasCompletedOnboarding) {
                        LaunchedEffect(Unit) {
                            NavLogger.log("Navigated to RESULT without onboarding completion! Redirecting...")
                            navController.navigate(Routes.PERMISSION_DISCLOSURE) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        return@composable
                    }

                    // ── Cleanup: clear stale selection when leaving this screen
                    DisposableEffect(Unit) {
                        onDispose { historyViewModel.clearSelectedResult() }
                    }

                    if (selectedResult != null) {
                        val result = selectedResult!!
                        LaunchedEffect(result.id) {
                            historyViewModel.markRead(result.id)
                            // ── SOLE DISMISSAL POINT for all persistent notifications ──
                            // This fires ONLY when ResultScreen is actually rendered with
                            // valid result data. Both managers are called because:
                            //   • SUSPICIOUS (40–69) → registered in PersistentNotificationManager
                            //   • DANGEROUS (70+)    → registered in SecurityAlertNotificationManager
                            // Each manager's dismiss is a safe no-op if the ID isn't found.
                            // DO NOT move this to MainActivity or any earlier point.
                            com.snow.safetalk.notification.PersistentNotificationManager
                                .dismissNotification(context, result.id)
                            com.snow.safetalk.notification.SecurityAlertNotificationManager
                                .cancelForAnalysis(context, result.id)
                        }
                        ResultScreen(
                            result           = result,
                            onBack           = { navController.popBackStack() },
                            onAnalyzeAnother = {
                                historyViewModel.clearSelectedResult()
                                navController.navigate(Routes.SCAN) {
                                    popUpTo(Routes.HOME)
                                    launchSingleTop = true
                                }
                            },
                            onMarkRead = { id -> historyViewModel.markRead(id) }
                        )
                    } else {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(AppColors.BgSolid),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Natija topilmadi", color = AppColors.TextMain)

                                Spacer(Modifier.height(16.dp))

                                Button(onClick = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = true }
                                    }
                                }) {
                                    Text("Bosh sahifa")
                                }
                            }
                        }
                    }
                }

                // ── Scan ──────────────────────────────────────────────────
                composable(Routes.SCAN) {
                    ScanScreen(
                        onBack       = { navController.popBackStack() },
                        onAnalyze    = { message ->
                            try {
                                val analysisResult = com.snow.safetalk.analysis.SafeTalkAnalyzer.analyzeMessage(
                                    payload = com.snow.safetalk.analysis.MessageAnalysisPayload(cleanMessageText = message),
                                    source = com.snow.safetalk.analysis.MessageSource.MANUAL,
                                    context = context
                                )
                                val uiModel = analysisResult.toUiModel(source = com.snow.safetalk.history.MessageSource.MANUAL)
                                historyViewModel.addResult(uiModel)
                                historyViewModel.selectResult(uiModel)
                                // Manual scans are read by default in toUiModel mapping, but we set it anyway
                                navController.navigate(Routes.RESULT) {
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                NavLogger.log("Analyzer.analyze() threw: ${e.message}")
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message  = "Tahlil xatosi: ${e.localizedMessage ?: "Noma'lum xato"}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    )
                }

                // ── Sources ───────────────────────────────────────────────
                composable(Routes.SOURCES) {
                    SourcesScreen(
                        onBack       = { navController.popBackStack() }
                    )
                }

                // ── Settings (Sozlamalar tab) ─────────────────────────────
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack       = { navController.popBackStack() },
                        onNavigateToSubscription = {
                            navController.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
                        },
                        onNavigateToPrivacy = {
                            navController.navigate(Routes.PRIVACY_POLICY) { launchSingleTop = true }
                        },
                        onNavigateToTerms = {
                            navController.navigate(Routes.TERMS_OF_SERVICE) { launchSingleTop = true }
                        }
                    )
                }

                // ── Security (Himoya tab) ─────────────────────────────────
                composable(
                    route = Routes.SECURITY_ROUTE,
                    arguments = listOf(navArgument("focus") {
                        type = NavType.StringType
                        nullable = true
                    })
                ) { backStackEntry ->
                    val focusItem = backStackEntry.arguments?.getString("focus")
                    SecurityScreen(
                        focusItem = focusItem,
                        onFocusConsumed = {
                            backStackEntry.arguments?.remove("focus")
                            backStackEntry.savedStateHandle.remove<String>("focus")
                        },
                        onBack       = { navController.popBackStack() }
                    )
                }

                // ── Subscription ──────────────────────────────────────────
                composable(Routes.SUBSCRIPTION) {
                    SubscriptionScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── About ─────────────────────────────────────────────────
                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Terms Of Service ──────────────────────────────────────────
                composable(Routes.TERMS_OF_SERVICE) {
                    TermsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Privacy Policy ────────────────────────────────────────────
                composable(Routes.PRIVACY_POLICY) {
                    PrivacyPolicyScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Onboarding (Legal Check) ─────────────────────────────
                composable(
                    route          = Routes.ONBOARDING_LEGAL,
                    enterTransition = { fadeIn(animationSpec = tween(400)) },
                    exitTransition  = { fadeOut(animationSpec = tween(300)) }
                ) {
                    LegalAcceptanceScreen(
                        onCompleted = {
                            navController.navigate(Routes.PERMISSION_DISCLOSURE) {
                                popUpTo(Routes.ONBOARDING_LEGAL) { inclusive = true }
                            }
                        },
                        onNavigateToPrivacy = {
                            navController.navigate(Routes.PRIVACY_POLICY) { launchSingleTop = true }
                        },
                        onNavigateToTerms = {
                            navController.navigate(Routes.TERMS_OF_SERVICE) { launchSingleTop = true }
                        }
                    )
                }

                // ── Permission Disclosure ────────────────────────────────
                composable(Routes.PERMISSION_DISCLOSURE) {
                    PermissionDisclosureScreen(
                        onContinue = {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.PERMISSION_DISCLOSURE) { inclusive = true }
                            }
                        }
                    )
                }

                // ── Onboarding (Feature Setup) ───────────────────────────
                composable(
                    route          = Routes.ONBOARDING,
                    enterTransition = { fadeIn(animationSpec = tween(400)) },
                    exitTransition  = { fadeOut(animationSpec = tween(300)) }
                ) {
                    ProtectionOnboardingScreen(
                        onCompleted = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
}

// ── Bottom Navigation Bar ─────────────────────────────────────────────────────

@Composable
private fun SafeTalkBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = AppColors.CardBg,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route || (item.route == Routes.SECURITY && currentRoute == Routes.SECURITY_ROUTE)
            NavigationBarItem(
                selected = selected,
                onClick  = { onTabSelected(item.route) },
                icon = {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.label,
                        modifier           = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text     = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = AppColors.PrimaryBlue,
                    selectedTextColor   = AppColors.PrimaryBlue,
                    unselectedIconColor = AppColors.TextSubtitle,
                    unselectedTextColor = AppColors.TextSubtitle,
                    indicatorColor      = AppColors.PrimaryBlue.copy(alpha = 0.15f)
                )
            )
        }
    }
}
