# Product Requirements Document
# Grove — Android Org-Mode Note-Taking App

**Version:** 1.0  
**Platform:** Android (Kotlin, native)  
**Package name:** `com.yourname.grove`  
**Internal code name:** Wren  
**Status:** Draft  

---

## 1. Overview

**Grove** is a native Android app for note-taking using plain-text `.org` files. The app is designed for people already using Org mode on their laptops who want a first-class mobile companion — not a full Org mode replacement, but an excellent tool for capturing, reading, editing, and navigating notes on the go.

The core philosophy is **file-first**: notes are always plain `.org` files on disk that any editor can open. Grove adds convenience on top of that format, not a proprietary layer above it.

**Primary inspiration:** Orgzly Revived  
**Key improvements over Orgzly Revived:** Automatic sync, capture templates, and a stronger reading/navigation experience.

---

## 2. Goals

- Provide a faithful Org mode note-taking experience on Android
- Make syncing with a laptop feel effortless and automatic
- Support capture templates so common note types (journal entries, quick notes, meeting notes) are one tap away
- Prioritize note-taking workflows; task/TODO management is secondary
- Produce `.org` files that are byte-compatible with Emacs/org-mode on desktop

## 3. Non-Goals

- Full org-agenda replacement (basic agenda view is in scope; deep customization is not)
- Project management or Kanban boards
- Real-time collaboration
- iOS version
- Custom scripting or Elisp execution
- Exporting to PDF/HTML (out of scope for v1)

---

## 4. Design Principles

1. **Plain text is the source of truth.** No proprietary database format. The `.org` file is always the canonical record.
2. **Mobile-first interactions, desktop-compatible output.** Touch interactions (swipe, long-press, FAB) should feel native to Android, but the output must be fully compatible with Emacs org-mode.
3. **Sync should be invisible.** The user should never have to think about whether their phone and laptop are in sync.
4. **Speed of capture over completeness.** Getting an idea captured in 2 seconds matters more than capturing it perfectly.
5. **Minimal chrome, maximum content.** The editor and outline view should give as much space as possible to the content.

---

## 5. Feature Specifications

---

### 5.1 Notebook Management

Notebooks correspond 1:1 with `.org` files in the synced repository.

**Requirements:**
- Display all notebooks (`.org` files) in a list, showing: name, number of notes, last-modified time
- Create new notebooks (creates a new `.org` file in the sync directory)
- Rename notebooks (renames the underlying file)
- Delete notebooks (moves to a local trash folder; not immediately hard-deleted)
- Long-press on a notebook to open a context menu: Rename, Set Sync Link, Delete, Force Load, Force Save
- Display a sync status badge per notebook: Synced, Modified (local), Conflict, Error
- Support `.orgzlyignore`-style ignore rules for filtering which files appear in the list

**Notebook list UI:**
- Navigation drawer on the left with: All Notes, Notebooks, Searches, Agenda, Settings
- Notebooks screen is the default home screen

---

### 5.2 Note Editor

The editor is the core of the app. It must handle Org mode syntax faithfully.

**Supported note properties:**
- Heading with level (depth), indicated by `*` stars
- TODO keyword / state (e.g., `TODO`, `DONE`, `IN-PROGRESS` — configurable)
- Priority (`[#A]`, `[#B]`, `[#C]`)
- Tags (`:tag1:tag2:`)
- SCHEDULED and DEADLINE timestamps
- CREATED timestamp (auto-added on note creation, configurable)
- Properties drawer (`:PROPERTIES:...:END:`)
- Note body / content (freeform text below the heading)
- `ID` and `CUSTOM_ID` properties (used for note-to-note linking)

**Editor behavior:**
- Full-screen editor with a toolbar for common formatting actions
- Toolbar actions: Bold (`*text*`), Italic (`/text/`), Underline (`_text_`), Code (`` `text` ``), Insert link, Insert timestamp, Insert heading
- Soft keyboard auto-shows on open; hardware keyboard fully supported
- Support indentation levels in body content
- Syntax highlighting: headings, keywords, timestamps, tags, links rendered in distinct colors (not raw org syntax)
- Auto-complete for tags (from all tags used across notebooks) and TODO keywords
- "Save" is automatic on navigate-away and on sync; no manual save button required

