package com.rrajath.grove.testing

import com.rrajath.grove.vault.FileStore

/**
 * Writes fixture `.org` files into a [FileStore]. Shared by the integration
 * tests, the Maestro debug-vault hook (M5), and the benchmark module (M6) so
 * every layer starts from the same vault contents.
 *
 * See internal/test-suite-00-overview.md § Fakes to build (TestVaultSeeder).
 */
object TestVaultSeeder {

    /** Write every entry of [files] into [store], overwriting any existing content. */
    suspend fun seed(store: FileStore, files: Map<String, String> = OrgFixtures.all) {
        files.forEach { (name, content) -> store.write(name, content) }
    }
}
