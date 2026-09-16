import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.pluginPublish)
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

// `website`, `vcsUrl` and per-plugin `tags` are required by com.gradle.plugin-publish — the Portal
// rejects a publication without them. They live here rather than in a `pluginBundle {}` block,
// which that plugin removed in 1.0.
gradlePlugin {
    website.set("https://adventures92.github.io/k_env_config/")
    vcsUrl.set("https://github.com/adventures92/k_env_config")

    plugins {
        create("kenv") {
            id = "io.github.adventures92.kenv-config"
            implementationClass = "adven.kenv.config.plugin.KEnvPlugin"
            displayName = "KEnv Config Plugin"
            description = "Schema-based, type-safe environment variable management for Kotlin Multiplatform projects"
            tags.set(
                listOf(
                    "configuration",
                    "environment-variables",
                    "dotenv",
                    "code-generation",
                    "kotlin-multiplatform",
                    "android",
                ),
            )
        }
    }
}

mavenPublishing {
    // There must be exactly ONE javadoc jar, and it must contain Dokka's HTML. 0.2.0 published a
    // jar holding nothing but a manifest: Maven Central requires the file to exist and never
    // inspects it, so an empty stub passes validation in silence.
    //
    // `com.gradle.plugin-publish` already calls `java.withJavadocJar()`, so a `javadocJar` task
    // exists and the java component publishes it. Asking vanniktech for a second one via
    // `JavadocJar.Dokka(...)` added `dokkaJavadocJar` writing the same `build/libs/*-javadoc.jar`.
    // Gradle rejected that as an undeclared dependency; had it not, whichever task ran last would
    // win and the empty jar would come back intermittently.
    //
    // So vanniktech contributes none, and the existing `javadocJar` is filled from Dokka below.
    configure(GradlePlugin(javadocJar = JavadocJar.None()))

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

// Fill the javadoc jar from Dokka rather than from the Java `javadoc` task, which produces nothing
// for a Kotlin project.
//
// `matching { }.configureEach { }` rather than `tasks.named("javadocJar")`: plugin-publish registers
// that task from an `afterEvaluate`, so an eager lookup fails from anywhere in this script — it is
// not an ordering problem that moving the block solves. This form configures the task if and when
// it is created, and quietly does nothing if it never is.
//
// The Dokka task name matters. `dokkaHtml` is the V1 helper — under V2 it still exists, runs,
// produces nothing and reports success, so wiring it here would rebuild the empty jar with no error
// anywhere. `dokkaGeneratePublicationHtml` is the V2 task that actually emits HTML. This build's own
// task list shows `dokkaJavadoc - [⚠ V1 tasks disabled]`, which is the same trap one name over.
tasks.matching { it.name == "javadocJar" }.configureEach {
    this as Jar
    from(tasks.named("dokkaGeneratePublicationHtml"))
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
