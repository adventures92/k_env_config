plugins {
    kotlin("jvm") version "2.3.20"
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.adventures92"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Gradle API
    implementation(gradleApi())

    // Kotlin stdlib
    implementation(kotlin("stdlib"))

    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")

    // TOML parsing
    implementation("com.moandjiezana.toml:toml4j:0.7.2")

    // Test dependencies
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
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
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "kenv-config", version.toString())

    pom {
        name.set("KEnv Config")
        description.set("Schema-based, type-safe environment variable management for Kotlin Multiplatform projects")
        inceptionYear.set("2025")
        url.set("https://github.com/adventures92/kenv-config")

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
            url.set("https://github.com/adventures92/kenv-config")
            connection.set("scm:git:git://github.com/adventures92/kenv-config.git")
            developerConnection.set("scm:git:ssh://git@github.com/adventures92/kenv-config.git")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
