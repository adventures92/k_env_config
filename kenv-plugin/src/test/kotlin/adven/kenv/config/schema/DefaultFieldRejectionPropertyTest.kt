package adven.kenv.config.schema

import adven.kenv.config.generators.SchemaGenerators
import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

/**
 * Property 2: Default field rejection
 *
 * For any YAML schema content where at least one variable definition contains a `default` field,
 * the parser SHALL return a Failure result with an error message indicating that defaults are
 * not supported.
 *
 * Feature: plugin-v2-restructure, Property 2: Default field rejection
 *
 * **Validates: Requirements 2.1**
 */
@OptIn(ExperimentalKotest::class)
class DefaultFieldRejectionPropertyTest : FunSpec({

    val parser = YamlSchemaParser()

    test("Property 2: Default field rejection - parser rejects schemas containing default fields") {
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchemaYamlWithDefault()) { yaml ->
            // Parse the YAML that contains at least one default field
            val result = parser.parse(yaml, "test.yaml")

            // Assert the result is a Failure
            result.shouldBeInstanceOf<ParseResult.Failure>()

            // Assert at least one error message contains "not supported"
            val hasNotSupportedError = result.errors.any { error ->
                error.message.contains("not supported")
            }
            hasNotSupportedError shouldBe true
        }
    }
})
