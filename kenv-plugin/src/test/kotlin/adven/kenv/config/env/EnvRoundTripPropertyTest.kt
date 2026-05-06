package adven.kenv.config.env

import adven.kenv.config.generators.EnvConfigGenerators
import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

/**
 * Property 2: Environment file round-trip
 *
 * For any valid EnvironmentConfig and for each format (.env, .yaml, .toml),
 * printing then parsing SHALL produce identical key-value pairs.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 9.1, 9.2, 9.3, 9.4, 9.5**
 */
@OptIn(ExperimentalKotest::class)
class EnvRoundTripPropertyTest : FunSpec({

    val dotEnvParser = DotEnvParser()
    val yamlParser = YamlEnvParser()
    val tomlParser = TomlEnvParser()

    test("Property 2: .env format round-trip - print then parse produces identical key-value pairs") {
        checkAll(PropTestConfig(iterations = 100), EnvConfigGenerators.arbEnvironmentConfig(EnvFileFormat.DOT_ENV)) { originalConfig ->
            // Print the config to .env format
            val printed = dotEnvParser.print(originalConfig)

            // Parse it back
            val result = dotEnvParser.parse(printed, originalConfig.sourceFile, originalConfig.name)

            // Assert successful parse
            result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
            val parsedConfig = result.value

            // Assert key-value pairs are identical
            parsedConfig.values shouldBe originalConfig.values
            parsedConfig.name shouldBe originalConfig.name
            parsedConfig.format shouldBe EnvFileFormat.DOT_ENV
        }
    }

    test("Property 2: YAML format round-trip - print then parse produces identical key-value pairs") {
        checkAll(PropTestConfig(iterations = 100), EnvConfigGenerators.arbEnvironmentConfig(EnvFileFormat.YAML)) { originalConfig ->
            // Print the config to YAML format
            val printed = yamlParser.print(originalConfig)

            // Parse it back
            val result = yamlParser.parse(printed, originalConfig.sourceFile, originalConfig.name)

            // Assert successful parse
            result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
            val parsedConfig = result.value

            // Assert key-value pairs are identical
            parsedConfig.values shouldBe originalConfig.values
            parsedConfig.name shouldBe originalConfig.name
            parsedConfig.format shouldBe EnvFileFormat.YAML
        }
    }

    test("Property 2: TOML format round-trip - print then parse produces identical key-value pairs") {
        checkAll(PropTestConfig(iterations = 100), EnvConfigGenerators.arbEnvironmentConfig(EnvFileFormat.TOML)) { originalConfig ->
            // Print the config to TOML format
            val printed = tomlParser.print(originalConfig)

            // Parse it back
            val result = tomlParser.parse(printed, originalConfig.sourceFile, originalConfig.name)

            // Assert successful parse
            result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
            val parsedConfig = result.value

            // Assert key-value pairs are identical
            parsedConfig.values shouldBe originalConfig.values
            parsedConfig.name shouldBe originalConfig.name
            parsedConfig.format shouldBe EnvFileFormat.TOML
        }
    }
})
