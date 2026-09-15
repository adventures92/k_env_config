plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
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

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
