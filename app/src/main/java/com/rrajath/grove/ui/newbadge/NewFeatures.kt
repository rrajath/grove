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

    /** The Settings hub row leading to the Notes page. */
    const val SETTINGS_NOTES = "settings.notes"

    /** The "New note cursor" row inside Settings § Notes. */
    const val SETTINGS_NOTES_NEW_NOTE_CURSOR = "settings.notes.newNoteCursor"

    /** The top-bar "☰" glyph. A feature reached through it lists this so the
     *  glyph carries a corner dot until the feature is seen. */
    const val TOPBAR_MENU = "topbar.menu"

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
)
