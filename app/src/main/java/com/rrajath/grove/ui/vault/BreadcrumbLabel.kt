package com.rrajath.grove.ui.vault

/**
 * Breadcrumb label for a note file. When the file lives in a folder the full
 * vault-relative path is shown (`projects/clients/acme.org`); a file at the vault
 * root shows its bare name (`inbox.org`). The `.org` suffix is kept, matching the
 * notebook list and the outline title.
 *
 * `NoteRef.fileName` already carries the full vault-relative path, so this is
 * effectively identity today. It exists as the single named contract every
 * breadcrumb surface routes through, locked by [BreadcrumbLabelTest], and the one
 * place to hang `.org` trimming should that decision ever change.
 */
fun breadcrumbFileLabel(fileName: String): String = fileName

/**
 * Space-saving display form of [breadcrumbFileLabel] for a top bar spot where the
 * file path is the only thing shown and needs room to wrap rather than truncate
 * (e.g. a whole-file Read/Edit header). A file at the vault root or one folder deep
 * is unchanged; a file two or more folders deep has every folder segment compacted
 * to its first letter, so `work/ideas/app.org` becomes `w/i/app.org`. The leaf
 * filename (with its `.org` suffix) is always kept in full.
 */
fun compactFileLabel(fileName: String): String {
    val lastSlash = fileName.lastIndexOf('/')
    if (lastSlash < 0) return fileName
    val folders = fileName.substring(0, lastSlash).split('/')
    if (folders.size < 2) return fileName
    val leaf = fileName.substring(lastSlash + 1)
    return folders.joinToString("/") { it.take(1) } + "/" + leaf
}
