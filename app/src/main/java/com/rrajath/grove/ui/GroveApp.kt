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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.capture.ShortcutSyncer
import com.rrajath.grove.icon.AppIconManager
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.agenda.AgendaScreen
import com.rrajath.grove.ui.capture.CaptureEditorScreen
import com.rrajath.grove.ui.editor.EditNoteScreen
import com.rrajath.grove.ui.capture.CapturePickerSheet
import com.rrajath.grove.ui.capture.TemplateEditScreen
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.nav.navEnterTransition
import com.rrajath.grove.ui.nav.navExitTransition
import com.rrajath.grove.ui.nav.navPopEnterTransition
import com.rrajath.grove.ui.nav.navPopExitTransition
import com.rrajath.grove.ui.reminders.ReminderResolveScreen
import com.rrajath.grove.ui.screens.ConflictScreen
import com.rrajath.grove.ui.screens.GroveDrawerContent
import com.rrajath.grove.ui.screens.NotebooksScreen
import com.rrajath.grove.ui.screens.OnboardingScreen
import com.rrajath.grove.ui.screens.OutlineDisplayFlags
import com.rrajath.grove.ui.screens.OutlineScreen
import com.rrajath.grove.ui.screens.ReadNoteScreen
import com.rrajath.grove.ui.screens.RefileSheet
import com.rrajath.grove.ui.search.SearchScreen
import com.rrajath.grove.ui.screens.SettingsScreen
import com.rrajath.grove.ui.screens.settings.SettingsAgendaScreen
import com.rrajath.grove.ui.screens.settings.SettingsAppearanceScreen
import com.rrajath.grove.ui.screens.settings.SettingsBackupScreen
import com.rrajath.grove.ui.screens.settings.SettingsCaptureTemplatesScreen
import com.rrajath.grove.ui.screens.settings.SettingsNotesScreen
import com.rrajath.grove.ui.screens.settings.SettingsRemindersScreen
import com.rrajath.grove.ui.screens.settings.SettingsSharingScreen
import com.rrajath.grove.ui.screens.settings.SettingsSyncScreen
import com.rrajath.grove.ui.screens.SyncLogScreen
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.theme.GroveTheme
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun GroveApp(
    deepLinkIntent: android.content.Intent? = null,
    viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory),
) {
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
                // Dynamic shortcuts published while the old alias was still
                // enabled are now stranded on it; republish against whichever
                // alias applyIcon just switched to.
                val app = context.applicationContext as GroveApplication
                app.appScope.launch {
                    val templates = app.templatesRepository.templates.first()
                    ShortcutSyncer.sync(app, templates, iconTheme, syncIcon)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    GroveTheme(theme = loaded.theme, fontSize = loaded.fontSize) {
        GroveNavigation(loaded, viewModel, deepLinkIntent)
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
private fun GroveNavigation(
    settings: GroveSettings,
    viewModel: AppViewModel,
    deepLinkIntent: android.content.Intent? = null,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.rrajath.grove.GroveApplication

    // Shared-into-Grove content is appended to the configured file directly
    // (PRD §10); observed so it works even when the app was already running.
    val pendingShare by app.pendingShare.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) viewModel.consumeSharedContent()
    }

    // Launcher shortcuts, the capture widget, and the persistent capture
    // notification all launch grove:// VIEW intents; NavHost doesn't consume
    // the hosting Activity's intent on its own, so route it through here on
    // both cold start and a warm-start onNewIntent (see MainActivity).
    LaunchedEffect(deepLinkIntent) {
        if (deepLinkIntent?.action == android.content.Intent.ACTION_VIEW && deepLinkIntent.data != null) {
            navController.handleDeepLink(deepLinkIntent)
        }
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
            // Predictive-back fade-through (see ui/nav/NavTransitions.kt). NavHost
            // seeks these with the system back gesture's progress, so the previous
            // screen fades in as far as the user has dragged and rewinds on release.
            enterTransition = navEnterTransition,
            exitTransition = navExitTransition,
            popEnterTransition = navPopEnterTransition,
            popExitTransition = navPopExitTransition,
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
                val narrowTo = entry.arguments?.getString("narrowTo")?.toIntOrNull()
                OutlineScreen(
                    notebookId = notebookId,
                    narrowLineIndex = narrowTo,
                    onBack = { navController.popBackStack() },
                    onWiden = {
                        navController.navigate(Routes.outline(notebookId)) {
                            popUpTo(Routes.OUTLINE) { inclusive = true }
                        }
                    },
                    onOpenNote = { ref ->
                        // Always open in the mode configured in Settings.
                        navController.navigate(Routes.note(ref.encode(), settings.defaultNoteOpenMode.storageKey))
                    },
                    // A freshly created note opens straight in edit mode (blank heading).
                    onCreateNote = { ref -> navController.navigate(Routes.note(ref.encode(), "edit", isNew = true)) },
                    onSearchInNotebook = { navController.navigate(Routes.search(notebook = notebookId)) },
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
                    showPreface = settings.showPreface,
                )
            }
            composable(
                Routes.NOTE,
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "grove://note/{noteId}?mode={mode}&isNew={isNew}" },
                ),
            ) { entry ->
                val noteId = entry.arguments?.getString("noteId").orEmpty()
                val isNew = entry.arguments?.getString("isNew") == "true"
                val ref = NoteRef.decode(noteId)
                if (ref == null) {
                    navController.popBackStack()
                } else {
                    // Local, not a nav argument: switching read <-> edit for this
                    // same note must not re-navigate, or it re-triggers the full-screen
                    // enter/exit transition meant for moving between distinct screens.
                    var mode by rememberSaveable(noteId) {
                        mutableStateOf(entry.arguments?.getString("mode") ?: "read")
                    }
                    if (mode == "edit") {
                        EditNoteScreen(
                            noteRef = ref,
                            isNewNote = isNew,
                            onBack = { navController.popBackStack() },
                            onSwitchToRead = { mode = "read" },
                        )
                    } else {
                        ReadNoteScreen(
                            noteRef = ref,
                            onBack = { navController.popBackStack() },
                            onOpenNote = { target -> navController.navigate(Routes.note(target.encode())) },
                            onEdit = { mode = "edit" },
                            // null (file breadcrumb) opens the full outline; a heading's
                            // line index narrows the outline to that heading's subtree.
                            onOpenBreadcrumb = { targetLine ->
                                navController.navigate(Routes.outline(ref.fileName, targetLine))
                            },
                            showPropertyDrawers = settings.showPropertyDrawers,
                            checklistStates = settings.checklistStates,
                            favoriteLines = favoriteLinesFor(favorites, ref.fileName),
                        )
                    }
                }
            }
            composable(
                Routes.REMINDER,
                deepLinks = listOf(
                    androidx.navigation.navDeepLink {
                        uriPattern = "grove://reminder/{fileName}?headingPath={headingPath}&level={level}"
                    },
                ),
            ) { entry ->
                ReminderResolveScreen(
                    fileName = entry.arguments?.getString("fileName").orEmpty(),
                    headingPath = entry.arguments?.getString("headingPath").orEmpty(),
                    level = entry.arguments?.getString("level")?.toIntOrNull() ?: 1,
                    onResolved = { ref ->
                        navController.navigate(
                            Routes.note(ref.encode(), mode = "read")
                        ) { popUpTo(Routes.REMINDER) { inclusive = true } }
                    },
                    onFailed = {
                        navController.navigate(Routes.NOTEBOOKS) {
                            popUpTo(Routes.REMINDER) { inclusive = true }
                        }
                    },
                )
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
                    initialNotebook = entry.arguments?.getString("notebook"),
                    onBack = { navController.popBackStack() },
                    onOpenNote = { ref -> navController.navigate(Routes.note(ref.encode())) },
                )
            }
            composable(
                Routes.AGENDA,
                deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "grove://agenda" }),
            ) {
                AgendaScreen(
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
                    onBack = { navController.popBackStack() },
                    onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onOpenCaptureTemplates = { navController.navigate(Routes.SETTINGS_CAPTURE_TEMPLATES) },
                    onOpenSync = { navController.navigate(Routes.SETTINGS_SYNC) },
                    onOpenNotes = { navController.navigate(Routes.SETTINGS_NOTES) },
                    onOpenAgenda = { navController.navigate(Routes.SETTINGS_AGENDA) },
                    onOpenReminders = { navController.navigate(Routes.SETTINGS_REMINDERS) },
                    onOpenSharing = { navController.navigate(Routes.SETTINGS_SHARING) },
                    onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                )
            }
            composable(Routes.SETTINGS_APPEARANCE) {
                SettingsAppearanceScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetTheme = viewModel::setTheme,
                    onSetSyncAppIconWithTheme = viewModel::setSyncAppIconWithTheme,
                    onSetShowPreface = viewModel::setShowPreface,
                    onSetShowPropertyDrawers = viewModel::setShowPropertyDrawers,
                    onSetFontSize = viewModel::setFontSize,
                    onSetNoteOpenMode = viewModel::setDefaultNoteOpenMode,
                )
            }
            composable(Routes.SETTINGS_CAPTURE_TEMPLATES) {
                SettingsCaptureTemplatesScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onEditTemplate = { id ->
                        navController.navigate(Routes.templateEdit(id ?: Routes.NEW_TEMPLATE_ID))
                    },
                    onSetCaptureNotification = viewModel::setCaptureNotification,
                )
            }
            composable(Routes.SETTINGS_SYNC) {
                SettingsSyncScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetSyncMode = viewModel::setSyncMode,
                    onSetPeriodicMinutes = viewModel::setPeriodicSyncMinutes,
                    onOpenSyncLog = { navController.navigate(Routes.SYNC_LOG) },
                    onSetVaultUri = viewModel::setVaultTreeUri,
                )
            }
            composable(Routes.SETTINGS_NOTES) {
                SettingsNotesScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetTodoKeywords = viewModel::setTodoKeywords,
                    onSetDefaultPriority = viewModel::setDefaultPriority,
                    onSetNotebookDisplayNameMode = viewModel::setNotebookDisplayNameMode,
                    onSetChecklistStates = viewModel::setChecklistStates,
                    onSetAddId = viewModel::setAddIdToNewNotes,
                    onSetAddCreated = viewModel::setAddCreatedToNewNotes,
                    onSetAutoArchiveDoneItems = viewModel::setAutoArchiveDoneItems,
                    onOpenArchiveLocationPicker = viewModel::startArchiveLocationPick,
                )
                val archiveLocationPicker by viewModel.archiveLocationPicker.collectAsStateWithLifecycle()
                archiveLocationPicker?.let { picker ->
                    RefileSheet(
                        state = picker,
                        currentFileName = null,
                        currentDoc = null,
                        onPickNotebook = viewModel::archiveLocationPickNotebook,
                        onDrillInto = viewModel::archiveLocationDrillInto,
                        onBack = viewModel::archiveLocationBack,
                        onCancel = viewModel::archiveLocationCancel,
                        onConfirm = viewModel::archiveLocationConfirm,
                        onArchive = {},
                        onPickLastUsed = {},
                        headerTitle = "Set archive location",
                        confirmLabel = "Set as archive location",
                    )
                }
            }
            composable(Routes.SETTINGS_AGENDA) {
                SettingsAgendaScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetAgendaSwipeLeftAction = viewModel::setAgendaSwipeLeftAction,
                    onSetAgendaSwipeRightAction = viewModel::setAgendaSwipeRightAction,
                )
            }
            composable(Routes.SETTINGS_REMINDERS) {
                SettingsRemindersScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetRemindersEnabled = viewModel::setRemindersEnabled,
                    onSetMorningBriefEnabled = viewModel::setMorningBriefEnabled,
                    onSetDefaultReminderTime = viewModel::setDefaultReminderTime,
                    reminderPendingCount = viewModel.reminderPendingCount.collectAsStateWithLifecycle().value,
                )
            }
            composable(Routes.SETTINGS_SHARING) {
                SettingsSharingScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetShareTargetFile = viewModel::setShareTargetFile,
                )
            }
            composable(Routes.SETTINGS_BACKUP) {
                SettingsBackupScreen(
                    onBack = { navController.popBackStack() },
                    onExportSettings = viewModel::exportSettings,
                    onImportSettings = viewModel::importSettings,
                )
            }
        }
    }
}
