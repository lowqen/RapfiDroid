pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zenyth"
include(":app")

// The Baseline Profile generator (docs/리팩토링-성능-위생-계획.md, Phase 7-2): only
// with -PbaselineProfile, which baseline-profile.yml passes. Every other build -
// local, CI, release, F-Droid - configures exactly the one module it always did.
if (providers.gradleProperty("baselineProfile").isPresent) {
    include(":baselineprofile")
}
