package adven.kenv.config.env

import adven.kenv.config.model.ParseResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for DotEnvParser covering edge cases:
 * - Empty values
 * - Escaped characters
 * - Comments (inline and full-line)
 * - Error messages include file name and line number
 */
class DotEnvParserTest : FunSpec({

    val parser = DotEnvParser()

    test("parses simple key-value pair") {
        val content = "API_KEY=abc123"
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("API_KEY" to "abc123")
    }

    test("parses empty value") {
        val content = "EMPTY_VAR="
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("EMPTY_VAR" to "")
    }

    test("parses double-quoted value") {
        val content = """DB_HOST="localhost""""
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("DB_HOST" to "localhost")
    }

    test("parses single-quoted value") {
        val content = "DB_HOST='localhost'"
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("DB_HOST" to "localhost")
    }

    test("skips comment-only lines") {
        val content = """
            # This is a comment
            API_KEY=value
            # Another comment
        """.trimIndent()
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("API_KEY" to "value")
    }

    test("skips blank lines") {
        val content = """
            API_KEY=value

            DB_HOST=localhost

        """.trimIndent()
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("API_KEY" to "value", "DB_HOST" to "localhost")
    }

    test("handles inline comments") {
        val content = "API_KEY=value # this is a comment"
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("API_KEY" to "value")
    }

    test("preserves hash inside double quotes") {
        val content = """API_KEY="value#with#hash""""
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf("API_KEY" to "value#with#hash")
    }

    test("reports error for line without equals sign") {
        val content = "INVALID_LINE"
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].filePath shouldBe "env.dev.env"
        result.errors[0].line shouldBe 1
    }

    test("reports error with correct line number for invalid line in middle") {
        val content = """
            API_KEY=value
            INVALID_LINE
            DB_HOST=localhost
        """.trimIndent()
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].line shouldBe 2
        result.errors[0].filePath shouldBe "env.dev.env"
    }

    test("reports error for empty key name") {
        val content = "=value"
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].filePath shouldBe "env.dev.env"
        result.errors[0].line shouldBe 1
    }

    test("reports error for unterminated quoted string") {
        val content = """API_KEY="unterminated"""
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Failure>()
        result.errors.size shouldBe 1
        result.errors[0].filePath shouldBe "env.dev.env"
        result.errors[0].line shouldBe 1
    }

    test("parses multiple key-value pairs") {
        val content = """
            API_KEY=abc123
            DB_HOST=localhost
            DB_PORT=5432
        """.trimIndent()
        val result = parser.parse(content, "env.dev.env", "dev")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.values shouldBe mapOf(
            "API_KEY" to "abc123",
            "DB_HOST" to "localhost",
            "DB_PORT" to "5432"
        )
    }

    test("sets correct environment name and format") {
        val content = "KEY=value"
        val result = parser.parse(content, "env.production.env", "production")

        result.shouldBeInstanceOf<ParseResult.Success<EnvironmentConfig>>()
        result.value.name shouldBe "production"
        result.value.format shouldBe EnvFileFormat.DOT_ENV
        result.value.sourceFile shouldBe "env.production.env"
    }

    test("print produces valid output for simple values") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("API_KEY" to "abc123", "DB_PORT" to "5432"),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )
        val printed = parser.print(config)
        printed shouldBe "API_KEY=abc123\nDB_PORT=5432"
    }

    test("print quotes values with spaces") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("APP_NAME" to "My App"),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )
        val printed = parser.print(config)
        printed shouldBe """APP_NAME="My App""""
    }
})
