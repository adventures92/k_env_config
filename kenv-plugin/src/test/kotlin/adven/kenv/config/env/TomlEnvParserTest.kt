package adven.kenv.config.env

import adven.kenv.config.model.ParseResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for TomlEnvParser covering:
 * - Flat key-value parsing
 * - Nested tables rejected
 * - Error messages include file name and line number
 */
class TomlEnvParserTest : FunSpec({

    val parser = TomlEnvParser()

    test("parses flat key-value TOML") {
        val content = """
            API_KEY = "abc123"
            DB_HOST = "localhost"
            DB_PORT = "5432"
        """.trimIndent()
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf(
            "API_KEY" to "abc123",
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432"
        )
    }

    test("rejects nested tables") {
        val content = """
            API_KEY = "abc123"
            
            [database]
            host = "localhost"
            port = "5432"
        """.trimIndent()
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].message shouldContain "Nested tables are not supported"
        result.errors[0].message shouldContain "database"
        result.errors[0].filePath shouldBe "env.dev.toml"
    }

    test("rejects arrays") {
        val content = """
            HOSTS = ["host1", "host2"]
        """.trimIndent()
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].message shouldContain "Arrays are not supported"
        result.errors[0].filePath shouldBe "env.dev.toml"
    }

    test("handles empty TOML file") {
        val content = ""
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe emptyMap()
    }

    test("handles TOML with only comments") {
        val content = """
            # This is a comment
            # Another comment
        """.trimIndent()
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe emptyMap()
    }

    test("treats numeric TOML values as strings") {
        val content = """
            PORT = "8080"
            RATIO = "3.14"
            ENABLED = "true"
        """.trimIndent()
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values["PORT"] shouldBe "8080"
        result.value.values["RATIO"] shouldBe "3.14"
        result.value.values["ENABLED"] shouldBe "true"
    }

    test("sets correct environment name and format") {
        val content = """KEY = "value""""
        val result = parser.parse(content, "env.production.toml", "production")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.name shouldBe "production"
        result.value.format shouldBe EnvFileFormat.TOML
        result.value.sourceFile shouldBe "env.production.toml"
    }

    test("print produces valid TOML output") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("API_KEY" to "abc123", "DB_PORT" to "5432"),
            format = EnvFileFormat.TOML,
            sourceFile = "env.dev.toml"
        )
        val printed = parser.print(config)

        // Parse it back to verify validity
        val result = parser.parse(printed, "env.dev.toml", "dev")
        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe config.values
    }

    test("print escapes special characters in values") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("MSG" to "hello\"world"),
            format = EnvFileFormat.TOML,
            sourceFile = "env.dev.toml"
        )
        val printed = parser.print(config)

        // Parse it back to verify round-trip
        val result = parser.parse(printed, "env.dev.toml", "dev")
        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values["MSG"] shouldBe "hello\"world"
    }

    test("reports error with file path for invalid TOML syntax") {
        val content = "= no_key"
        val result = parser.parse(content, "env.dev.toml", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors[0].filePath shouldBe "env.dev.toml"
    }
})
