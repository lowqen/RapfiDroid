import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin.android: AGP 9 compiles Kotlin itself (built-in Kotlin).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.gomoku.zenyth"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.gomoku.rapfidroid"
        minSdk = 26
        targetSdk = 35
        // versionCode only ever goes up (18 was the withdrawn 0.15.3 test build,
        // 19 and 20 the two 0.15.0 builds).
        versionCode = 21
        versionName = "0.16.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Three languages, and only these. Without this the APK carries every locale
    // AndroidX ships and the system offers them, which would put a French system
    // label over a screen the app cannot translate. Chinese is listed with its
    // regions because AndroidX keeps its Chinese strings in values-zh-rCN/-rTW/-rHK.
    androidResources {
        localeFilters += listOf("en", "ko", "zh", "zh-rCN", "zh-rTW", "zh-rHK")
        // The NNUE weights are already LZ4 and the classical model is dense
        // binary: deflating them again costs install time and copy time and
        // saves nothing.
        noCompress += listOf("lz4", "bin")
    }

    // Sideloading needs a signed APK — an unsigned one will not install. The
    // keystore and its passwords are the user's, so they live in
    // `keystore.properties` beside this file, which is gitignored; without that
    // file the release build still runs and produces the unsigned APK it always
    // did, so nobody is blocked by a secret they do not have.
    //
    //   keystore.properties:
    //     storeFile=C:/Users/User/gomoku-dev/zenyth.jks
    //     storePassword=...
    //     keyAlias=zenyth
    //     keyPassword=...
    //
    //   create it once with:
    //     keytool -genkeypair -v -keystore zenyth.jks -alias zenyth     //             -keyalg RSA -keysize 2048 -validity 10000
    val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProperties != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 drops the code and resources nothing reaches - most of what
            // Compose and the icon set bring - and does nothing else: no
            // optimising, no renaming (proguard-rules.pro). The engine's own
            // packages are kept whole, and its binaries and weights are assets
            // and native libraries R8 never touches; CI checks those byte for
            // byte (.github/scripts/check_engine_files.py).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // What the Baseline Profile is recorded against (Phase 7-2): the release
        // build, R8 and all, signed with the debug key so the generator can
        // install it. Only with -PbaselineProfile; no other build has it.
        if (project.hasProperty("baselineProfile")) {
            create("benchmark") {
                initWith(getByName("release"))
                signingConfig = signingConfigs.getByName("debug")
                matchingFallbacks += listOf("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Kotlin's JVM target follows targetCompatibility under AGP 9's built-in Kotlin.
    // buildConfig: the About screen states the version, and a version the
    // user cannot read is a bug report we cannot place.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            // The on-device engine is not a library we load, it is a program we
            // execute — and since Android 10 the only place an app may execute
            // from is the directory the package manager extracted its native
            // libraries into. With the modern default (uncompressed, mapped
            // straight out of the APK) nothing is extracted and there is no
            // executable path at all, so `libengine.so` would be dead weight.
            useLegacyPackaging = true
        }
    }

    testOptions {
        // The screenshot tests draw real Compose UI under Robolectric, which needs
        // the merged resources: the RapfiSans font, the strings, the theme.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // A good many tests assert user-facing text, and that text is now
            // whichever side of `tr()` the JVM's default locale selects. Pin it,
            // or the suite passes on a Korean machine and fails on any other.
            it.systemProperty("user.language", "ko")
            it.systemProperty("user.country", "KR")
            // Screenshot mode, without the Roborazzi Gradle plugin (one plugin fewer
            // for AGP 9 and for the F-Droid build): `-Pscreenshots=record` writes
            // the goldens in src/test/screenshots, `verify` fails on any pixel
            // that moved, `compare` only reports. Unset, the tests just compose.
            (project.findProperty("screenshots") as String?)?.let { mode ->
                require(mode in setOf("record", "verify", "compare")) { "screenshots=$mode" }
                it.systemProperty("roborazzi.test.$mode", "true")
                // The goldens are not an input Gradle knows about, so a verify
                // after a golden changed came back FROM-CACHE and checked
                // nothing, and a cached record would write nothing. In
                // screenshot mode the tests always run.
                it.outputs.cacheIf { false }
                it.outputs.upToDateWhen { false }
            }
        }
    }
}

// `-PcomposeReports`: the compiler's verdict on every composable — skippable or
// not, which parameters it takes for stable — written under build/compose_*.
// The performance work reads these (docs/리팩토링-성능-위생-계획.md); an ordinary
// build never sets the property and compiles exactly as before.
composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)

    // Screenshot tests (docs/UI-개편-마스터플랜.md P0). Test classpath only; the
    // manifest artifact adds the empty test activity to debug builds alone.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
