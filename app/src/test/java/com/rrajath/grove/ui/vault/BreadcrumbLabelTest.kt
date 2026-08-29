package com.rrajath.grove.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the breadcrumb file-label contract (follow-up plan, Item 1). */
class BreadcrumbLabelTest {

    @Test
    fun `a nested file keeps its full vault-relative path`() {
        assertEquals("projects/clients/acme.org", breadcrumbFileLabel("projects/clients/acme.org"))
    }

    @Test
    fun `a vault-root file shows its bare name, org suffix kept`() {
        assertEquals("inbox.org", breadcrumbFileLabel("inbox.org"))
    }

    @Test
    fun `an empty path is passed through unchanged`() {
        assertEquals("", breadcrumbFileLabel(""))
    }
}