**Note metadata panel:**
- A bottom sheet or collapsible panel for setting: State, Priority, Tags, SCHEDULED, DEADLINE
- State cycling via tap (cycles through configured states)
- Date/time picker for SCHEDULED and DEADLINE that produces valid org timestamps

---

### 5.3 Outline View (Notebook View)

Each notebook opens into an outline view showing its heading tree.

**Requirements:**
- Headings rendered as collapsible tree nodes
- Tap heading to expand/collapse subtree (same as `TAB` in Emacs)
- Long-press opens context menu: Edit, New sub-note, Move up/down, Cut, Copy, Paste, Delete
- Swipe right: quick action menu (cycle state, set scheduled, mark done)
- Swipe left: "show in context" (narrows view to just that note and its subtree)
- Visual indentation by heading level
- Heading text wraps; tags aligned to right edge
- Folded headings show a summary of direct child count
- Drag-and-drop to reorder headings (within the same level or reparent)
- FAB (Floating Action Button) to add a new top-level note to the current notebook

**Display options (toggle in overflow menu):**
- Show/hide tags
- Show/hide SCHEDULED/DEADLINE timestamps
- Show/hide TODO keywords

---

### 5.4 Links

Links can appear in note titles or body content.

**Supported link types:**
- Web URLs: `https://...` and `[[https://...][label]]` — opens browser
- Email: `mailto:...` — opens mail client
- Telephone: `tel:...`, `sms:...` — opens dialer/messages
- Location: `geo:lat,lon` — opens maps
- File: `file:path/to/file.org` — opens file in app or external app
- Internal note link by ID: `id:uuid` — jumps to the note with that ID
- Internal note link by CUSTOM_ID: `[[#custom-id]]` — jumps to matching note
- Notebook link: `file:notebook.org` — opens that notebook

**Behavior:**
- Links in body content are tappable in both edit and read modes
- Tapping a link to another note navigates to that note within the app
- Long-pressing a link shows: Open, Copy link address, Edit link

---

### 5.5 Search

**Basic search:**
- Full-text search across all notebooks (heading text + body content)
- Results show: heading, notebook name, matched snippet with match highlighted
- Results update as user types (debounced 300ms)

**Advanced search (using Orgzly-compatible syntax):**

