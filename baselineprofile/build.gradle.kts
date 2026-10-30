// The Baseline Profile generator (docs/리팩토링-성능-위생-계획.md, Phase 7-2).
// Included only with -PbaselineProfile; baseline-profile.yml runs it on an
// emulator, and the profile it records is committed as
// app/src/main/baseline-prof.txt, which AGP builds into every release APK.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "dev.gomoku.zenyth.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Recording a profile needs API 28 or later.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    // The app's benchmark build is the only thing worth instrumenting.
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
