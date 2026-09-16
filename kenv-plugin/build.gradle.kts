plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "io.github.adventures92"
version = providers.gradleProperty("VERSION_NAME").get()

repositories {
    mavenCentral()
}

dependencies {
    // Gradle API
    implementation(gradleApi())

    // Kotlin stdlib
    implementation(kotlin("stdlib"))

    // YAML parsing
    implementation(libs.snakeyaml)

    // TOML parsing
    implementation(libs.toml4j)

    // Test dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertionsCore)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kenv") {
            id = "io.github.adventures92.kenv-config"
            implementationClass = "adven.kenv.config.plugin.KEnvPlugin"
            displayName = "KEnv Config Plugin"
            description = "Schema-based, type-safe environment variable management for Kotlin Multiplatform projects"
        }
    }
}

mavenPublishing {
    // Central Portal is the only host from 0.33 onwards — the SonatypeHost argument this used to
    // take was removed when the legacy OSSRH endpoints were retired. The release workflow runs
    // `publishAndReleaseToMavenCentral`, which promotes the deployment once Central's validation
    // passes; a plain `publishToMavenCentral` leaves it waiting for a manual Publish in the portal.
    publishToMavenCentral()

    // Sign only when a key is configured, so a local `publishToMavenLocal` works without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "kenv-config", version.toString())

    pom {
        name.set("KEnv Config")
        description.set("Schema-based, type-safe environment variable management for Kotlin Multiplatform projects")
        inceptionYear.set("2025")
        url.set("https://github.com/adventures92/k_env_config")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("adventures92")
                name.set("adventures92")
                url.set("https://github.com/adventures92")
            }
        }

        scm {
            url.set("https://github.com/adventures92/k_env_config")
            connection.set("scm:git:git://github.com/adventures92/k_env_config.git")
            developerConnection.set("scm:git:ssh://git@github.com/adventures92/k_env_config.git")
        }
    }
}

// This is a separate Gradle build, so it applies its own formatter and static analysis; the root
// build's spotless excludes `kenv-plugin/**` for exactly that reason. Both plugins resolve from
// this build's own `pluginManagement` repositories (gradlePluginPortal) and both versions come from
// the shared catalog wired in settings.gradle.kts — never inline.
//
// There is deliberately NO `.editorconfig` anywhere in this repository. ktlint reads one when it is
// present, and any Kotlin style key in it silently overrides what is configured here, reformatting
// the whole tree. Formatter configuration belongs in the build script.
spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "android" to "true",
                // Kotest's generator DSL is a flat package of several dozen `Arb.xxx()` extension
                // functions and is designed to be imported on demand; naming them individually
                // would add ~15 import lines per property test and nothing else. This is the only
                // package allowed a wildcard — note that setting this key REPLACES ktlint's
                // default list, so no other on-demand import is permitted, including this
                // project's own packages.
                "ij_kotlin_packages_to_use_import_on_demand" to "io.kotest.property.arbitrary.**",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// The config file lives at the repository root so both builds read one copy. `rootProject` here is
// the *kenv-plugin* build, whose directory is `kenv-plugin/` — hence the `..`.
//
// `detekt-baseline.xml` records the 17 findings that already existed when static analysis was first
// introduced (mostly `LongMethod` in the parsers and the code generator). It is accepted debt, not
// a licence: the rules are live, so anything new fails the build, and every entry is one line of
// XML naming the exact function — delete the line once the function is split. Raising a threshold
// in detekt.yml instead would have hidden the same debt everywhere, permanently and invisibly.
detekt {
    config.setFrom(rootProject.file("../detekt.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom("src/main/kotlin")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
