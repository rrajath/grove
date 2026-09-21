# Ignore List — Design

Date: 2026-09-21

## Problem

Grove indexes every `.org` file in the vault by recursively walking the SAF
tree. Today the only exclusion mechanism is `.orgzlyignore`, a gitignore-style
file dropped in the vault root: `Vault.listOrgFiles()` and `SyncEngine` each
read it and filter the *already-fully-walked* file list against it. For a
vault with thousands of directories where large subtrees are irrelevant to
Grove (e.g. a shared Syncthing folder that also holds non-org project trees),
this is expensive twice over: the SAF walk (`SafFileStore.listTree()`) still
issues a `child-documents` query for every directory in an ignored subtree,
and it does this on every scan and every sync pass.

This adds a Settings-managed ignore list that patterns match against before a
directory is ever queried, so an ignored subtree is never descended into —
not during the initial scan, not during sync, not ever.

## Non-goals

- No live preview of "N files currently match this pattern" in the UI.
- No per-pattern validation/error UI; a pattern that matches nothing is just
  inert.
- `.orgzlyignore` is not kept as an ongoing mechanism (see Decisions).

## Decisions

1. **Replaces `.orgzlyignore`.** The Settings-based list becomes the only
   ignore mechanism. On first load after this ships, if `ignoreList` is empty
   and `.orgzlyignore` exists in the vault root, its lines are imported into
   `ignoreList` once (guarded by a device-local flag so clearing the list
   afterward doesn't re-import). The file itself is left on disk, untouched,
   and never read again after that one-time import.
2. **Folder/file matching:** an entry with no `/` matches that bare name at
   any depth (`archive` matches `archive`, `projects/archive`,
   `a/b/c/archive`, ...). An entry containing `/` matches only that exact
   vault-relative path.
3. **Wildcards (`*`, `?`) stay within one path segment** — same semantics as
   the existing `IgnoreRules.globToRegex`. A pattern is never evaluated
   against more of the path than the walker has already seen, which is what
   makes "skip before descending" possible without look-ahead.
4. Negation (`!prefix`) is dropped — it existed in `IgnoreRules` for
   gitignore-style overrides, but overrides never make sense against
   directories that are never walked in the first place; a negated line counts
   as a no-op ("re-including" is meaningless once we never see the subtree).

## Pattern syntax

One entry per line in the `ignoreList` setting. Blank lines and lines
starting with `#` are ignored (kept for readability/copy-paste from an old
`.orgzlyignore`, not documented as a user-facing feature).

| Entry | Matches |
|---|---|
| `archive` | Any file or folder named `archive`, at any depth |
| `*.bak` | Any file or folder whose name matches the glob, at any depth |
| `projects/archive` | Only the folder/file at that exact vault-relative path |
| `projects/*/scratch` | Not supported — wildcards don't cross `/` |

## Architecture

### `vault/IgnorePatterns.kt` (new, replaces `IgnoreRules`)

```kotlin
class IgnorePatterns(rulesText: String) {
    fun isDirIgnored(name: String, path: String): Boolean
    fun isFileIgnored(name: String, path: String): Boolean
}
```

Both functions share one matcher: a bare pattern (no `/`) is compiled to a
segment-scoped glob regex and checked against `name`; a pattern containing
`/` is checked with a full match against `path`. `path` is the same
vault-relative path shape `FileEntry.name`/`SafFileStore`'s `dir` already use.

### Traversal — both `FileStore` implementations take an `IgnorePatterns`

**`SafFileStore`** (constructor gains `private val ignore: IgnorePatterns`):
in `listTree()`'s BFS loop (currently `SafFileStore.kt:76-107`), the existing
`if (isSkippedVaultDir(name)) continue` for directories gets a sibling check:

```kotlin
if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
    if (isSkippedVaultDir(name) || ignore.isDirIgnored(name, path)) continue
    dirs[path] = childDocId
    queue.add(path to childDocId)
} else {
    if (ignore.isFileIgnored(name, path)) continue
    files[path] = childDocId
    entries.add(FileEntry(path, cursor.getLong(2), cursor.getLong(3)))
}
```

An ignored directory is never added to `dirs` or `queue`, so no
`buildChildDocumentsUriUsingTree` query is ever issued for it or anything
beneath it — this is the actual performance win.

**`JvmFileStore`** (constructor gains the same param): `onEnter` gains
`&& !ignore.isDirIgnored(it.name, it.relativeTo(root).invariantSeparatorsPath)`
alongside the existing dot-directory check; the file `.filter` gains the
matching `isFileIgnored` check.

### Callers simplify

- `Vault.listOrgFiles()` drops its own `IgnoreRules` read/filter — `store.list()`
  already excludes ignored entries, so this becomes a plain `.filter { it.name.endsWith(".org") && !it.name.contains(".sync-conflict-") }`.
- `SyncEngine` drops its separate `.orgzlyignore` read (`SyncEngine.kt:102-104`)
  entirely — same reasoning.
- `IgnoreRules.kt` is deleted; its `globToRegex` helper moves into
  `IgnorePatterns`.

### Settings storage

`GroveSettings` gains:

```kotlin
/** Settings § Notebooks: newline-separated file/folder/pattern names, never descended into or indexed. */
val ignoreList: String = "",
```

- DataStore key `stringPreferencesKey("ignore_list")`, `SettingsRepository.setIgnoreList(value: String)`.
- Included in `SettingsSerialization` (import/export), same treatment as `todoKeywords`.
- A second, device-local field `ignoreListImportedFromFile: Boolean = false` —
  **not** included in `SettingsSerialization` (same treatment as
  `newBadgeBaseline`/`onboardingDone`) — guards the one-time `.orgzlyignore`
  import so it only ever runs once per install.

### One-time import

On `SettingsRepository`'s settings flow construction (or lazily, the first
time `GroveApplication.fileStore` needs to build a store and
`ignoreListImportedFromFile` is false): if the vault has a `.orgzlyignore` at
its root, read it, take its non-comment/non-blank lines (dropping any leading
`!` negation markers, per Decision 4), append them to `ignoreList` (dedup by
line), and set `ignoreListImportedFromFile = true`. This runs at most once;
the file is never deleted or rewritten.

