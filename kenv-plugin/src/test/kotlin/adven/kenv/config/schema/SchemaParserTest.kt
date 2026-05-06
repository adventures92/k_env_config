package adven.kenv.config.schema

import adven.kenv.config.model.ParseResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [YamlSchemaParser].
 *
 * Validates: Requirements 1.1, 1.2, 1.5, 1.6, 1.7, 1.8
 */
class SchemaParserTest : FunSpec({

    val parser = YamlSchemaParser()

    test("parse a schema with all 7 type annotations and verify each maps correctly") {
        val yaml = """
            environments:
              - dev

            variables:
              VAR_STRING:
                type: String
                scope: environment
              VAR_INT:
                type: Int
                scope: environment
              VAR_LONG:
                type: Long
                scope: environment
              VAR_DOUBLE:
                type: Double
                scope: environment
              VAR_FLOAT:
                type: Float
                scope: environment
              VAR_BOOLEAN:
                type: Boolean
                scope: environment
              VAR_URL:
                type: Url
                scope: environment
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Success<Schema>>()

        val variables = result.value.variables
        variables shouldHaveSize 7

        val byName = variables.associateBy { it.name }
        byName["VAR_STRING"]!!.type shouldBe SchemaType.STRING
        byName["VAR_INT"]!!.type shouldBe SchemaType.INT
        byName["VAR_LONG"]!!.type shouldBe SchemaType.LONG
        byName["VAR_DOUBLE"]!!.type shouldBe SchemaType.DOUBLE
        byName["VAR_FLOAT"]!!.type shouldBe SchemaType.FLOAT
        byName["VAR_BOOLEAN"]!!.type shouldBe SchemaType.BOOLEAN
        byName["VAR_URL"]!!.type shouldBe SchemaType.URL
    }

    test("parse a variable without scope field and verify it defaults to ENVIRONMENT") {
        val yaml = """
            environments:
              - dev

            variables:
              API_KEY:
                type: String
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Success<Schema>>()

        val variable = result.value.variables.first()
        variable.name shouldBe "API_KEY"
        variable.scope shouldBe VariableScope.ENVIRONMENT
    }

    test("parse invalid YAML and verify error includes file path and line > 0") {
        val invalidYaml = """
            environments:
              - dev
            variables:
              BAD_VAR: [invalid
                nested: broken
        """.trimIndent()

        val result = parser.parse(invalidYaml, "path/to/schema.yaml")
        result.shouldBeInstanceOf<ParseResult.Failure>()

        val error = result.errors.first()
        error.filePath shouldBe "path/to/schema.yaml"
        (error.line > 0) shouldBe true
    }

    test("parse a schema with groups and verify group names and nested variables") {
        val yaml = """
            environments:
              - dev
              - production

            groups:
              database:
                DB_HOST:
                  type: String
                  scope: environment
                DB_PORT:
                  type: Int
                  scope: global
              analytics:
                TRACKING_ID:
                  type: String
                  scope: global
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Success<Schema>>()

        val groups = result.value.groups
        groups shouldHaveSize 2

        val groupsByName = groups.associateBy { it.name }
        groupsByName.keys shouldBe setOf("database", "analytics")

        val dbGroup = groupsByName["database"]!!
        dbGroup.variables shouldHaveSize 2
        val dbVarsByName = dbGroup.variables.associateBy { it.name }
        dbVarsByName["DB_HOST"]!!.type shouldBe SchemaType.STRING
        dbVarsByName["DB_HOST"]!!.scope shouldBe VariableScope.ENVIRONMENT
        dbVarsByName["DB_PORT"]!!.type shouldBe SchemaType.INT
        dbVarsByName["DB_PORT"]!!.scope shouldBe VariableScope.GLOBAL

        val analyticsGroup = groupsByName["analytics"]!!
        analyticsGroup.variables shouldHaveSize 1
        analyticsGroup.variables.first().name shouldBe "TRACKING_ID"
    }

    test("parse a variable with a default field and verify it is rejected with clear message") {
        val yaml = """
            environments:
              - dev

            variables:
              APP_NAME:
                type: String
                scope: global
                default: "MyApp"
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Failure>()

        val error = result.errors.first()
        error.message shouldContain "not supported in v2"
        error.message shouldContain "APP_NAME"
        error.filePath shouldBe "schema.kenv.yaml"
    }

    test("parse a variable with a description and verify it is stored") {
        val yaml = """
            environments:
              - dev

            variables:
              API_HOST:
                type: Url
                scope: environment
                description: "The base URL for the API server"
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Success<Schema>>()

        val variable = result.value.variables.first()
        variable.name shouldBe "API_HOST"
        variable.description shouldBe "The base URL for the API server"
    }

    test("parse a schema with unknown type and verify error message") {
        val yaml = """
            environments:
              - dev

            variables:
              BAD_VAR:
                type: BigDecimal
                scope: environment
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Failure>()

        val error = result.errors.first()
        error.message shouldContain "Unknown type"
        error.message shouldContain "BigDecimal"
        error.message shouldContain "BAD_VAR"
        error.filePath shouldBe "schema.kenv.yaml"
    }

    test("parse an empty schema and verify error") {
        val yaml = ""

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Failure>()

        val error = result.errors.first()
        error.filePath shouldBe "schema.kenv.yaml"
        error.message shouldContain "empty"
    }

    test("parse a schema missing the environments field and verify error") {
        val yaml = """
            variables:
              API_KEY:
                type: String
                scope: environment
        """.trimIndent()

        val result = parser.parse(yaml, "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Failure>()

        val error = result.errors.first()
        error.filePath shouldBe "schema.kenv.yaml"
        error.message shouldContain "environments"
    }
})
