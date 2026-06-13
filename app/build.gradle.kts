import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Semantic version lives in version.properties; any build that produces an
// app (assemble/bundle/install/build) auto-bumps the patch and versionCode.
val versionFile = file("version.properties")
val versionProps = Properties().apply { versionFile.inputStream().use { load(it) } }
val bumpVersion = gradle.startParameter.taskNames.any { name ->
    listOf("assemble", "bundle", "install", "build").any { name.contains(it, ignoreCase = true) }
}
if (bumpVersion) {
    versionProps.setProperty(
        "VERSION_PATCH",
        (versionProps.getProperty("VERSION_PATCH").toInt() + 1).toString(),
    )
    versionProps.setProperty(
        "VERSION_CODE",
        (versionProps.getProperty("VERSION_CODE").toInt() + 1).toString(),
    )
    versionFile.outputStream().use { versionProps.store(it, "Auto-incremented on build") }
}
val semanticVersion = listOf("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH")
    .joinToString(".") { versionProps.getProperty(it) }

android {
    namespace = "com.rrajath.grove"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.rrajath.grove"
        minSdk = 34
        targetSdk = 36
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = semanticVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}