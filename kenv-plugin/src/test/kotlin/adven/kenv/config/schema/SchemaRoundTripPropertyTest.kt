package adven.kenv.config.schema

import adven.kenv.config.generators.SchemaGenerators
import adven.kenv.config.model.ParseResult
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

/**
 * Property 1: Schema round-trip (no defaults)
 *
 * For any valid Schema object (without defaultValue), printing to YAML and parsing back
 * SHALL produce a structurally equivalent Schema object — same environments, same variables
 * (name, type, scope, description), and same groups.
 *
 * **Validates: Requirements 7.1, 7.2, 7.3**
 */
@OptIn(ExperimentalKotest::class)
class SchemaRoundTripPropertyTest : FunSpec({

    val parser = YamlSchemaParser()

    test("Property 1: Schema round-trip (no defaults) - print then parse produces structurally equivalent Schema") {
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { originalSchema ->
            // Print the schema to YAML
            val yaml = parser.print(originalSchema)

            // Parse the YAML back
            val result = parser.parse(yaml, "test-schema.yaml")

            // Assert successful parse
            result.shouldBeInstanceOf<ParseResult.Success<Schema>>()
            val parsedSchema = result.value

            // Assert structural equality
            parsedSchema.environments shouldBe originalSchema.environments
            parsedSchema.variables.size shouldBe originalSchema.variables.size
            parsedSchema.groups.size shouldBe originalSchema.groups.size

            // Compare variables (order may differ due to YAML map parsing)
            val originalVarsByName = originalSchema.variables.associateBy { it.name }
            val parsedVarsByName = parsedSchema.variables.associateBy { it.name }
            originalVarsByName.keys shouldBe parsedVarsByName.keys
            for ((name, originalVar) in originalVarsByName) {
                val parsedVar = parsedVarsByName[name]!!
                parsedVar.type shouldBe originalVar.type
                parsedVar.scope shouldBe originalVar.scope
                parsedVar.description shouldBe originalVar.description
            }

            // Compare groups (order may differ)
            val originalGroupsByName = originalSchema.groups.associateBy { it.name }
            val parsedGroupsByName = parsedSchema.groups.associateBy { it.name }
            originalGroupsByName.keys shouldBe parsedGroupsByName.keys
            for ((groupName, originalGroup) in originalGroupsByName) {
                val parsedGroup = parsedGroupsByName[groupName]!!
                val origGroupVarsByName = originalGroup.variables.associateBy { it.name }
                val parsedGroupVarsByName = parsedGroup.variables.associateBy { it.name }
                origGroupVarsByName.keys shouldBe parsedGroupVarsByName.keys
                for ((varName, originalVar) in origGroupVarsByName) {
                    val parsedVar = parsedGroupVarsByName[varName]!!
                    parsedVar.type shouldBe originalVar.type
                    parsedVar.scope shouldBe originalVar.scope
                    parsedVar.description shouldBe originalVar.description
                }
            }
        }
    }
})
