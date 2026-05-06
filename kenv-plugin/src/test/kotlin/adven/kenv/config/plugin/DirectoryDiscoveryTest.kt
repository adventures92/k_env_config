package adven.kenv.config.plugin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlin.io.path.createTempDirectory

@OptIn(io.kotest.common.ExperimentalKotest::class)
class DirectoryDiscoveryTest : FunSpec({

    /**
     * Generates a simple lowercase environment name (letters only, 3-10 chars).
     */
    fun arbEnvName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a file extension from the supported set.
     */
    fun arbExtension(): Arb<String> = Arb.element("env", "yaml", "yml", "toml")

    test("Property 11: Unified directory discovery - discoverEnvFile finds correct file by extension") {
        /**
         * **Validates: Requirements 1.1, 1.2**
         *
         * For any directory containing a file named `env.<envName>.<ext>` where ext is one of
         * env, yaml, yml, or toml, the discovery function SHALL return that file when asked
         * for environment envName.
         */
        checkAll(
            PropTestConfig(iterations = 100),
            arbEnvName(),
            arbExtension()
        ) { envName, ext ->
            val tempDir = createTempDirectory("kenv-discovery-test")
            try {
                val expectedFile = tempDir.resolve("env.$envName.$ext").toFile()
                expectedFile.createNewFile()

                val discovery = DirectoryDiscovery()
                val result = discovery.discoverEnvFile(tempDir.toFile(), envName)

                result shouldBe expectedFile
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    test("Property 11: Unified directory discovery - discoverEnvFile returns null when no matching file exists") {
        /**
         * **Validates: Requirements 1.1, 1.2**
         *
         * For any directory NOT containing a file matching `env.<envName>.<ext>`,
         * the discovery function SHALL return null.
         */
        checkAll(
            PropTestConfig(iterations = 100),
            arbEnvName()
        ) { envName ->
            val tempDir = createTempDirectory("kenv-discovery-test")
            try {
                // Directory is empty - no matching file exists
                val discovery = DirectoryDiscovery()
                val result = discovery.discoverEnvFile(tempDir.toFile(), envName)

                result.shouldBeNull()
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    test("Property 11: Unified directory discovery - discoverGlobalFile finds correct file by extension") {
        /**
         * **Validates: Requirements 1.1, 1.2**
         *
         * For any directory containing a file named `env.global.<ext>` where ext is one of
         * env, yaml, yml, or toml, the discovery function SHALL return that file.
         */
        checkAll(
            PropTestConfig(iterations = 100),
            arbExtension()
        ) { ext ->
            val tempDir = createTempDirectory("kenv-discovery-test")
            try {
                val expectedFile = tempDir.resolve("env.global.$ext").toFile()
                expectedFile.createNewFile()

                val discovery = DirectoryDiscovery()
                val result = discovery.discoverGlobalFile(tempDir.toFile())

                result shouldBe expectedFile
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    test("Property 11: Unified directory discovery - discoverGlobalFile returns null when no matching file exists") {
        /**
         * **Validates: Requirements 1.1, 1.2**
         *
         * For any directory NOT containing a file matching `env.global.<ext>`,
         * the discovery function SHALL return null.
         */
        val tempDir = createTempDirectory("kenv-discovery-test")
        try {
            // Directory is empty - no matching global file exists
            val discovery = DirectoryDiscovery()
            val result = discovery.discoverGlobalFile(tempDir.toFile())

            result.shouldBeNull()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // ===== Example-Based Unit Tests =====

    test("discoverEnvFile finds env.dev.env when it exists") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            val envFile = tempDir.resolve("env.dev.env").toFile()
            envFile.createNewFile()

            val discovery = DirectoryDiscovery()
            val result = discovery.discoverEnvFile(tempDir.toFile(), "dev")

            result.shouldNotBeNull()
            result shouldBe envFile
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discoverEnvFile finds env.dev.yaml when .env does not exist but .yaml does (extension priority)") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            // Only create the .yaml file, not the .env file
            val yamlFile = tempDir.resolve("env.dev.yaml").toFile()
            yamlFile.createNewFile()

            val discovery = DirectoryDiscovery()
            val result = discovery.discoverEnvFile(tempDir.toFile(), "dev")

            result.shouldNotBeNull()
            result shouldBe yamlFile
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discoverEnvFile returns null when no matching file exists") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            // Directory is empty — no env.dev.* files
            val discovery = DirectoryDiscovery()
            val result = discovery.discoverEnvFile(tempDir.toFile(), "dev")

            result.shouldBeNull()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discoverGlobalFile finds env.global.env when it exists") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            val globalFile = tempDir.resolve("env.global.env").toFile()
            globalFile.createNewFile()

            val discovery = DirectoryDiscovery()
            val result = discovery.discoverGlobalFile(tempDir.toFile())

            result.shouldNotBeNull()
            result shouldBe globalFile
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discoverGlobalFile returns null when no global file exists") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            // Directory has other files but no env.global.*
            tempDir.resolve("env.dev.env").toFile().createNewFile()

            val discovery = DirectoryDiscovery()
            val result = discovery.discoverGlobalFile(tempDir.toFile())

            result.shouldBeNull()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discover returns DiscoveredFiles with correct schema, env files, and global file") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            // Set up directory with schema, env files, and global file
            val schemaFile = tempDir.resolve("schema.kenv.yaml").toFile()
            schemaFile.writeText("environments: [dev, staging]")

            val devEnvFile = tempDir.resolve("env.dev.env").toFile()
            devEnvFile.createNewFile()

            val stagingEnvFile = tempDir.resolve("env.staging.yaml").toFile()
            stagingEnvFile.createNewFile()

            val globalFile = tempDir.resolve("env.global.env").toFile()
            globalFile.createNewFile()

            val discovery = DirectoryDiscovery()
            val result = discovery.discover(tempDir.toFile(), listOf("dev", "staging"))

            result.schemaFile shouldBe schemaFile
            result.envFiles shouldHaveSize 2
            result.envFiles shouldContainKey "dev"
            result.envFiles shouldContainKey "staging"
            result.envFiles["dev"] shouldBe devEnvFile
            result.envFiles["staging"] shouldBe stagingEnvFile
            result.globalFile.shouldNotBeNull()
            result.globalFile shouldBe globalFile
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("discover throws IllegalStateException when an env file is not found") {
        val tempDir = createTempDirectory("kenv-unit-test")
        try {
            // Only create the schema and one env file, but request two environments
            val schemaFile = tempDir.resolve("schema.kenv.yaml").toFile()
            schemaFile.writeText("environments: [dev, production]")

            val devEnvFile = tempDir.resolve("env.dev.env").toFile()
            devEnvFile.createNewFile()
            // Note: no env.production.* file exists

            val discovery = DirectoryDiscovery()
            val exception = shouldThrow<IllegalStateException> {
                discovery.discover(tempDir.toFile(), listOf("dev", "production"))
            }

            exception.message shouldContain "production"
            exception.message shouldContain "Environment file not found"
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
})
