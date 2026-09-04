package com.rrajath.grove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.rrajath.grove.R
import com.rrajath.grove.capture.ShortcutSyncer
import com.rrajath.grove.icon.AppIconManager
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.agenda.AgendaScreen
import com.rrajath.grove.ui.capture.CaptureEditorScreen
import com.rrajath.grove.ui.editor.EditNoteScreen
import com.rrajath.grove.ui.editor.EditIntroScreen
import com.rrajath.grove.ui.editor.EditPrefaceScreen
import com.rrajath.grove.ui.editor.EditRegion
import com.rrajath.grove.ui.editor.EditRegionScreen
import com.rrajath.grove.ui.capture.CapturePickerSheet
import com.rrajath.grove.ui.capture.TemplateEditScreen
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.newbadge.LocalNewBadges
import com.rrajath.grove.ui.newbadge.NewBadges
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
import com.rrajath.grove.ui.screens.settings.SettingsBugReportScreen
import com.rrajath.grove.ui.screens.settings.SettingsCaptureTemplatesScreen
import com.rrajath.grove.ui.screens.settings.SettingsDeveloperScreen
import com.rrajath.grove.ui.screens.settings.SettingsNotebooksScreen
import com.rrajath.grove.ui.screens.settings.SettingsNotesScreen
import com.rrajath.grove.ui.screens.settings.SettingsRemindersScreen
import com.rrajath.grove.ui.screens.settings.SettingsSharingScreen
import com.rrajath.grove.ui.screens.settings.SettingsSyncScreen
import com.rrajath.grove.ui.screens.settings.SettingsTipsScreen
import com.rrajath.grove.ui.screens.SyncLogScreen
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.GroveTheme
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.vault.matchOpenedFileToNotebook
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun GroveApp(
    deepLinkIntent: android.content.Intent? = null,
    /** Called with the pending [deepLinkIntent] once it has been navigated, so
     *  the host Activity can drop it and it never re-fires on a later
     *  recreation. The Activity ignores the call if a newer Intent has since
     *  arrived. */
    onDeepLinkConsumed: (android.content.Intent) -> Unit = {},
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
                val app = context.applicationContext as GroveApplication
                app.appScope.launch {
                    // Off the main thread: applyIcon does synchronous Binder IPC
                    // and ON_STOP runs on the main thread (PERFORMANCE_AUDIT #5).
                    // Runs before the shortcut re-sync, which binds shortcuts to
                    // whichever alias applyIcon enables.
                    AppIconManager.applyIcon(context, syncIcon, iconTheme)
                    // Dynamic shortcuts published while the old alias was still
                    // enabled are now stranded on it; republish against whichever
                    // alias applyIcon just switched to.
                    val templates = app.templatesRepository.templates.first()
                    ShortcutSyncer.sync(app, templates, iconTheme, syncIcon)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    GroveTheme(theme = loaded.theme) {
        // App-wide text-size baseline: scales every sp-sized text under one lever.
        // The per-mode read/edit levers nest inside this and compound on top.
        ContentFontScale(loaded.appFontSize) {
            GroveNavigation(loaded, viewModel, deepLinkIntent, onDeepLinkConsumed)
        }
    }
}

/**
 * True while this entry is the one on screen. Guards a dismiss/navigation
 * callback that can fire a second time after the entry is already popped (a fast
 * double back-press): a popped entry drops below RESUMED, so the late callback
 * no-ops instead of popping another entry off the back stack.
 */
private fun androidx.navigation.NavBackStackEntry.isResumed() =
    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

/** Favorites scoped to [fileName], for resolving each row's ★ by customId rather than raw line index. */
private fun favoritesFor(
    favorites: List<com.rrajath.grove.data.FavoriteNote>,
    fileName: String,
): List<com.rrajath.grove.data.FavoriteNote> = favorites.filter { it.fileName == fileName }

/**
 * The file name of an externally-opened .org file, e.g. from tapping one in a
 * file manager. `content://` URIs from other providers carry a display name
 * via [android.provider.OpenableColumns.DISPLAY_NAME] rather than a usable
 * path segment (the last segment is often an opaque document id); `file://`
 * URIs have no content resolver row, so the path segment is all there is.
 */
private fun externalOrgFileName(context: android.content.Context, uri: android.net.Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}

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
    onDeepLinkConsumed: (android.content.Intent) -> Unit = {},
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
    val consumeDeepLink by rememberUpdatedState(onDeepLinkConsumed)
    LaunchedEffect(deepLinkIntent) {
        val intent = deepLinkIntent ?: return@LaunchedEffect
        val uri = intent.data
        try {
            val action = intent.action
            if (uri == null) return@LaunchedEffect
            if (action == android.content.Intent.ACTION_VIEW && uri.scheme == "grove") {
                navController.handleDeepLink(intent)
                return@LaunchedEffect
            }
            // A .org file opened from outside Grove (file manager, "Open with",
            // or Grove set as its default handler; see the file-open intent-filter
            // on MainActivity in the manifest). This isn't one of the grove://
            // NavDeepLinks above -- there's no route pattern an arbitrary
            // content:// / file:// URI could match -- so it's resolved by hand:
            // match the tapped file's name against a notebook already indexed in
            // the vault and open its outline.
            if ((action == android.content.Intent.ACTION_VIEW || action == android.content.Intent.ACTION_EDIT) &&
                (uri.scheme == "content" || uri.scheme == "file")
            ) {
                val requestedName = externalOrgFileName(app, uri)
                val vault = withTimeoutOrNull(5_000) { app.vault.filterNotNull().first() }
                val match = requestedName?.let { name ->
                    vault?.let { matchOpenedFileToNotebook(name, it.notebooks()) }
                }
                if (match != null) {
                    navController.navigate(Routes.outline(match.fileName)) { launchSingleTop = true }
                } else {
                    android.widget.Toast.makeText(
                        app,
                        if (requestedName != null) {
                            "\"$requestedName\" isn't in your ${app.getString(R.string.app_name)} vault folder."
                        } else {
                            "Couldn't open that file in ${app.getString(R.string.app_name)}."
                        },
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } finally {
            // Navigated (or found nothing to do): the Intent is spent. Clearing
            // it here is what stops a cancelled capture from returning after a
            // process-death recreation or an uncovered configuration change.
            // Passes the exact Intent handled so a newer one that arrived
            // mid-resolve (cancelling this effect) is left untouched.
            consumeDeepLink(intent)
        }
    }

    fun closeDrawerAnd(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    val newBadgeState by viewModel.newBadgeState.collectAsStateWithLifecycle()
    val newBadges = NewBadges(newBadgeState) { ids -> viewModel.markNewFeaturesSeen(ids) }

    CompositionLocalProvider(LocalNewBadges provides newBadges) {
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
                    onRenameSavedSearch = { id, name -> viewModel.renameSavedSearch(id, name) },
                    onMoveSavedSearch = { id, delta -> viewModel.moveSavedSearch(id, delta) },
                    onDeleteFavorite = { viewModel.removeFavorite(it.fileName, it.lineIndex, it.customId) },
                    onRenameFavorite = { fav, title -> viewModel.renameFavorite(fav.fileName, fav.lineIndex, title, fav.customId) },
                    onMoveFavorite = { fav, delta -> viewModel.moveFavorite(fav.fileName, fav.lineIndex, delta, fav.customId) },
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
                    // The outline's ★ swipe action: OutlineScreen decides add vs. remove
                    // itself (it already resolves each row's favorite by customId to draw
                    // the star correctly) and, for adds, resolves a stable id first via
                    // viewModel.ensureCustomId before calling onFavorite.
                    onFavorite = { fileName, lineIndex, title, customId ->
                        viewModel.addFavorite(fileName, lineIndex, title, customId)
                    },
                    onUnfavorite = { fileName, lineIndex, customId ->
                        viewModel.removeFavorite(fileName, lineIndex, customId)
                    },
                    favorites = favoritesFor(favorites, notebookId),
                    displayFlags = OutlineDisplayFlags(
                        tags = settings.showTagsInOutline,
                        timestamps = settings.showTimestampsInOutline,
                        keywords = settings.showKeywordsInOutline,
                    ),
                    onToggleDisplay = viewModel::setOutlineToggle,
                    showPreface = settings.showPreface,
                    showPropertyDrawers = settings.showPropertyDrawers,
                    onOpenPreface = { fileName -> navController.navigate(Routes.preface(fileName)) },
                    onOpenFileProperties = { fileName ->
                        navController.navigate(Routes.drawer(fileName, "fileProps"))
                    },
                )
            }
            composable(Routes.PREFACE) { entry ->
                val fileName = entry.arguments?.getString("fileName").orEmpty()
                EditPrefaceScreen(
                    fileName = fileName,
                    onBack = { navController.popBackStack() },
                    editModeFontSize = settings.editModeFontSize,
                )
            }
            composable(Routes.DRAWER) { entry ->
                val fileName = entry.arguments?.getString("fileName").orEmpty()
                val noteId = entry.arguments?.getString("noteId")
                val region = when (entry.arguments?.getString("kind")) {
                    "headingProps" -> EditRegion.HEADING_PROPERTIES
                    "headingLog" -> EditRegion.HEADING_LOGBOOK
                    else -> EditRegion.FILE_PROPERTIES
                }
                EditRegionScreen(
                    fileName = fileName,
                    region = region,
                    noteId = noteId,
                    onBack = { navController.popBackStack() },
                    editModeFontSize = settings.editModeFontSize,
                )
            }
            composable(Routes.BLOCK) { entry ->
                EditRegionScreen(
                    fileName = entry.arguments?.getString("fileName").orEmpty(),
                    region = EditRegion.BLOCK,
                    noteId = null,
                    blockLine = entry.arguments?.getString("line")?.toIntOrNull() ?: -1,
                    onBack = { navController.popBackStack() },
                    editModeFontSize = settings.editModeFontSize,
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
                    // Line of the subheading double-tapped in read mode, if any; carried
                    // alongside `mode` (not a nav arg) so the mode toggle keeps not
                    // re-navigating/re-triggering the enter/exit transition.
                    var editTargetLine by rememberSaveable(noteId) { mutableStateOf<Int?>(null) }
                    if (mode == "edit" && ref.isIntro) {
                        // The intro has no heading to edit as a subtree: its editor is
                        // scoped to just that content, since the preface and the file's
                        // property drawer have editors of their own. Back returns to the
                        // intro read view.
                        EditIntroScreen(
                            fileName = ref.fileName,
                            onBack = { mode = "read" },
                            editModeFontSize = settings.editModeFontSize,
                        )
                    } else if (mode == "edit") {
                        EditNoteScreen(
                            noteRef = ref,
                            isNewNote = isNew,
                            initialCursorLine = editTargetLine,
                            editModeFontSize = settings.editModeFontSize,
                            newNoteCursor = settings.newNoteCursor,
                            onBack = { navController.popBackStack() },
                            onSwitchToRead = { editTargetLine = null; mode = "read" },
                        )
                    } else {
                        ReadNoteScreen(
                            noteRef = ref,
                            onBack = { navController.popBackStack() },
                            onOpenNote = { target -> navController.navigate(Routes.note(target.encode())) },
                            onOpenOutline = { fileName -> navController.navigate(Routes.outline(fileName)) },
                            onEdit = { targetLine -> editTargetLine = targetLine; mode = "edit" },
                            // null (file breadcrumb) opens the full outline; a heading's
                            // line index narrows the outline to that heading's subtree.
                            onOpenBreadcrumb = { targetLine ->
                                navController.navigate(Routes.outline(ref.fileName, targetLine))
                            },
                            onOpenDrawer = { kind, drawerRef ->
                                navController.navigate(Routes.drawer(drawerRef.fileName, kind, drawerRef.encode()))
                            },
                            onOpenBlock = { fileName, line ->
                                navController.navigate(Routes.block(fileName, line))
                            },
                            showPropertyDrawers = settings.showPropertyDrawers,
                            readModeFontSize = settings.readModeFontSize,
                            favorites = favoritesFor(favorites, ref.fileName),
                            // The intro just got a blank heading (a metadata action
                            // needed one); re-open the file at that heading so it
                            // continues as an ordinary note, replacing this entry.
                            onPromotedToHeading = { line ->
                                navController.navigate(
                                    Routes.note(NoteRef(ref.fileName, line).encode(), mode = "read")
                                ) {
                                    popUpTo(entry.destination.route ?: Routes.NOTE) { inclusive = true }
                                }
                            },
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
            ) { entry ->
                CapturePickerSheet(
                    // A quick double back-press used to pop twice: once from the
                    // sheet's own onDismissRequest and once from the system back
                    // dispatcher. On the widget's shallow deep-link back stack
                    // that popped past the start destination and left the NavHost
                    // empty (a blank white screen). Ignore the second callback:
                    // the entry is no longer RESUMED once it has been popped.
                    onDismiss = { if (entry.isResumed()) navController.popBackStack() },
                    onPickTemplate = { template ->
                        navController.navigate(Routes.capture(template.id)) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                        }
                    },
                    onManage = {
                        navController.navigate(Routes.SETTINGS_CAPTURE_TEMPLATES) {
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
                    editModeFontSize = settings.editModeFontSize,
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
                    onOpenOutline = { fileName -> navController.navigate(Routes.outline(fileName)) },
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
                    onOpenNotebooks = { navController.navigate(Routes.SETTINGS_NOTEBOOKS) },
                    onOpenCaptureTemplates = { navController.navigate(Routes.SETTINGS_CAPTURE_TEMPLATES) },
                    onOpenSync = { navController.navigate(Routes.SETTINGS_SYNC) },
                    onOpenNotes = { navController.navigate(Routes.SETTINGS_NOTES) },
                    onOpenAgenda = { navController.navigate(Routes.SETTINGS_AGENDA) },
                    onOpenReminders = { navController.navigate(Routes.SETTINGS_REMINDERS) },
                    onOpenSharing = { navController.navigate(Routes.SETTINGS_SHARING) },
                    onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                    onOpenBugReport = { navController.navigate(Routes.SETTINGS_BUG_REPORT) },
                    onOpenTips = { navController.navigate(Routes.SETTINGS_TIPS) },
                    onOpenDeveloper = { navController.navigate(Routes.SETTINGS_DEVELOPER) },
                )
            }
            composable(Routes.SETTINGS_APPEARANCE) {
                SettingsAppearanceScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetTheme = viewModel::setTheme,
                    onSetSyncAppIconWithTheme = viewModel::setSyncAppIconWithTheme,
                    onSetAppFontSize = viewModel::setAppFontSize,
                )
            }
            composable(Routes.SETTINGS_NOTEBOOKS) {
                SettingsNotebooksScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetShowNotebookFileIcons = viewModel::setShowNotebookFileIcons,
                    onSetFlattenNotebookFolders = viewModel::setFlattenNotebookFolders,
                    onSetNotebookDisplayNameMode = viewModel::setNotebookDisplayNameMode,
                    onSetNotebookSortKey = viewModel::setNotebookSortKey,
                    onSetNotebookSortAscending = viewModel::setNotebookSortAscending,
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
                    onSetNoteOpenMode = viewModel::setDefaultNoteOpenMode,
                    onSetReadModeFontSize = viewModel::setReadModeFontSize,
                    onSetEditModeFontSize = viewModel::setEditModeFontSize,
                    onSetShowPreface = viewModel::setShowPreface,
                    onSetShowPropertyDrawers = viewModel::setShowPropertyDrawers,
                    onSetAddId = viewModel::setAddIdToNewNotes,
                    onSetAddCreated = viewModel::setAddCreatedToNewNotes,
                    onSetNewNoteCursor = viewModel::setNewNoteCursor,
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
                    onSetAgendaWidgetTransparency = viewModel::setAgendaWidgetTransparency,
                    onSetAgendaWidgetDaysAhead = viewModel::setAgendaWidgetDaysAhead,
                )
            }
            composable(Routes.SETTINGS_REMINDERS) {
                SettingsRemindersScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onSetRemindersEnabled = viewModel::setRemindersEnabled,
                    onSetMorningBriefEnabled = viewModel::setMorningBriefEnabled,
                    onSetDefaultReminderTime = viewModel::setDefaultReminderTime,
                    onSetReminderLeadTime = viewModel::setReminderLeadTime,
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
            composable(Routes.SETTINGS_BUG_REPORT) {
                SettingsBugReportScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_TIPS) {
                SettingsTipsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_DEVELOPER) {
                SettingsDeveloperScreen(
                    onBack = { navController.popBackStack() },
                    onResetNewBadges = viewModel::resetNewBadges,
                )
            }
        }
    }
    }

    LaunchedEffect(settings.onboardingDone) {
        if (settings.onboardingDone) {
            viewModel.checkWhatsNew()
            viewModel.ensureNewBadgeBaseline()
        }
    }
    val whatsNew by viewModel.whatsNew.collectAsStateWithLifecycle()
    if (whatsNew.isNotEmpty()) {
        com.rrajath.grove.ui.screens.WhatsNewDialog(versions = whatsNew, onDismiss = viewModel::dismissWhatsNew)
    }
}
