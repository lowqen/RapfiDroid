import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.gomoku.yixindroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gomoku.yixindroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.12.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two languages, and only two. Without this the APK carries every locale
    // AndroidX ships and the system offers them, which would put a Compose
    // screen of Korean literals under a French system label.
    androidResources {
        localeFilters += listOf("en", "ko")
    }

    // Sideloading needs a signed APK — an unsigned one will not install. The
    // keystore and its passwords are the user's, so they live in
    // `keystore.properties` beside this file, which is gitignored; without that
    // file the release build still runs and produces the unsigned APK it always
    // did, so nobody is blocked by a secret they do not have.
    //
    //   keystore.properties:
    //     storeFile=C:/Users/User/gomoku-dev/yixindroid.jks
    //     storePassword=...
    //     keyAlias=yixindroid
    //     keyPassword=...
    //
    //   create it once with:
    //     keytool -genkeypair -v -keystore yixindroid.jks -alias yixindroid     //             -keyalg RSA -keysize 2048 -validity 10000
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // buildConfig: the About screen states the version, and a version the
    // user cannot read is a bug report we cannot place.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests.all {
            // A good many tests assert user-facing text, and that text is now
            // whichever side of `tr()` the JVM's default locale selects. Pin it,
            // or the suite passes on a Korean machine and fails on any other.
            it.systemProperty("user.language", "ko")
            it.systemProperty("user.country", "KR")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
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
}
