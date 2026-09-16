package adven.kenv.config.schema

import adven.kenv.config.model.ParseResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Regression tests: YAML 1.1 implicit typing must not rewrite anything in a schema file.
 *
 * Every scalar in a schema is consumed as a String — environment names, variable names, type and
 * scope names, descriptions — so SnakeYAML's implicit resolver has nothing to offer and plenty to
 * break. Before this was fixed the parser used a default `Yaml()`, which coerced the text *before*
 * it was converted back to a String.
 *
 * The variable-name case is the sharpest of these: KEnv variables are conventionally
 * SCREAMING_SNAKE_CASE, and YAML 1.1 resolves `ON`, `OFF`, `YES`, `NO`, `Y` and `N` in any casing
 * as booleans, so a variable named `ON` silently became `true`.
 */
class SchemaImplicitTypingTest : FunSpec({

    val parser = YamlSchemaParser()

    fun parse(yaml: String): Schema {
        val result = parser.parse(yaml.trimIndent(), "schema.kenv.yaml")
        result.shouldBeInstanceOf<ParseResult.Success<Schema>>()
        return result.value
    }

    test("environment names that YAML 1.1 reads as booleans keep their text") {
        val schema = parse(
            """
            environments:
              - dev
              - no
              - on
              - off
              - yes
            """
        )
        schema.environments shouldContainExactly listOf("dev", "no", "on", "off", "yes")
    }

    test("an environment name that looks numeric is not renormalised") {
        // 1.10 parsed as a Double becomes 1.1 — a different environment.
        val schema = parse(
            """
            environments:
              - 1.10
              - 0755
            """
        )
        schema.environments shouldContainExactly listOf("1.10", "0755")
    }

    test("variable names that YAML 1.1 reads as booleans keep their text") {
        val schema = parse(
            """
            environments:
              - dev

            variables:
              ON:
                type: Boolean
                scope: environment
              NO:
                type: String
                scope: global
              API_PORT:
                type: Int
                scope: environment
            """
        )
        schema.variables.map { it.name } shouldContainExactly listOf("ON", "NO", "API_PORT")
    }

    test("a group name that YAML 1.1 reads as a boolean keeps its text") {
        val schema = parse(
            """
            environments:
              - dev

            groups:
              on:
                API_URL:
                  type: Url
                  scope: environment
            """
        )
        schema.groups.map { it.name } shouldContainExactly listOf("on")
    }

    test("an unquoted description is not coerced") {
        val schema = parse(
            """
            environments:
              - dev

            variables:
              FEATURE_STATE:
                type: String
                scope: environment
                description: no
              VERSION_HINT:
                type: String
                scope: environment
                description: 1.10
            """
        )
        schema.variables.first { it.name == "FEATURE_STATE" }.description shouldBe "no"
        schema.variables.first { it.name == "VERSION_HINT" }.description shouldBe "1.10"
    }

    test("quoted scalars are unaffected, as they always were") {
        val schema = parse(
            """
            environments:
              - "production"

            variables:
              APP_NAME:
                type: String
                scope: global
                description: "Display name"
            """
        )
        schema.environments shouldContainExactly listOf("production")
        schema.variables.first().description shouldBe "Display name"
    }
})