| Expression | Finds notes |
|---|---|
| `s.PERIOD` | Scheduled within period |
| `d.PERIOD` | Deadline within period |
| `c.PERIOD` | Closed within period |
| `cr.PERIOD` | Created within period |
| `i.STATE` | With todo state (e.g. `i.todo`) |
| `b.NOTEBOOK` | From notebook |
| `t.TAG` | With tag (inherited) |
| `tn.TAG` | With tag (note's own only) |
| `p.PRIORITY` | With priority |

- Operators: `AND` (implicit, space-separated), `OR`, `.` prefix for NOT
- Sorting via `o.PROPERTY` operator
- Period aliases: `today`, `tomorrow`, `yesterday`, `now`, `Nd`, `Nw`, `Nm`

**Saved searches:**
- Save any search as a named shortcut
- Saved searches appear in the navigation drawer
- Default saved searches provided: `Scheduled Today`, `All TODO`, `This Week`

**Agenda view:**
- Add `ad.DAYS` to any search query to get a day-grouped view
- Default agenda: notes scheduled or with deadline in the next 7 days
- Agenda accessible directly from the nav drawer

---

### 5.6 Tags and Priority

- Tags are set on individual notes and inherited by child notes
- Global tag list auto-populated from all tags in use across all notebooks
- Priorities: `[#A]`, `[#B]`, `[#C]` (configurable range)
- File-level tags (e.g. `#+FILETAGS: project`) respected and propagated
- Tag search matches as substring (e.g. `t.bee` matches `:beeblebrox:`)

---

### 5.7 Repeated Tasks

- Support org-mode repeater syntax: `SCHEDULED: <2025-04-30 Wed +1w>`
- Marking a repeated task DONE advances the date and resets state
- Supported repeater types: `+` (cumulative), `++` (catch-up), `.+` (future-only)

---

## 6. Priority Feature 1: Automatic Sync

This is the most important improvement over Orgzly Revived, which requires manual sync every time.

### 6.1 Recommended Sync Backend: Local Directory + Syncthing

For a low-friction, no-account setup between an Android phone and a laptop, the recommended approach is:

**Syncthing** (https://syncthing.net) over a **local directory** on the phone. Syncthing is free, open source, peer-to-peer (no cloud intermediary), and runs on Android, Windows, macOS, and Linux. Setup is one-time pairing between devices; after that, sync is fully automatic and continuous.

The app's role in this model: write `.org` files to a local directory on the phone. Syncthing watches that directory and mirrors changes to the laptop in real time.

**Why this is better than Dropbox for this use case:**
- No account limits (Dropbox free tier limits 2 connected clients)
- Works on the local network without internet
- Conflict resolution is handled by Syncthing with configurable rules
- Entirely open source and privacy-respecting

**Onboarding suggestion:** The app should detect if the sync directory has not been set up and show an optional "Setup Sync" flow that links to Syncthing's Play Store page and explains the directory-pairing setup.

### 6.2 Supported Sync Backends in the App

The app supports the following sync backends (user configures one or more):

| Backend | Description | Best for |
|---|---|---|
| Local Directory | Sync to a folder on-device | Used with Syncthing; recommended default |
| WebDAV | Sync over WebDAV (Nextcloud, etc.) | Self-hosted cloud, good conflict handling |
| Dropbox | Sync via Dropbox API | Users with existing Dropbox setup |

For each repository, the user configures: path/URL, credentials (if any), and sync behavior.

### 6.3 Auto-Sync Modes

Users configure auto-sync behavior per repository in Settings → Sync → [Repository] → Auto-Sync:

| Mode | Behavior |
|---|---|
| **Manual only** | Sync only when user taps the Sync button (Orgzly parity) |
| **On open / on close** | Sync when app moves to foreground and when it moves to background |
| **Periodic** | Sync every N minutes in the background (configurable: 5, 15, 30, 60 min) |
| **On file change** | Watch the sync directory for changes; sync immediately when a file is modified externally (local directory only) |
| **Continuous (recommended)** | Combines "on open/close" + "on file change"; uses a foreground service when the app is visible |

**Recommended default:** "On open / on close" — this covers the most common pain point (stale notes after editing on laptop) with minimal battery impact.

**Implementation notes (Kotlin):**
- Use `WorkManager` for periodic background sync (respects Doze mode)
- Use `FileObserver` for on-change detection in local directory repos
- Use a foreground `Service` with notification when "Continuous" mode is active
- Sync runs on a background coroutine (`Dispatchers.IO`); never blocks UI thread
- Show a persistent sync status icon in the app toolbar (synced ✓, syncing ↻, conflict ⚠, error ✗)

### 6.4 Conflict Resolution

When both the local and remote version of a file have changed since the last sync:

1. **Conflict picker:** Present a clear diff view showing local vs. remote changes side by side. User taps to keep local, keep remote, or "Keep both" (appends the conflicting content under a `CONFLICT` heading for manual resolution later). No automated structural merge in v1 — Syncthing's own conflict file handling (which creates a `.sync-conflict-...` copy of the diverged file) is the recommended fallback for Syncthing users.
2. **Force options:** "Force Load" (overwrite local with remote) and "Force Save" (overwrite remote with local) always available in the notebook context menu, regardless of conflict state.
3. Conflicts are indicated by a ⚠ badge on the notebook in the notebook list.
4. A conflict notification is shown so the user is alerted even if they don't open the app.

> **v2 consideration:** Structural auto-merge (attempting to merge changes in different headings automatically) is deferred. The conflict picker handles v1 well; Syncthing prevents most conflicts in normal use anyway.

---

## 7. Priority Feature 2: Capture Templates (Org-Capture)

Inspired by `org-capture` in Emacs and the Templates feature in Beorg (iOS). This lets users define templates for quickly adding structured notes to specific notebooks and locations.

### 7.1 Concept

A **capture template** defines:
- **Name:** Human-readable name shown in the picker (e.g. "Journal Entry", "Quick Note", "Meeting Note")
- **Target file:** Which `.org` notebook to write to
- **Target location:** Where in the file to insert the new note
- **Template body:** The pre-filled content, with placeholders

### 7.2 Capture Flow

1. User taps the **Capture** FAB or shortcut (available from anywhere in the app, the home screen widget, or the share sheet)
2. If more than one template exists, a bottom sheet appears with template names as cards
3. User selects a template
4. A pre-filled editor opens with the template expanded (placeholders replaced with real values)
5. User edits as needed, then taps **Save**
6. Entry is inserted into the target file at the configured location
7. App optionally navigates to the inserted note, or returns to previous screen

### 7.3 Template Placeholders

| Placeholder | Expands to |
|---|---|
| `%t` | Inactive timestamp: `[2025-06-11 Wed]` |
| `%T` | Active timestamp: `<2025-06-11 Wed>` |
| `%u` | Inactive date+time: `[2025-06-11 Wed 14:32]` |
| `%U` | Active date+time: `<2025-06-11 Wed 14:32>` |
| `%date` | Short date: `2025-06-11` |
| `%time` | Current time: `14:32` |
| `%day` | Day of week: `Wednesday` |
| `%year` | Year: `2025` |
| `%month` | Month name: `June` |
| `%cursor` | Final cursor position after insertion |
| `%?` | Same as `%cursor` (Emacs compat) |
| `%^{prompt}` | Prompts user to type a value before opening the editor |
| `%clipboard` | Android clipboard content |
| `%shared_text` | Text received via Android share sheet |
| `%shared_url` | URL received via Android share sheet |

### 7.4 Target Location Types

| Location type | Behavior |
|---|---|
| **Top of file** | Inserts before all headings |
| **Bottom of file** | Appends at end of file |
| **Under heading** | Inserts as child of a specific heading. The heading can be identified by **exact name** (simple to set up, fragile if the heading is renamed) or by **`CUSTOM_ID` property** (robust, recommended). The template editor lets users pick either method; CUSTOM_ID is shown as the recommended option with a short explanation. |
| **Datetree (date)** | Inserts under a date-tree: Year → Month → Day heading hierarchy. Creates missing headings automatically. |
| **Datetree (datetime)** | Same as above but the leaf node is a timestamped entry |
| **After last entry under heading** | Inserts as last sibling under a specific heading |

The **Datetree** target is the key one for journal use cases. Given a target file of `journal.org` and a datetree target, capturing automatically creates:

```org
* 2025
** 2025-06 June
*** 2025-06-11 Wednesday
**** 14:32 My journal entry
    Body text here...
```

...without the user ever having to navigate to the file and manually create headings.

### 7.5 Built-in Default Templates

The app ships with these templates out of the box (all editable or deletable):

| Name | Target | Location | Template text |
|---|---|---|---|
| Journal Entry | `journal.org` | Datetree (datetime) | `%T\n%cursor` |
| Quick Note | `inbox.org` | Bottom of file | `* %^{Title}\n%cursor` |
| TODO | `inbox.org` | Bottom of file | `* TODO %^{Title}\nSCHEDULED: %T\n%cursor` |

### 7.6 Template Editor UI

Templates are managed in Settings → Templates:
- List of templates with name and target file shown
- Tap to edit, swipe to delete, drag to reorder (order = display order in capture picker)
- Template editor fields: Name, Icon (emoji picker), Target File (file picker from synced notebooks), Target Location (dropdown), Template Text (multiline text field with placeholder reference guide)

---

## 8. Suggested Feature 1: Quick Capture Widget & Notification Shortcut

The fastest possible path to capturing a thought — without unlocking, finding the app, and navigating.

**Home screen widget:**
- A 1×1 or 2×1 Android app widget labeled "Capture"
- Tapping it immediately opens the capture template picker (or skips to editor if only one template)
- Widget is configurable: can be pinned to a specific template (bypasses picker entirely)

**Persistent notification (optional, user-controlled):**
- A low-priority notification always visible in the notification shade with a "Capture" action
- Tapping opens the same capture flow as the widget
- Can be enabled/disabled in Settings → Notifications

**Lock screen action (Android 13+):**
- Support the customizable lock screen shortcut to open capture directly from lock screen

**Rationale:** The best note-taking apps reduce capture friction to near zero. A journal entry you skip because opening the app takes 15 seconds never gets written. This feature is disproportionately valuable relative to its implementation complexity.

---

## 9. Suggested Feature 2: Read Mode (Rendered View)

A toggle between raw Org syntax editing and a clean rendered reading view.

**Rendered view renders:**
- `*bold*` → **bold**
- `/italic/` → *italic*
- `_underline_` → underlined text
- `` `code` `` → inline code block (monospace, highlighted background)
- `=verbatim=` → verbatim
- `[[url][label]]` → tappable hyperlink showing just the label
- `[[id:uuid]]` → tappable internal link showing the heading title
- `- item` and `1. item` → rendered lists
- `#+BEGIN_SRC ... #+END_SRC` → syntax-highlighted code block
- `| table | syntax |` → rendered table
- Timestamps rendered as readable dates, not raw angle-bracket syntax
- TODO keyword rendered as a colored chip (e.g. TODO = amber, DONE = green)
- Tags rendered as small rounded chips aligned right

**Behavior:**
- Toggle between Edit and Read mode via a button in the toolbar (pencil icon ↔ eye icon)
- Tapping any element in Read mode jumps to that position in Edit mode
- Read mode is the default when opening a note that was not just created
- App remembers per-notebook whether the last session was in read or edit mode

**Rationale:** Orgzly Revived always shows raw org syntax. For reading notes, this is visually noisy. A rendered view makes the app useful for reviewing notes, not just editing them.

---



## 10. Suggested Feature 3: Share Sheet Integration (Web Clipping)

Accept content shared from other apps and route it directly into a capture template.

**Behavior:**
- App registers as an Android share target for: plain text, URLs, images
- When content is shared to the app, the capture template picker opens with `%shared_text` and `%shared_url` pre-populated
- User picks a template (e.g. a "Reading List" template) and the captured note is created
- For images: attaches the image as a file link in the note body (copies image to attachments directory in the org repo)

**Example use case:**
- Reading an article in the browser → tap Share → select the app → "Reading List" template opens with `[[url][Article Title]]` pre-filled → tap Save → article saved to `reading.org` with a timestamp

**Rationale:** The phone is where people encounter things worth saving — articles, links, addresses, quotes. Without share sheet integration, saving these requires copy-pasting manually. This feature closes the loop between the rest of the phone and the note-taking system.

---

## 11. Suggested Feature 4: Full-Text Search with Match Highlighting

Orgzly Revived's search is powerful for structured queries (by state, tag, notebook, dates) but requires knowing the query language. Full-text search provides a natural starting point.

**Behavior:**
- Dedicated full-text search mode accessible from a search icon in the toolbar or by pressing `/` in any list
- Searches: heading text, body content, tags, properties
- Results show: heading, notebook, and a snippet of body text with the matched term **highlighted**
- Results ranked by: exact match > heading match > body match; recency as tiebreaker
- Incremental results as user types (debounced 200ms)
- "Advanced" toggle expands to the structured query syntax (5.5) for power users
- Search history (last 10 searches) shown when search field is empty

**Implementation notes (Kotlin/Android):**
- Use Android's built-in SQLite FTS5 (Full-Text Search) for the body content index
- Index is built at first sync and updated incrementally
- FTS5 snippet function used for match context extraction

**Rationale:** When you have hundreds of notes, finding a half-remembered thought is the most common operation. A fast, visually clear full-text search lowers the activation energy for retrieving notes and makes the system feel trustworthy.

---

## 12. What's Trimmed vs. Orgzly Revived

The following features from Orgzly Revived are intentionally excluded or deferred from v1 to keep the app focused on note-taking:

| Feature | Decision | Rationale |
|---|---|---|
| TODO state cycling as primary workflow | Demoted to secondary | Note-taking focus; state support retained but not front-and-center |
| Calendar Provider integration | Deferred to v2 | Adds complexity; not core to note-taking |
| Statistics cookies (`[/]`, `[%]`) | Deferred to v2 | Task management feature |
| Git sync backend | Deferred to v2 | High setup friction; Syncthing covers the use case better |
| `.orgzlyignore` | Included | Low effort, high value for large repos |
| Biometric lock | Deferred to v2 | Security feature, not core |

---

## 13. Technical Architecture Notes (Kotlin/Android)

**Data layer:**
- All `.org` files stored as plain text in the configured sync directory (no proprietary database as primary store)
- SQLite (via Room) used as a local **index** only: full-text search index, search cache
- Index is always rebuildable from the `.org` files; treat it as a cache, never as source of truth
- Org file parsing: use an existing Java/Kotlin org-mode parser library (evaluate `org-java` and other open-source options first; write a custom parser only if the chosen library has gaps that cannot be worked around)

**Sync layer:**
- `WorkManager` for all background sync tasks (periodic, on-boot)
- `FileObserver` for local directory change detection
- Coroutines + `Dispatchers.IO` for all I/O
- Sync state machine: Idle → Checking → Pulling → Merging → Pushing → Done/Conflict/Error
- Sync log accessible to user (Settings → Sync → View Log) for debugging

**UI layer:**
- Jetpack Compose for all UI (modern, well-suited to the custom rendering required)
- Material 3 design system
- Navigation: `NavHost` with deep links supporting `grove://note/{id}` for widget/notification shortcuts
- ViewModels + StateFlow for state management
- Dark mode support (follows system setting, with manual override)

**Editor:**
- Consider `BasicTextField` in Compose with custom `VisualTransformation` for syntax highlighting in edit mode
- For read mode, build a custom Compose renderer that maps org AST nodes to composables
- No dependency on WebView for rendering (keeps the app fast and native-feeling)

**Testing:**
- Org parser unit tests: round-trip property (parse → serialize → re-parse should be identical)
- Sync engine unit tests with mock repositories
- UI tests with Compose testing APIs

---

## 14. Settings Reference

Settings are organized into sections:

**Sync:**
- Repositories (add/edit/delete: Local Directory, WebDAV, Dropbox)
- Default sync mode (Manual / On Open-Close / Periodic / Continuous)
- Periodic sync interval (5 / 15 / 30 / 60 min)
- Conflict resolution default (Ask / Force Load / Force Save)
- Org file format: Tags column, Tags indentation (org-indent-mode compat)
- Show sync log

**Notes:**
- TODO keywords (configurable list; split by `|` for done-type: `TODO IN-PROGRESS | DONE CANCELLED`)
- Default priority (A / B / C / none)
- Add ID property to new notes (on/off)
- Add CREATED timestamp to new notes (on/off)

**Templates:**
- Manage capture templates (see §7.6)

**Appearance:**
- Theme (System / Light / Dark)
- Font size (Small / Medium / Large)
- Default note open mode (Edit / Read)
- Show tags in outline view (on/off)
- Show timestamps in outline view (on/off)

**Storage:**
- Sync root directory
- Attachments directory (for images/files added via share sheet)
- File links root (absolute and relative, per Orgzly convention)

---

## 15. Decisions Log

The following questions were raised during drafting and have been resolved:

| # | Question | Decision |
|---|---|---|
| 1 | App name | **Grove** (`com.yourname.grove`, internal code name: Wren) |
| 2 | Org parser | **Use an existing library** (evaluate `org-java` and other open-source Kotlin/Java parsers first). Write a custom parser only if the chosen library has a hard gap that cannot be worked around — e.g. a property or syntax element Grove needs that the library silently drops or corrupts. |
| 3 | Conflict auto-merge in v1 | **No auto-merge in v1.** Show a conflict picker (keep local / keep remote / keep both). Document Syncthing's own `.sync-conflict-...` file mechanism as the fallback for users on the recommended Syncthing setup. Auto-merge is a v2 consideration. |
| 4 | Read mode tables | **Defer to v2.** In v1, org table syntax inside a note body is displayed as monospace plain text in Read mode, with a small "table rendering coming in v2" note. No partial implementation. |
| 5 | Template "under heading" target | **Offer both.** Users can identify the target heading by exact name (easy to set up) or by `CUSTOM_ID` property (robust, recommended). The template editor explains the tradeoff and marks CUSTOM_ID as the recommended option. |

