plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.spotless)
}

// Formatting for this build's own sources: the demo app, and the root and `composeApp` build
// scripts.
//
// `kenv-plugin/` is excluded deliberately. It is a separate Gradle build and applies spotless
// itself, so its files are covered by `kenv-plugin:spotlessCheck`. Formatting them from here as
// well would give two tasks in two builds write access to the same files.
//
// There is deliberately NO `.editorconfig` in this repository. ktlint reads one when it is present,
// and any Kotlin style key in it silently overrides what is configured here — reformatting the
// whole tree. Formatter configuration belongs in the build script.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "kenv-plugin/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "android" to "true",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "kenv-plugin/**")
        ktlint(libs.versions.ktlint.get())
    }
}
