package com.rrajath.grove.testing

/**
 * Canonical `.org` sample content shared by every test layer (integration,
 * UI, Maestro, benchmark). Keep these small and stable — assertions elsewhere
 * pin their exact text (e.g. the unique word "photosynthesis" in
 * [READING_LIST], the `capture-inbox` CUSTOM_ID in [INBOX]).
 *
 * See internal/test-suite-00-overview.md § Fakes to build (OrgFixtures).
 */
object OrgFixtures {

    /** Small note with a capture-target heading the default template inserts under. */
    val INBOX = """
        #+TITLE: Inbox

        * Captured
          :PROPERTIES:
          :CUSTOM_ID: capture-inbox
          :END:
        ** A first captured thought
    """.trimIndent() + "\n"

    /** Multi-level TODO tree for outline / edit / agenda coverage. */
    val PROJECTS = """
        #+TITLE: Projects

        * TODO Ship v2 release
        SCHEDULED: <2026-09-10 Thu>
        ** DONE Cut the changelog
        ** TODO Tag the release
        ** IN-PROGRESS Write the store listing
        * TODO Backlog
        ** TODO Dark mode polish
        *** TODO Audit contrast ratios
        ** CANCELLED Drop the widget rewrite
    """.trimIndent() + "\n"

    /** A heading whose body contains the unique search word "photosynthesis". */
    val READING_LIST = """
        #+TITLE: Reading list

        * Articles
        ** How leaves work
           The process of photosynthesis converts light into chemical energy.
        ** Rust ownership, revisited
           Notes on borrow-checker ergonomics.
    """.trimIndent() + "\n"

    /** Read-mode org table: a header row above a `|---|` rule, then body rows. */
    val TABLE = """
        #+TITLE: Table

        * Quarterly numbers

        | Quarter | Revenue | Growth |
        |---------+---------+--------|
        | Q1      | 120     | 4%     |
        | Q2      | 135     | 12%    |
        | Q3      | 128     | -5%    |
    """.trimIndent() + "\n"

    /** Large subtree (> LARGE_SUBTREE_THRESHOLD headings) to exercise fold-on-open and scroll. */
    val LARGE_SUBTREE: String = buildString {
        appendLine("#+TITLE: Large subtree")
        appendLine()
        appendLine("* Everything")
        for (i in 1..80) {
            appendLine("** Section ${"%02d".format(i)}")
            appendLine("   Body line for section $i.")
        }
    }

    /** The "keep local" side of a Syncthing sync-conflict pair. */
    val CONFLICT_LOCAL = """
        #+TITLE: Notes

        * Meeting notes
          Local edit: decided to postpone.
    """.trimIndent() + "\n"

    /** The "keep remote" side — same file, a `.sync-conflict-*` sibling on disk. */
    val CONFLICT_REMOTE = """
        #+TITLE: Notes

        * Meeting notes
          Remote edit: decided to proceed.
    """.trimIndent() + "\n"

    const val CONFLICT_FILE = "notes.org"
    const val CONFLICT_SIBLING = "notes.sync-conflict-20260903-120000-ABCDEF1.org"

    /** Every fixture keyed by its vault-relative path — the default seed set. */
    val all: Map<String, String> = mapOf(
        "inbox.org" to INBOX,
        "projects.org" to PROJECTS,
        "reading-list.org" to READING_LIST,
        "table.org" to TABLE,
        "large-subtree.org" to LARGE_SUBTREE,
    )

    /** [all] plus a live sync-conflict pair, for conflict-resolution tests. */
    val withConflict: Map<String, String> = all + mapOf(
        CONFLICT_FILE to CONFLICT_LOCAL,
        CONFLICT_SIBLING to CONFLICT_REMOTE,
    )
}
