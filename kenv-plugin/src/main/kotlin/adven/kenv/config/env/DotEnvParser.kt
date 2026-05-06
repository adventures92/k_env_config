package adven.kenv.config.env

import adven.kenv.config.model.ParseError
import adven.kenv.config.model.ParseResult

/**
 * Parser for .env format files.
 *
 * Supports:
 * - KEY=value pairs, one per line
 * - Quoted string values (single and double quotes)
 * - Inline comments prefixed with # (only outside quotes)
 * - Blank lines and comment-only lines (skipped)
 */
class DotEnvParser : EnvFileParser {

    override fun parse(content: String, filePath: String, environmentName: String): ParseResult<EnvironmentConfig> {
        val values = mutableMapOf<String, String>()
        val errors = mutableListOf<ParseError>()

        val lines = content.lines()
        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1
            val trimmed = line.trim()

            // Skip blank lines and comment-only lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }

            // Find the first '=' to split key and value
            val equalsIndex = trimmed.indexOf('=')
            if (equalsIndex == -1) {
                errors.add(
                    ParseError(
                        message = "Expected KEY=value format",
                        filePath = filePath,
                        line = lineNumber,
                        column = null
                    )
                )
                continue
            }

            val key = trimmed.substring(0, equalsIndex).trim()
            if (key.isEmpty()) {
                errors.add(
                    ParseError(
                        message = "Empty key name",
                        filePath = filePath,
                        line = lineNumber,
                        column = 1
                    )
                )
                continue
            }

            if (!isValidKey(key)) {
                errors.add(
                    ParseError(
                        message = "Invalid key name '$key': keys must contain only alphanumeric characters and underscores",
                        filePath = filePath,
                        line = lineNumber,
                        column = 1
                    )
                )
                continue
            }

            val rawValue = trimmed.substring(equalsIndex + 1)
            val parsedValue = parseValue(rawValue, filePath, lineNumber)

            if (parsedValue == null) {
                errors.add(
                    ParseError(
                        message = "Unterminated quoted string",
                        filePath = filePath,
                        line = lineNumber,
                        column = equalsIndex + 2
                    )
                )
                continue
            }

            values[key] = parsedValue
        }

        return if (errors.isNotEmpty()) {
            ParseResult.Failure(errors)
        } else {
            ParseResult.Success(
                EnvironmentConfig(
                    name = environmentName,
                    values = values,
                    format = EnvFileFormat.DOT_ENV,
                    sourceFile = filePath
                )
            )
        }
    }

    override fun print(config: EnvironmentConfig): String {
        return config.values.entries.joinToString("\n") { (key, value) ->
            "$key=${quoteIfNeeded(value)}"
        }
    }

    private fun isValidKey(key: String): Boolean {
        return key.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }

    /**
     * Parses the value portion of a KEY=value line.
     * Handles quoted values and inline comments.
     * Returns null if a quoted string is unterminated.
     */
    private fun parseValue(rawValue: String, filePath: String, lineNumber: Int): String? {
        val trimmed = rawValue.trim()

        if (trimmed.isEmpty()) {
            return ""
        }

        // Check for quoted values
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            val quoteChar = trimmed[0]
            val endIndex = trimmed.indexOf(quoteChar, 1)
            if (endIndex == -1) {
                return null // Unterminated quote
            }
            return trimmed.substring(1, endIndex)
        }

        // Unquoted value: strip inline comments
        val commentIndex = trimmed.indexOf('#')
        return if (commentIndex != -1) {
            trimmed.substring(0, commentIndex).trim()
        } else {
            trimmed
        }
    }

    /**
     * Quotes a value if it contains spaces, #, or special characters.
     */
    private fun quoteIfNeeded(value: String): String {
        if (value.isEmpty()) {
            return ""
        }
        val needsQuoting = value.contains(' ') ||
            value.contains('#') ||
            value.contains('\'') ||
            value.contains('"') ||
            value.contains('\t') ||
            value.contains('=')

        return if (needsQuoting) {
            // Use double quotes, escape any internal double quotes
            "\"${value.replace("\"", "\\\"")}\""
        } else {
            value
        }
    }
}
