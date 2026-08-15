import java.io.File
import java.security.KeyStore

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// versionCode is derived from git at build time: nothing is hardcoded or
// written back into the repo, so every commit yields a unique, monotonically
// increasing build number. versionName is the opposite — a manually-bumped
// SemVer string read as-is from the `versionName` key in gradle.properties;
// nothing here computes or increments it.
fun gitOutput(args: List<String>): String? {
    val out = providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }
    return if (out.result.get().exitValue == 0)
        out.standardOutput.asText.get().trim().ifEmpty { null }
    else null
}

// Number of commits reachable from HEAD. CI must check out full history
// (actions/checkout fetch-depth: 0) or a shallow clone undercounts. Falls back
// to 1 outside a git checkout (e.g. a source archive).
val gitCommitCount = gitOutput(listOf("rev-list", "--count", "HEAD"))?.toIntOrNull() ?: 1
val manualVersionName = providers.gradleProperty("versionName").get()

// Bundles the repo's CHANGELOG.md into the APK as a raw asset (read at runtime by the
// What's New modal) instead of hand-duplicating its content into a resource: this keeps
// the single source of truth in the root CHANGELOG.md, so it can't drift from what CI
// actually cuts into GitHub Releases. A plain Copy task's output is a File, but the
// Variant API's addGeneratedSourceDirectory needs a DirectoryProperty, hence this
// dedicated task instead of a stock Copy/Sync.
abstract class CopyChangelogTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val dest = outputDir.get().asFile
        dest.mkdirs()
        inputFile.get().asFile.copyTo(File(dest, "CHANGELOG.md"), overwrite = true)
    }
}

// Release signing comes from the environment (CI secrets). We validate the
// keystore and alias up front so a missing or misconfigured secret degrades to
// an unsigned release build instead of failing packaging, and a local
// `assembleRelease` (no env set) likewise stays unsigned, as before.
class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

val releaseSigning: ReleaseSigning? = run {
    val path = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() } ?: return@run null
    val storePassword = System.getenv("SIGNING_STORE_PASSWORD").orEmpty()
    val keyAlias = System.getenv("SIGNING_KEY_ALIAS").orEmpty()
    val keyPassword = System.getenv("SIGNING_KEY_PASSWORD").orEmpty()
    val store = file(path)
    fun disable(reason: String): ReleaseSigning? {
        logger.warn("Release signing disabled: $reason. Building an unsigned release APK.")
        return null
    }
    if (!store.exists()) return@run disable("keystore '$path' not found")
    if (keyAlias.isBlank()) return@run disable("SIGNING_KEY_ALIAS is empty")
    val keystore = listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
        runCatching {
            KeyStore.getInstance(type).apply {
                store.inputStream().use { load(it, storePassword.toCharArray()) }
            }
        }.getOrNull()
    } ?: return@run disable("could not open keystore (wrong store password or unknown format)")
    if (!keystore.containsAlias(keyAlias)) return@run disable("alias '$keyAlias' not found in keystore")
    val keyUsable = runCatching {
        keystore.getKey(keyAlias, keyPassword.toCharArray()) != null
    }.getOrDefault(false)
    if (!keyUsable) return@run disable("wrong key password for alias '$keyAlias'")
    ReleaseSigning(store, storePassword, keyAlias, keyPassword)
}

android {
    namespace = "com.rrajath.grove"
    compileSdk {
        version = release(36)
    }
    // Pinned so every build environment strips prebuilt native libs (from
    // dependencies like androidx.datastore/androidx.sqlite-bundled) with the
    // same NDK, byte-for-byte — otherwise an unpinned/auto-selected NDK
    // differs between CI and F-Droid's build server, breaking F-Droid's
    // reproducible-build comparison against the CI-published APK even though
    // Grove has no native source of its own. Keep in sync with fdroiddata's
    // `ndk:` build entry for this app.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.rrajath.grove"
        minSdk = 34
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = manualVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUG_REPORT_ENDPOINT", "\"https://grove-bug-report.r-rajath.workers.dev\"")
    }

    signingConfigs {
        create("release") {
            // Wired only when the CI secrets resolve to a valid keystore + alias;
            // otherwise the config stays empty and the release builds unsigned.
            releaseSigning?.let {
                storeFile = it.storeFile
                storePassword = it.storePassword
                keyAlias = it.keyAlias
                keyPassword = it.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Distinct applicationId so a debug build from Android Studio can be
            // installed side-by-side with the CI-signed release build on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Build type the :macrobenchmark module measures against. Mirrors release
        // (so numbers reflect a shippable build) but is debug-signed and marked
        // profileable so Macrobenchmark can capture traces without root.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyTask = tasks.register<CopyChangelogTask>("copy${variantName}ChangelogAsset") {
            inputFile.set(rootProject.file("CHANGELOG.md"))
            outputDir.set(layout.buildDirectory.dir("generated/assets/changelog/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyTask) { it.outputDir }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.glance.appwidget)
    // Enables CompilationMode.Partial (baseline-profile) benchmarks and installs
    // a packaged baseline profile at app startup once one is generated.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.java.diff.utils)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Print just the versionName so CI can tag releases from one source of
// truth: `./gradlew -q printVersionName` → "1.0.0". The value itself comes
// from gradle.properties, not from anything computed here.
tasks.register("printVersionName") {
    doLast { println(manualVersionName) }
}

// Print just the versionCode (`./gradlew -q printVersionCode` → "262") so CI can
// stamp it into the CHANGELOG.md release header alongside versionName. Since
// versionName is now manually bumped and can repeat across releases, versionCode
// is the only value still guaranteed unique and increasing per release — the
// What's New modal (see ChangelogParser) keys off it for that reason.
tasks.register("printVersionCode") {
    doLast { println(gitCommitCount) }
}
