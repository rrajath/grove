package com.rrajath.grove.org

import java.util.UUID

/**
 * A fresh identifier for an org heading's `:ID:` (or the `:CUSTOM_ID:` Grove
 * auto-injects when favoriting). Upper-cased so the UUID matches the form Emacs
 * `org-id` writes into `:PROPERTIES:` drawers.
 */
fun newOrgId(): String = UUID.randomUUID().toString().uppercase()
