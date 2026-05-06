package adven.kenv.config.schema

import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property 12: Parser error reporting includes location
 *
 * For any invalid YAML schema content, the parser's error result SHALL include the `filePath`
 * that was passed to the parse function and a non-null `line` number.
 *
 * Feature: plugin-v2-restructure, Property 12: Parser error reporting includes location
 *
 * **Validates: Requirements 7.4**
 */
@OptIn(ExperimentalKotest::class)
class ParserErrorLocationPropertyTest : FunSpec({

    val parser = YamlSchemaParser()

    /**
     * Generates arbitrary file path strings (simple path-like strings).
     */
    val arbFilePath: Arb<String> = arbitrary {
        val segments = Arb.int(1..4).bind()
        val pathParts = (1..segments).map {
            val length = Arb.int(3..10).bind()
            val chars = ('a'..'z') + ('0'..'9') + listOf('-', '_')
            (1..length).map { chars.random() }.joinToString("")
        }
        pathParts.joinToString("/") + ".yaml"
    }

    /**
     * Generates invalid YAML content that will cause parse errors.
     * Strategies:
     * 1. Completely invalid YAML syntax
     * 2. Valid YAML but missing required `environments` field
     * 3. Valid YAML with invalid type values
     * 4. Schema with `default` field (rejected in v2)
     */
    val arbInvalidYaml: Arb<String> = Arb.choice(
        // Strategy 1: Completely invalid YAML syntax
        arbitrary {
            val invalidPatterns = listOf(
                "{{invalid yaml",
                ":\n  - :\n    :",
                "[[[unclosed",
                "key: *undefined_anchor",
                "%invalid_directive\n---\nkey: value"
            )
            invalidPatterns.random()
        },
        // Strategy 2: Valid YAML but missing required `environments` field
        arbitrary {
            val length = Arb.int(5..10).bind()
            val varName = (1..length).map { ('a'..'z').random() }.joinToString("")
            """
            |variables:
            |  $varName:
            |    type: String
            """.trimMargin()
        },
        // Strategy 3: Valid YAML with invalid type values
        arbitrary {
            val length = Arb.int(5..10).bind()
            val varName = (1..length).map { ('a'..'z').random() }.joinToString("")
            val typeLength = Arb.int(3..8).bind()
            val invalidType = (1..typeLength).map { ('a'..'z').random() }.joinToString("")
            """
            |environments:
            |  - dev
            |variables:
            |  $varName:
            |    type: $invalidType
            """.trimMargin()
        },
        // Strategy 4: Schema with `default` field (rejected in v2)
        arbitrary {
            val length = Arb.int(5..10).bind()
            val varName = (1..length).map { ('a'..'z').random() }.joinToString("")
            val defaultLength = Arb.int(3..10).bind()
            val defaultVal = (1..defaultLength).map { ('a'..'z').random() }.joinToString("")
            """
            |environments:
            |  - dev
            |variables:
            |  $varName:
            |    type: String
            |    default: "$defaultVal"
            """.trimMargin()
        }
    )

    test("Property 12: Parser error reporting includes location - errors contain filePath and non-null line") {
        checkAll(PropTestConfig(iterations = 100), arbFilePath, arbInvalidYaml) { filePath, invalidYaml ->
            // Parse the invalid YAML with the given file path
            val result = parser.parse(invalidYaml, filePath)

            // Assert the result is a Failure
            result.shouldBeInstanceOf<ParseResult.Failure>()

            // Assert there is at least one error
            result.errors.shouldNotBeEmpty()

            // Assert every error includes the filePath that was passed to parse
            for (error in result.errors) {
                error.filePath shouldBe filePath
            }

            // Assert every error has a positive line number (line is always present)
            for (error in result.errors) {
                error.line shouldBeGreaterThan 0
            }
        }
    }
})
