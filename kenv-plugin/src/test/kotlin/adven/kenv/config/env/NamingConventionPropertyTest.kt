package adven.kenv.config.env

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property 14: Environment file naming convention extraction
 *
 * For any valid environment name and supported extension, constructing `env.<name>.<ext>`
 * and extracting SHALL return the original name.
 *
 * **Validates: Requirements 2.6**
 */
@OptIn(ExperimentalKotest::class)
class NamingConventionPropertyTest : FunSpec({

    /**
     * Generates a valid environment name: lowercase letters only, no dots.
     */
    val arbEnvName: Arb<String> = arbitrary {
        val length = Arb.int(2..15).bind()
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a supported file extension.
     */
    val arbExtension: Arb<String> = Arb.of("env", "yaml", "yml", "toml")

    test("Property 14: Naming convention extraction - constructing env.<name>.<ext> and extracting returns original name") {
        checkAll(PropTestConfig(iterations = 100), arbEnvName, arbExtension) { envName, extension ->
            // Construct filename using the pattern
            val filename = "env.$envName.$extension"

            // Extract the environment name
            val extracted = EnvFileNaming.extractEnvironmentName(filename)

            // Assert round-trip
            extracted shouldBe envName
        }
    }

    test("Property 14: Naming convention format detection - constructing env.<name>.<ext> and detecting format returns correct format") {
        checkAll(PropTestConfig(iterations = 100), arbEnvName, arbExtension) { envName, extension ->
            val filename = "env.$envName.$extension"

            val format = EnvFileNaming.detectFormat(filename)

            val expectedFormat = when (extension) {
                "env" -> EnvFileFormat.DOT_ENV
                "yaml", "yml" -> EnvFileFormat.YAML
                "toml" -> EnvFileFormat.TOML
                else -> null
            }
            format shouldBe expectedFormat
        }
    }

    test("Property 14: Global file detection - env.global.<ext> is detected as global") {
        checkAll(PropTestConfig(iterations = 100), arbExtension) { extension ->
            val filename = "env.global.$extension"
            EnvFileNaming.isGlobalFile(filename) shouldBe true
        }
    }

    test("Property 14: Non-global files are not detected as global") {
        checkAll(PropTestConfig(iterations = 100), arbEnvName) { envName ->
            // Ensure the name is not "global"
            if (envName != "global") {
                val filename = "env.$envName.yaml"
                EnvFileNaming.isGlobalFile(filename) shouldBe false
            }
        }
    }
})
