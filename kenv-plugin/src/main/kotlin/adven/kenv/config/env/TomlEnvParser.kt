package adven.kenv.config.env

import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import adven.kenv.config.model.ParseError
import adven.kenv.config.model.ParseResult

/**
 * Parser for TOML format environment files.
 *
 * Supports flat key-value TOML structures where all values are treated as strings.
 * Nested tables and arrays of tables are not supported for environment files.
 */
class TomlEnvParser : EnvFileParser {

    override fun parse(content: String, filePath: String, environmentName: String): ParseResult<EnvironmentConfig> {
        if (content.isBlank()) {
            return ParseResult.Success(
                EnvironmentConfig(
                    name = environmentName,
                    values = emptyMap(),
                    format = EnvFileFormat.TOML,
                    sourceFile = filePath
                )
            )
        }

        val toml: Toml = try {
            Toml().read(content)
        } catch (e: IllegalStateException) {
            val line = extractLineNumber(e.message)
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = e.message ?: "Invalid TOML syntax",
                        filePath = filePath,
                        line = line,
                        column = null
                    )
                )
            )
        } catch (e: Exception) {
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = e.message ?: "Invalid TOML syntax",
                        filePath = filePath,
                        line = 1,
                        column = null
                    )
                )
            )
        }

        val values = mutableMapOf<String, String>()
        val errors = mutableListOf<ParseError>()
        val lines = content.lines()

        for ((key, value) in toml.toMap()) {
            val lineNumber = findKeyLine(lines, key)

            when (value) {
                is Map<*, *> -> {
                    errors.add(
                        ParseError(
                            message = "Nested tables are not supported in environment files. Key '$key' must have a scalar value.",
                            filePath = filePath,
                            line = lineNumber,
                            column = null
                        )
                    )
                }
                is List<*> -> {
                    errors.add(
                        ParseError(
                            message = "Arrays are not supported in environment files. Key '$key' must have a scalar value.",
                            filePath = filePath,
                            line = lineNumber,
                            column = null
                        )
                    )
                }
                else -> {
                    values[key] = value.toString()
                }
            }
        }

        return if (errors.isNotEmpty()) {
            ParseResult.Failure(errors)
        } else {
            ParseResult.Success(
                EnvironmentConfig(
                    name = environmentName,
                    values = values,
                    format = EnvFileFormat.TOML,
                    sourceFile = filePath
                )
            )
        }
    }

    override fun print(config: EnvironmentConfig): String {
        if (config.values.isEmpty()) {
            return ""
        }
        return config.values.entries.joinToString("\n") { (key, value) ->
            "$key = \"${escapeTomlString(value)}\""
        }
    }

    /**
     * Extracts a line number from a toml4j exception message.
     * toml4j error messages typically contain line information.
     */
    private fun extractLineNumber(message: String?): Int {
        if (message == null) return 1
        // toml4j messages often contain "line X" pattern
        val linePattern = Regex("""line\s+(\d+)""", RegexOption.IGNORE_CASE)
        val match = linePattern.find(message)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    /**
     * Finds the 1-based line number where a key is defined.
     */
    private fun findKeyLine(lines: List<String>, key: String): Int {
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("$key") && trimmed.contains('=')) {
                return index + 1
            }
        }
        return 1
    }

    /**
     * Escapes special characters in a TOML string value.
     */
    private fun escapeTomlString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
