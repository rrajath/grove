package com.rrajath.grove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rrajath.grove.icon.AppIconManager
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.capture.CaptureEditorScreen
import com.rrajath.grove.ui.editor.EditNoteScreen
import com.rrajath.grove.ui.capture.CapturePickerSheet
import com.rrajath.grove.ui.capture.TemplateEditScreen
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.screens.ConflictScreen
import com.rrajath.grove.ui.screens.GroveDrawerContent
import com.rrajath.grove.ui.screens.NotebooksScreen
import com.rrajath.grove.ui.screens.OnboardingScreen
import com.rrajath.grove.ui.screens.OutlineDisplayFlags
import com.rrajath.grove.ui.screens.OutlineScreen
import com.rrajath.grove.ui.screens.ReadNoteScreen
import com.rrajath.grove.ui.search.SearchScreen
import com.rrajath.grove.ui.screens.SettingsScreen
import com.rrajath.grove.ui.screens.SyncLogScreen
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.theme.GroveTheme
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.launch

@Composable
fun GroveApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Wait for the first DataStore emission so theme and start destination don't flash.
    val loaded = settings ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    // Switching the enabled launcher alias closes the app's task, so defer it
    // until the app goes to the background (ON_STOP) instead of applying it live.
    val syncIcon by rememberUpdatedState(loaded.syncAppIconWithTheme)
    val iconTheme by rememberUpdatedState(loaded.theme)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                AppIconManager.applyIcon(context, syncIcon, iconTheme)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    GroveTheme(theme = loaded.theme, fontSize = loaded.fontSize) {
        GroveNavigation(loaded, viewModel)
    }
}

/** Line indices of favorited headlines in [fileName]. */
private fun favoriteLinesFor(
    favorites: List<com.rrajath.grove.data.FavoriteNote>,
    fileName: String,
): Set<Int> = favorites.filter { it.fileName == fileName }.map { it.lineIndex }.toSet()

/**
 * Human-readable form of the persisted SAF tree URI for the drawer header,
 * e.g. "primary:Documents/org" → "~/Documents/org".
 */
private fun vaultDisplayPath(treeUri: String?): String {
    if (treeUri == null) return "no folder selected"
    val docId = runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(treeUri))
    }.getOrNull() ?: return treeUri
    val path = docId.substringAfter(':', docId).ifEmpty { "(storage root)" }
    return if (docId.startsWith("primary:")) "~/$path" else path
}

