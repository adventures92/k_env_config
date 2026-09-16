rootProject.name = "kenv-plugin"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// This is a separate build (the root includes it via `includeBuild("kenv-plugin")`), so it does
// NOT inherit the root's version catalog — Gradle auto-detects gradle/libs.versions.toml only for
// the build that owns it. Without this block `libs` is undefined here and every version in
// build.gradle.kts has to be hardcoded, which is exactly how the plugin came to pin its own Kotlin
// version. Pointing at the same file keeps one source of truth for both builds.
//
// Only `versionCatalogs` is declared: repositoriesMode defaults to PREFER_PROJECT, so the
// `repositories {}` block in build.gradle.kts still applies.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
