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
