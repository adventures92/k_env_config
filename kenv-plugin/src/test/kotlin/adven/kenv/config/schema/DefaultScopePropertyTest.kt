package adven.kenv.config.schema

import adven.kenv.config.generators.SchemaGenerators
import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

/**
 * Property 15: Default scope is environment
 *
 * For any schema variable definition that omits the `scope` field, the parser SHALL produce
 * a SchemaVariable with `scope = VariableScope.ENVIRONMENT`.
 *
 * **Validates: Requirements 1.8**
 */
@OptIn(ExperimentalKotest::class)
class DefaultScopePropertyTest : FunSpec({

    val parser = YamlSchemaParser()

    test("Property 15: Default scope is environment - variables without scope field default to ENVIRONMENT") {
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchemaYamlWithoutScope()) { yamlContent ->
            // Parse the YAML schema that has no scope fields
            val result = parser.parse(yamlContent, "test-schema.yaml")

            // Assert successful parse
            result.shouldBeInstanceOf<ParseResult.Success<Schema>>()
            val schema = result.value

            // There should be at least one variable
            schema.variables.shouldNotBeEmpty()

            // All variables should have scope = ENVIRONMENT since scope was omitted
            for (variable in schema.variables) {
                variable.scope shouldBe VariableScope.ENVIRONMENT
            }
        }
    }
})
