plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.rrajath.grove.macrobenchmark"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Must share a name with the :app build type we benchmark against.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Benchmark the real app module.
    targetProjectPath = ":app"
    // Lets the benchmark run without a separate test-instrumentation app process.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only the benchmark variant is meaningful; disable the rest.
        it.enable = it.buildType == "benchmark"
    }
}