### Wiring — `GroveApplication.fileStore`

The existing `combine(vaultTreeUri, TestVaultHook.root)` (`GroveApplication.kt:152-163`)
gains a third input:

```kotlin
combine(
    settingsRepository.settings.map { it.vaultTreeUri }.distinctUntilChanged(),
    settingsRepository.settings.map { it.ignoreList }.distinctUntilChanged(),
    TestVaultHook.root,
) { uriString, ignoreListText, testRoot ->
    val ignore = IgnorePatterns(ignoreListText)
    when {
        testRoot != null -> JvmFileStore(testRoot, ignore)
        uriString != null -> SafFileStore(this, uriString.toUri(), ignore)
        else -> null
    }
}
```

Editing the list in Settings rebuilds the `FileStore` (and, downstream, the
`Vault`, via the existing `vault` `combine`), so the next scan/sync picks up
the new rules — same mechanism that already handles a vault-URI change.

### UI

New row in `SettingsNotebooksScreen` ("Ignore list", no description needed
beyond the row label — detail lives on the destination screen), navigating to
a new route `settings/notebooks/ignorelist` → `SettingsIgnoreListScreen`:

- Explanatory copy: "All .org files in this vault and its subfolders are
  indexed automatically. The files, folders, and patterns listed below are
  skipped entirely and never scanned."
- If import happened (`ignoreListImportedFromFile == true` and the imported
  set was non-empty): a one-line note above the field, "Imported from
  .orgzlyignore."
- Multiline `OutlinedTextField`, pre-filled with the current `ignoreList`
  value, one entry per line — same apply-on-dispose pattern as
  `SettingsNotesScreen`'s TODO-keywords field (`keywordsText` +
  `DisposableEffect` committing on leave, `onSetIgnoreList` callback threaded
  the same way `onSetTodoKeywords` is).

## Testing

- `IgnorePatterns` unit tests (JVM): bare-name-anywhere, exact-path, glob
  segment matching, comments/blanks, negation lines treated as no-ops.
- `SafFileStore`/`JvmFileStore`: existing traversal tests extended to assert
  an ignored directory's children never appear in `list()` results and (for
  `SafFileStore`, via its test double) that no query is issued for it.
- `Vault`/`SyncEngine`: existing `.orgzlyignore`-based tests migrated to
  construct their `FileStore` with an `IgnorePatterns` instead.
- One-time import: settings-repository test asserting `.orgzlyignore` content
  lands in `ignoreList` exactly once, and a second app start with a non-empty
  `.orgzlyignore` and `ignoreListImportedFromFile = true` does not re-import.

## Docs

`todoKeywords`-style settings fields aren't separately documented under
`docs-site/src/content/docs/features/` today for Notebooks-section settings
(the Notebooks settings page isn't itself a docs page); confirm whether this
warrants a features doc entry when this ships — flagging per CLAUDE.md's
"significant change" rule rather than deciding it here.
