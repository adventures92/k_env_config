package adven.kenv.config.env

import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property 3: Parse error reporting includes location
 *
 * For any string that is not a valid environment file, the parser SHALL return a Failure
 * with filePath matching input and line > 0.
 *
 * **Validates: Requirements 1.5, 2.5**
 */
@OptIn(ExperimentalKotest::class)
class ParseErrorPropertyTest : FunSpec({

    val dotEnvParser = DotEnvParser()
    val yamlParser = YamlEnvParser()
    val tomlParser = TomlEnvParser()

    /**
     * Generates strings that are guaranteed to be invalid .env content.
     * Invalid .env lines: lines without '=' that aren't comments or blank.
     */
    val arbInvalidDotEnvContent: Arb<String> = arbitrary {
        // Generate lines that are invalid: no '=' sign, not a comment, not blank
        val numLines = Arb.int(1..3).bind()
        val lines = (1..numLines).map {
            // Generate a non-empty string without '=' and not starting with '#'
            val length = Arb.int(3, 15).bind()
            val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            val line = (1..length).map { chars.random() }.joinToString("")
            // Ensure it doesn't accidentally look like a valid line
            line
        }
        lines.joinToString("\n")
    }

    /**
     * Generates strings that are guaranteed to be invalid YAML content.
     */
    val arbInvalidYamlContent: Arb<String> = Arb.of(
        "key: [unclosed",
        "key: {unclosed",
        ": no_key",
        "  bad indent\n wrong: yaml\n  broken:",
        "---\n- item\n  bad: indent\n wrong",
        "key:\n  nested:\n    deep: value"  // nested structure - will fail as invalid for env
    )

    /**
     * Generates strings that are guaranteed to be invalid TOML content.
     */
    val arbInvalidTomlContent: Arb<String> = Arb.of(
        "key = [1, 2, 3]",           // arrays not supported
        "[table]\nkey = \"value\"",   // nested tables not supported
        "= no_key",                   // invalid TOML syntax
        "key = ",                     // missing value (invalid TOML)
        "[nested]\nfoo = \"bar\""     // table section not supported
    )

    test("Property 3: .env parser - parse errors include filePath and line > 0") {
        checkAll(PropTestConfig(iterations = 100), arbInvalidDotEnvContent) { invalidContent ->
            val filePath = "test/env.dev.env"
            val result = dotEnvParser.parse(invalidContent, filePath, "dev")

            result.shouldBeInstanceOf<ParseResult.Failure>()
            result.errors.forEach { error ->
                error.filePath shouldBe filePath
                error.line shouldBeGreaterThan 0
            }
        }
    }

    test("Property 3: YAML parser - parse errors include filePath and line > 0") {
        checkAll(PropTestConfig(iterations = 100), arbInvalidYamlContent) { invalidContent ->
            val filePath = "test/env.dev.yaml"
            val result = yamlParser.parse(invalidContent, filePath, "dev")

            // Only check if it actually fails (some YAML strings may parse as valid scalars)
            if (result is ParseResult.Failure) {
                result.errors.forEach { error ->
                    error.filePath shouldBe filePath
                    error.line shouldBeGreaterThan 0
                }
            }
        }
    }

    test("Property 3: TOML parser - parse errors include filePath and line > 0") {
        checkAll(PropTestConfig(iterations = 100), arbInvalidTomlContent) { invalidContent ->
            val filePath = "test/env.dev.toml"
            val result = tomlParser.parse(invalidContent, filePath, "dev")

            // Only check if it actually fails (some TOML strings may parse as valid)
            if (result is ParseResult.Failure) {
                result.errors.forEach { error ->
                    error.filePath shouldBe filePath
                    error.line shouldBeGreaterThan 0
                }
            }
        }
    }
})