@Composable
private fun GroveNavigation(settings: GroveSettings, viewModel: AppViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.rrajath.grove.GroveApplication

    // Shared-into-Grove content is appended to the configured file directly
    // (PRD §10) — observed so it works even when the app was already running.
    val pendingShare by app.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) viewModel.consumeSharedContent()
    }

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
                    vaultPath = vaultDisplayPath(settings.vaultTreeUri),
                    savedSearches = viewModel.savedSearches.collectAsStateWithLifecycle().value,
                    favorites = favorites,
                    logoFollowsTheme = settings.syncAppIconWithTheme,
                    onNavigate = { route -> closeDrawerAnd { navController.navigate(route) } },
                    onDeleteSavedSearch = { viewModel.deleteSavedSearch(it.id) },
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
                    onFolderPicked = viewModel::setVaultTreeUri,
                )
            }
            composable(Routes.NOTEBOOKS) {
                NotebooksScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = { navController.navigate(Routes.search()) },
                    onOpenCapture = { navController.navigate(Routes.CAPTURE) },
                    onOpenNotebook = { id -> navController.navigate(Routes.outline(id)) },
                    onOpenConflict = { id -> navController.navigate(Routes.conflict(id)) },
                )
            }
            composable(Routes.OUTLINE) { entry ->
                val notebookId = entry.arguments?.getString("notebookId").orEmpty()
                OutlineScreen(
                    notebookId = notebookId,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { ref ->
                        // Always open in the mode configured in Settings.
                        navController.navigate(Routes.note(ref.encode(), settings.defaultNoteOpenMode.storageKey))
                    },
                    // A freshly created note opens straight in edit mode (blank heading).
                    onCreateNote = { ref -> navController.navigate(Routes.note(ref.encode(), "edit", isNew = true)) },
                    // Toggle: the outline's ★ swipe action both adds and removes.
                    onFavorite = { fileName, lineIndex, title ->
                        if (favorites.any { it.fileName == fileName && it.lineIndex == lineIndex }) {
                            viewModel.removeFavorite(fileName, lineIndex)
                        } else {
                            viewModel.addFavorite(fileName, lineIndex, title)
                        }
                    },
                    favoriteLines = favoriteLinesFor(favorites, notebookId),
                    displayFlags = OutlineDisplayFlags(
                        tags = settings.showTagsInOutline,
                        timestamps = settings.showTimestampsInOutline,
                        keywords = settings.showKeywordsInOutline,
                    ),
                    onToggleDisplay = viewModel::setOutlineToggle,
                )
            }
            composable(
                Routes.NOTE,
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "grove://note/{noteId}?mode={mode}&isNew={isNew}" },
                ),
            ) { entry ->
                val noteId = entry.arguments?.getString("noteId").orEmpty()
                val mode = entry.arguments?.getString("mode") ?: "read"
                val isNew = entry.arguments?.getString("isNew") == "true"
                val ref = NoteRef.decode(noteId)
                if (ref == null) {
                    navController.popBackStack()
                } else if (mode == "edit") {
                    EditNoteScreen(
                        noteRef = ref,
                        isNewNote = isNew,
                        onBack = { navController.popBackStack() },
                        onSwitchToRead = {
                            navController.navigate(Routes.note(ref.encode(), "read")) {
                                popUpTo(Routes.NOTE) { inclusive = true }
                            }
                        },
                    )
                } else {
                    ReadNoteScreen(
                        noteRef = ref,
                        onBack = { navController.popBackStack() },
                        onOpenNote = { target -> navController.navigate(Routes.note(target.encode())) },
                        onEdit = {
                            navController.navigate(Routes.note(ref.encode(), "edit")) {
                                popUpTo(Routes.NOTE) { inclusive = true }
                            }
                        },
                        showHeaderTags = settings.showHeaderTags,
                        showPropertyDrawers = settings.showPropertyDrawers,
                        favoriteLines = favoriteLinesFor(favorites, ref.fileName),
                    )
                }
            }
            composable(
                Routes.CAPTURE,
                deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "grove://capture" }),
            ) {
                CapturePickerSheet(
                    onDismiss = { navController.popBackStack() },
                    onPickTemplate = { template ->
                        navController.navigate(Routes.capture(template.id)) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                        }
                    },
                    onManage = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                Routes.CAPTURE_TEMPLATE,
                // Launcher shortcuts (and any caller) can jump straight into a
                // specific template's editor: grove://capture/builtin-journal etc.
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "grove://capture/{templateId}" },
                ),
            ) { entry ->
                CaptureEditorScreen(
                    templateId = entry.arguments?.getString("templateId").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.TEMPLATE_EDIT) { entry ->
                TemplateEditScreen(
                    templateId = entry.arguments?.getString("templateId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SYNC_LOG) {
                SyncLogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) { entry ->
                SearchScreen(
                    initialQuery = entry.arguments?.getString("q"),
                    onBack = { navController.popBackStack() },
                    onOpenNote = { ref -> navController.navigate(Routes.note(ref.encode())) },
                )
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
                    onSetSyncAppIconWithTheme = viewModel::setSyncAppIconWithTheme,
                    onSetShowHeaderTags = viewModel::setShowHeaderTags,
                    onSetShowPropertyDrawers = viewModel::setShowPropertyDrawers,
                    onSetFontSize = viewModel::setFontSize,
                    onSetNoteOpenMode = viewModel::setDefaultNoteOpenMode,
                    onEditTemplate = { id ->
                        navController.navigate(Routes.templateEdit(id ?: Routes.NEW_TEMPLATE_ID))
                    },
                    onSetSyncMode = viewModel::setSyncMode,
                    onSetPeriodicMinutes = viewModel::setPeriodicSyncMinutes,
                    onOpenSyncLog = { navController.navigate(Routes.SYNC_LOG) },
                    onSetTodoKeywords = viewModel::setTodoKeywords,
                    onSetDefaultPriority = viewModel::setDefaultPriority,
                    onSetAddId = viewModel::setAddIdToNewNotes,
                    onSetAddCreated = viewModel::setAddCreatedToNewNotes,
                    onSetCaptureNotification = viewModel::setCaptureNotification,
                    onSetVaultUri = viewModel::setVaultTreeUri,
                    onSetShareTargetFile = viewModel::setShareTargetFile,
                    onExportSettings = viewModel::exportSettings,
                    onImportSettings = viewModel::importSettings,
                )
            }
        }
    }
}
