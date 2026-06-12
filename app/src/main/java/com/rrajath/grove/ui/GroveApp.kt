package com.rrajath.grove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.screens.CaptureScreen
import com.rrajath.grove.ui.screens.ConflictScreen
import com.rrajath.grove.ui.screens.GroveDrawerContent
import com.rrajath.grove.ui.screens.NotebooksScreen
import com.rrajath.grove.ui.screens.NoteScreen
import com.rrajath.grove.ui.screens.OnboardingScreen
import com.rrajath.grove.ui.screens.OutlineScreen
import com.rrajath.grove.ui.screens.SearchScreen
import com.rrajath.grove.ui.screens.SettingsScreen
import com.rrajath.grove.ui.theme.GroveTheme
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.launch

@Composable
fun GroveApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val settings by viewModel.settings.collectAsState()
    // Wait for the first DataStore emission so theme and start destination don't flash.
    val loaded = settings ?: return
    GroveTheme(theme = loaded.theme, fontSize = loaded.fontSize) {
        GroveNavigation(loaded, viewModel)
    }
}

@Composable
private fun GroveNavigation(settings: GroveSettings, viewModel: AppViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun closeDrawerAnd(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == Routes.NOTEBOOKS,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.grove.surface,
                modifier = Modifier.background(MaterialTheme.grove.surface),
            ) {
                GroveDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route -> closeDrawerAnd { navController.navigate(route) } },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = if (settings.onboardingDone) Routes.NOTEBOOKS else Routes.ONBOARDING,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.grove.bg),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        viewModel.completeOnboarding()
                        navController.navigate(Routes.NOTEBOOKS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.NOTEBOOKS) {
                NotebooksScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenCapture = { navController.navigate(Routes.CAPTURE) },
                    onOpenNotebook = { id -> navController.navigate(Routes.outline(id)) },
                )
            }
            composable(Routes.OUTLINE) { entry ->
                OutlineScreen(
                    notebookId = entry.arguments?.getString("notebookId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTE) { entry ->
                NoteScreen(
                    noteId = entry.arguments?.getString("noteId").orEmpty(),
                    mode = entry.arguments?.getString("mode") ?: "read",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CAPTURE) {
                CaptureScreen(templateId = null, onBack = { navController.popBackStack() })
            }
            composable(Routes.CAPTURE_TEMPLATE) { entry ->
                CaptureScreen(
                    templateId = entry.arguments?.getString("templateId"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CONFLICT) { entry ->
                ConflictScreen(
                    notebookId = entry.arguments?.getString("notebookId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetTheme = viewModel::setTheme,
                    onSetFontSize = viewModel::setFontSize,
                    onSetNoteOpenMode = viewModel::setDefaultNoteOpenMode,
                )
            }
        }
    }
}
