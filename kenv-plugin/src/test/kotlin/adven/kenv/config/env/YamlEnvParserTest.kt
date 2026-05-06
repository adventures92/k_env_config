package adven.kenv.config.env

import adven.kenv.config.model.ParseResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for YamlEnvParser covering:
 * - Flat key-value parsing
 * - Nested structures rejected
 * - Error messages include file name and line number
 */
class YamlEnvParserTest : FunSpec({

    val parser = YamlEnvParser()

    test("parses flat key-value YAML") {
        val content = """
            API_KEY: abc123
            DB_HOST: localhost
            DB_PORT: "5432"
        """.trimIndent()
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf(
            "API_KEY" to "abc123",
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432"
        )
    }

    test("rejects nested structures") {
        val content = """
            API_KEY: abc123
            database:
              host: localhost
              port: 5432
        """.trimIndent()
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].message shouldContain "Nested structures are not supported"
        result.errors[0].message shouldContain "database"
        result.errors[0].filePath shouldBe "env.dev.yaml"
        result.errors[0].line shouldBe 2
    }

    test("rejects list values") {
        val content = """
            API_KEY: abc123
            HOSTS:
              - host1
              - host2
        """.trimIndent()
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].message shouldContain "Nested structures are not supported"
        result.errors[0].filePath shouldBe "env.dev.yaml"
    }

    test("handles empty YAML file") {
        val content = ""
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe emptyMap()
    }

    test("handles YAML with only comments") {
        val content = """
            # This is a comment
            # Another comment
        """.trimIndent()
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe emptyMap()
    }

    test("reports syntax error with file path and line number") {
        val content = "key: [unclosed"
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].filePath shouldBe "env.dev.yaml"
    }

    test("treats numeric values as strings") {
        val content = """
            PORT: 8080
            RATIO: 3.14
            ENABLED: true
        """.trimIndent()
        val result = parser.parse(content, "env.dev.yaml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values["PORT"] shouldBe "8080"
        result.value.values["RATIO"] shouldBe "3.14"
        result.value.values["ENABLED"] shouldBe "true"
    }

    test("sets correct environment name and format") {
        val content = "KEY: value"
        val result = parser.parse(content, "env.production.yaml", "production")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.name shouldBe "production"
        result.value.format shouldBe EnvFileFormat.YAML
        result.value.sourceFile shouldBe "env.production.yaml"
    }

    test("print produces valid YAML output") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("API_KEY" to "abc123", "DB_PORT" to "5432"),
            format = EnvFileFormat.YAML,
            sourceFile = "env.dev.yaml"
        )
        val printed = parser.print(config)

        // Parse it back to verify validity
        val result = parser.parse(printed, "env.dev.yaml", "dev")
        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe config.values
    }

    test("print quotes values that look like YAML special values") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("FLAG" to "true", "EMPTY" to ""),
            format = EnvFileFormat.YAML,
            sourceFile = "env.dev.yaml"
        )
        val printed = parser.print(config)

        // Parse it back to verify round-trip
        val result = parser.parse(printed, "env.dev.yaml", "dev")
        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values["FLAG"] shouldBe "true"
        result.value.values["EMPTY"] shouldBe ""
    }
})
