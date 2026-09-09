package com.rrajath.grove.ui.newbadge

/**
 * One newly-shipped part of the app that should carry a "NEW" badge on every
 * navigational element leading to it, until the user reaches it.
 *
 * The badge shows only for installs that were already on an *older* build than
 * [since] — i.e. app updates, never fresh installs (see [NewBadgeState]).
 *
 * @param id stable identifier; also the token stored in
 *   [com.rrajath.grove.settings.GroveSettings.seenNewFeatures].
 * @param since the `versionCode` this feature shipped in (the numeric form of
 *   `versionName`, `MAJOR*10000 + MINOR*100 + PATCH` — see CHANGELOG.md).
 * @param anchors every anchor key that should show the badge (drawer item,
 *   settings row, the destination itself, …).
 * @param destination the anchor whose appearance on screen retires the feature:
 *   once the user reaches it, the badge clears from every anchor at once.
 */
data class NewFeature(
    val id: String,
    val since: Int,
    val anchors: Set<String>,
    val destination: String,
)

/**
 * Anchor keys a [NewDot] can attach to. Plain strings so any composable — even
 * a deep one that knows nothing about the feature registry — can render a dot
 * for one.
 */
object NewAnchors {
    const val DRAWER_SETTINGS = "drawer.settings"
    const val SETTINGS_TIPS = "settings.tips"

    /** The Settings hub row leading to the Look and Feel page. */
    const val SETTINGS_APPEARANCE = "settings.appearance"

    /** The app-wide "Text size" block inside Settings § Look and Feel. */
    const val SETTINGS_APPEARANCE_TEXT_SIZE = "settings.appearance.textSize"

    /** The Settings hub row leading to the Notes page. */
    const val SETTINGS_NOTES = "settings.notes"

    /** The "New note cursor" row inside Settings § Notes. */
    const val SETTINGS_NOTES_NEW_NOTE_CURSOR = "settings.notes.newNoteCursor"

    /** The "Auto-save notes" toggle inside Settings § Notes. */
    const val SETTINGS_NOTES_AUTO_SAVE = "settings.notes.autoSave"

    /** The Settings hub row leading to the Capture Templates page. */
    const val SETTINGS_CAPTURE_TEMPLATES = "settings.captureTemplates"

    /** The "Capture link" section on a template's editor (grove:// link + Add to Homescreen). */
    const val SETTINGS_CAPTURE_TEMPLATES_LINK = "settings.captureTemplates.captureLink"

    /** The Settings hub row leading to the Reminders page. */
    const val SETTINGS_REMINDERS = "settings.reminders"

    /** The "Notify for tasks without a time" toggle inside Settings § Reminders. */
    const val SETTINGS_REMINDERS_UNTIMED = "settings.reminders.untimed"

    /** The Settings hub row leading to the Widget page. */
    const val SETTINGS_WIDGET = "settings.widget"

    /** The Widget settings page itself (home-screen Agenda ledger widget). */
    const val SETTINGS_WIDGET_PAGE = "settings.widget.page"

    /** The note editor's metadata FAB (the "☰" button, bottom-right of the Read
     *  and Edit note screens). A feature reached through it lists this so the
     *  button carries a corner dot until the feature is seen. */
    const val TOPBAR_MENU = "topbar.menu"

    /** The ACTIVE tab in the tabbed planning-dates editor (bare active timestamps / events). */
    const val PLANNING_DATES_ACTIVE = "planningDates.active"

    /** A Tips & Tricks section, keyed by its [TipGroup] id. */
    fun tipsGroup(id: String) = "tips.group.$id"
}

/**
 * The registry of features that currently carry a NEW badge. Add an entry when a
 * release ships something worth pointing at; drop it a release or two later, once
 * every updating user has had a chance to see it.
 */
