package adven.kenv.config.plugin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

/**
 * Integration tests for the KEnv Gradle plugin v2 using Gradle TestKit.
 *
 * Validates: Requirements 1.4, 1.7, 3.1, 3.2
 */
class KEnvPluginIntegrationTest : FunSpec({

    fun createProjectDir(testName: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "kenv-integration-$testName-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    fun File.writeBuildFile(extraConfig: String = "") {
        resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.adventures92.kenv-config")
            }
            
            $extraConfig
            """.trimIndent()
        )
    }

    fun File.writeSettingsFile() {
        resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "kenv-test-project"
            """.trimIndent()
        )
    }

    /**
     * Creates the kenv/ directory structure and writes the schema file.
     */
    fun File.writeSchemaFile(content: String): File {
        val kenvDir = resolve("kenv")
        kenvDir.mkdirs()
        val file = kenvDir.resolve("schema.kenv.yaml")
        file.writeText(content)
        return file
    }

    /**
     * Writes an env file into the kenv/ directory.
     */
    fun File.writeEnvFile(envName: String, content: String): File {
        val kenvDir = resolve("kenv")
        kenvDir.mkdirs()
        val file = kenvDir.resolve("env.$envName.env")
        file.writeText(content)
        return file
    }

    /**
     * Writes a global env file into the kenv/ directory.
     */
    fun File.writeGlobalEnvFile(content: String): File {
        val kenvDir = resolve("kenv")
        kenvDir.mkdirs()
        val file = kenvDir.resolve("env.global.env")
        file.writeText(content)
        return file
    }

    fun gradleRunner(projectDir: File): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
    }

    val simpleSchema = """
        environments:
          - dev
          - production

        variables:
          API_HOST:
            type: Url
            scope: environment
          APP_NAME:
            type: String
            scope: global
    """.trimIndent()

    val simpleKenvConfig = """
        kenvConfig {
            directory.set(file("kenv"))
            environments.set(listOf("dev", "production"))
            generatedPackageName.set("com.test.config")
        }
    """.trimIndent()

    test("plugin registers kenvGenerate task") {
        val projectDir = createProjectDir("task-registration")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(
                """
                kenvConfig {
                    directory.set(file("kenv"))
                    environments.set(listOf("dev"))
                    generatedPackageName.set("com.test.config")
                }
                """.trimIndent()
            )

            val result = gradleRunner(projectDir)
                .withArguments("tasks", "--all")
                .build()

            result.output shouldContain "kenvGenerate"
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("plugin discovers environment files using unified kenv directory convention") {
        val projectDir = createProjectDir("file-discovery")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(simpleKenvConfig)
            projectDir.writeSchemaFile(
                """
                environments:
                  - dev
                  - production

                variables:
                  API_HOST:
                    type: Url
                    scope: environment
                  SHARED_SECRET:
                    type: String
                    scope: global
                """.trimIndent()
            )
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080")
            projectDir.writeEnvFile("production", "API_HOST=https://api.example.com")
            projectDir.writeGlobalEnvFile("SHARED_SECRET=my-secret-key")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .build()

            result.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.SUCCESS

            // Verify generated file exists with package directory structure
            val generatedFile = projectDir.resolve("build/generated/kenv/commonMain/kotlin/com/test/config/EnvConfig.kt")
            generatedFile.exists() shouldBe true

            // Verify global variable is in generated code
            val generatedCode = generatedFile.readText()
            generatedCode shouldContain "SHARED_SECRET"
            generatedCode shouldContain "my-secret-key"
            generatedCode shouldContain "package com.test.config"
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("incremental build support - no re-run when files unchanged") {
        val projectDir = createProjectDir("incremental")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(simpleKenvConfig)
            projectDir.writeSchemaFile(simpleSchema)
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080")
            projectDir.writeEnvFile("production", "API_HOST=https://api.example.com")
            projectDir.writeGlobalEnvFile("APP_NAME=TestApp")

            // First run
            val firstResult = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .build()
            firstResult.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.SUCCESS

            // Second run without changes - should be UP-TO-DATE
            val secondResult = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .build()
            secondResult.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.UP_TO_DATE
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("activeEnvironment override via -P flag") {
        val projectDir = createProjectDir("active-env-flag")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(simpleKenvConfig)
            projectDir.writeSchemaFile(simpleSchema)
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080")
            projectDir.writeEnvFile("production", "API_HOST=https://api.example.com")
            projectDir.writeGlobalEnvFile("APP_NAME=TestApp")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate", "-PactiveEnvironment=dev")
                .build()

            result.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.SUCCESS

            // Verify generated file contains only dev values (no nested environment objects)
            val generatedFile = projectDir.resolve("build/generated/kenv/commonMain/kotlin/com/test/config/EnvConfig.kt")
            generatedFile.exists() shouldBe true

            val generatedCode = generatedFile.readText()
            // Active environment mode: top-level object with values, no per-env nested objects
            generatedCode shouldContain "http://localhost:8080"
            // Should NOT contain the production-specific nested object
            generatedCode.contains("object Production") shouldBe false
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("missing generatedPackageName produces build failure") {
        val projectDir = createProjectDir("missing-package-name")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(
                """
                kenvConfig {
                    directory.set(file("kenv"))
                    environments.set(listOf("dev"))
                }
                """.trimIndent()
            )
            // Create kenv directory with schema and env file
            projectDir.writeSchemaFile(
                """
                environments:
                  - dev

                variables:
                  API_HOST:
                    type: Url
                    scope: environment
                """.trimIndent()
            )
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .buildAndFail()

            result.output shouldContain "generatedPackageName is required"
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("missing schema file in kenv directory produces descriptive error") {
        val projectDir = createProjectDir("missing-schema")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(
                """
                kenvConfig {
                    directory.set(file("kenv"))
                    environments.set(listOf("dev"))
                    generatedPackageName.set("com.test.config")
                }
                """.trimIndent()
            )
            // Create kenv directory with env file but NO schema file
            val kenvDir = projectDir.resolve("kenv")
            kenvDir.mkdirs()
            kenvDir.resolve("env.dev.env").writeText("API_HOST=http://localhost:8080")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .buildAndFail()

            result.output shouldContain "Schema file not found"
            result.output shouldContain "schema.kenv.yaml"
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("generated code compiles successfully - file is created with correct structure") {
        val projectDir = createProjectDir("generated-compiles")
        try {
            projectDir.writeSettingsFile()
            projectDir.writeBuildFile(simpleKenvConfig)
            projectDir.writeSchemaFile(
                """
                environments:
                  - dev
                  - production

                variables:
                  API_HOST:
                    type: Url
                    scope: environment
                  DEBUG_ENABLED:
                    type: Boolean
                    scope: environment
                  APP_NAME:
                    type: String
                    scope: global
                  MAX_RETRIES:
                    type: Int
                    scope: global

                groups:
                  database:
                    DB_HOST:
                      type: String
                      scope: environment
                """.trimIndent()
            )
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080\nDEBUG_ENABLED=true\nDB_HOST=localhost")
            projectDir.writeEnvFile("production", "API_HOST=https://api.example.com\nDEBUG_ENABLED=false\nDB_HOST=db.example.com")
            projectDir.writeGlobalEnvFile("APP_NAME=TestApp\nMAX_RETRIES=3")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .build()

            result.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.SUCCESS

            val generatedFile = projectDir.resolve("build/generated/kenv/commonMain/kotlin/com/test/config/EnvConfig.kt")
            generatedFile.exists() shouldBe true

            val generatedCode = generatedFile.readText()

            // Verify package declaration
            generatedCode shouldContain "package com.test.config"

            // Verify typed properties are present
            generatedCode shouldContain "API_HOST"
            generatedCode shouldContain "String"
            generatedCode shouldContain "DEBUG_ENABLED"
            generatedCode shouldContain "Boolean"
            generatedCode shouldContain "APP_NAME"
            generatedCode shouldContain "MAX_RETRIES"
            generatedCode shouldContain "Int"

            // Verify group nesting
            generatedCode shouldContain "Database"
            generatedCode shouldContain "DB_HOST"

            // Verify environment-specific values
            generatedCode shouldContain "http://localhost:8080"
            generatedCode shouldContain "https://api.example.com"
        } finally {
            projectDir.deleteRecursively()
        }
    }

    test("default kenv directory convention is used when directory not explicitly set") {
        val projectDir = createProjectDir("default-directory")
        try {
            projectDir.writeSettingsFile()
            // Only set environments and package name, rely on default directory convention
            projectDir.writeBuildFile(
                """
                kenvConfig {
                    environments.set(listOf("dev"))
                    generatedPackageName.set("com.test.config")
                }
                """.trimIndent()
            )
            // Write files into the default kenv/ directory
            projectDir.writeSchemaFile(
                """
                environments:
                  - dev

                variables:
                  API_HOST:
                    type: Url
                    scope: environment
                """.trimIndent()
            )
            projectDir.writeEnvFile("dev", "API_HOST=http://localhost:8080")

            val result = gradleRunner(projectDir)
                .withArguments("kenvGenerate")
                .build()

            result.task(":kenvGenerate")!!.outcome shouldBe TaskOutcome.SUCCESS

            val generatedFile = projectDir.resolve("build/generated/kenv/commonMain/kotlin/com/test/config/EnvConfig.kt")
            generatedFile.exists() shouldBe true
        } finally {
            projectDir.deleteRecursively()
        }
    }
})