val NEW_FEATURES: List<NewFeature> = listOf(
    NewFeature(
        // The "Links" group is new to Tips & Tricks; point updating users at it.
        // `since` is the versionCode of the release that ships this framework —
        // bump it to match `gradle.properties` versionName when cutting the release.
        id = "tips-links-group",
        since = 10300,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_TIPS,
            NewAnchors.tipsGroup("links"),
        ),
        destination = NewAnchors.tipsGroup("links"),
    ),
    NewFeature(
        // Settings § Notes gained a "New note cursor" lever (heading vs body).
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "notes-new-note-cursor",
        since = 10400,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_NOTES,
            NewAnchors.SETTINGS_NOTES_NEW_NOTE_CURSOR,
        ),
        destination = NewAnchors.SETTINGS_NOTES_NEW_NOTE_CURSOR,
    ),
    NewFeature(
        // Settings § Look and Feel gained an app-wide "Text size" lever
        // (Small/Medium/Large) that scales every text in the app.
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "app-text-size",
        since = 10400,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_APPEARANCE,
            NewAnchors.SETTINGS_APPEARANCE_TEXT_SIZE,
        ),
        destination = NewAnchors.SETTINGS_APPEARANCE_TEXT_SIZE,
    ),
    NewFeature(
        // Settings § Notes gained an "Auto-save notes" switch. Off means an editor
        // never writes on its own: no 5s idle save, and switching to Read no longer
        // saves either (Read renders the unsaved buffer instead).
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "notes-auto-save",
        since = 10500,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_NOTES,
            NewAnchors.SETTINGS_NOTES_AUTO_SAVE,
        ),
        destination = NewAnchors.SETTINGS_NOTES_AUTO_SAVE,
    ),
    NewFeature(
        // Read-mode checkboxes now toggle done on tap and in-progress on
        // long-press (the "Checklist states" setting is gone). Point updating
        // users at the Tips & Tricks entry that spells the gestures out.
        // `since` is the versionCode of the release that ships it.
        id = "checklist-gestures",
        since = 10400,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_TIPS,
            NewAnchors.tipsGroup("checklists"),
        ),
        destination = NewAnchors.tipsGroup("checklists"),
    ),
    NewFeature(
        // A capture template's editor gained a "Capture link" section: a live
        // grove://capture/<slug> deep link (tap to copy) plus an "Add to
        // Homescreen" button that pins a shortcut straight to that template.
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "template-capture-link",
        since = 10500,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_CAPTURE_TEMPLATES,
            NewAnchors.SETTINGS_CAPTURE_TEMPLATES_LINK,
        ),
        destination = NewAnchors.SETTINGS_CAPTURE_TEMPLATES_LINK,
    ),
    NewFeature(
        // Settings gained a dedicated Widget page: Filename/Tags/Priority toggles,
        // a Font size lever, and a live preview for the home-screen Agenda ledger
        // widget.
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "agenda-widget-settings",
        since = 10500,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_WIDGET,
            NewAnchors.SETTINGS_WIDGET_PAGE,
        ),
        destination = NewAnchors.SETTINGS_WIDGET_PAGE,
    ),
    NewFeature(
        // Bare active timestamps: a heading can now carry plain `<date>` event
        // stamps (single or a range), managed on the new ACTIVE tab of the
        // planning-dates editor. They show on the agenda, in read mode / outline
        // as a violet dot chip, are searchable with `a.` filters, and fire
        // reminders like SCHEDULED/DEADLINE.
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "bare-active-timestamps",
        since = 10600,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.PLANNING_DATES_ACTIVE,
        ),
        destination = NewAnchors.PLANNING_DATES_ACTIVE,
    ),
    NewFeature(
        // Settings § Reminders gained a "Notify for tasks without a time" toggle:
        // a separate notification for every SCHEDULED/DEADLINE/active timestamp
        // that lands on a day with no time of day, fired at the reminder time.
        // `since` is the versionCode of the release that ships it — bump it to
        // match `gradle.properties` versionName when cutting the release.
        id = "reminders-notify-untimed",
        since = 10600,
        anchors = setOf(
            NewAnchors.TOPBAR_MENU,
            NewAnchors.DRAWER_SETTINGS,
            NewAnchors.SETTINGS_REMINDERS,
            NewAnchors.SETTINGS_REMINDERS_UNTIMED,
        ),
        destination = NewAnchors.SETTINGS_REMINDERS_UNTIMED,
    ),
)
