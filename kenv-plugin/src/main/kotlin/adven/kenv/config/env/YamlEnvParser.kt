package adven.kenv.config.env

import adven.kenv.config.model.ParseError
import adven.kenv.config.model.ParseResult
import adven.kenv.config.yaml.StringOnlyYaml
import org.yaml.snakeyaml.error.MarkedYAMLException

/**
 * Parser for YAML format environment files.
 *
 * Supports flat key-value YAML structures where all values are treated as strings.
 * Nested structures are not supported for environment files.
 *
 * Implicit type resolution is disabled (see [StringOnlyYaml]), so unquoted scalars
 * keep their literal text instead of being coerced by YAML's implicit typing rules.
 */
class YamlEnvParser : EnvFileParser {

    override fun parse(content: String, filePath: String, environmentName: String): ParseResult<EnvironmentConfig> {
        val yaml = StringOnlyYaml.createLoader()

        val parsed: Any? = try {
            yaml.load<Any>(content)
        } catch (e: MarkedYAMLException) {
            val line = (e.problemMark?.line ?: 0) + 1
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = e.problem ?: "Invalid YAML syntax",
                        filePath = filePath,
                        line = line,
                        column = e.problemMark?.column?.plus(1)
                    )
                )
            )
        } catch (e: Exception) {
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = e.message ?: "Invalid YAML syntax",
                        filePath = filePath,
                        line = 1,
                        column = null
                    )
                )
            )
        }

        // Handle empty file
        if (parsed == null) {
            return ParseResult.Success(
                EnvironmentConfig(
                    name = environmentName,
                    values = emptyMap(),
                    format = EnvFileFormat.YAML,
                    sourceFile = filePath
                )
            )
        }

        // Expect a map at the top level
        if (parsed !is Map<*, *>) {
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = "Expected a YAML mapping (key-value pairs) at the top level",
                        filePath = filePath,
                        line = 1,
                        column = null
                    )
                )
            )
        }

        val values = mutableMapOf<String, String>()
        val errors = mutableListOf<ParseError>()

        // Track line numbers by re-parsing line by line for error reporting
        val lines = content.lines()

        for ((key, value) in parsed) {
            val keyStr = key.toString()
            val lineNumber = findKeyLine(lines, keyStr)

            if (value is Map<*, *> || value is List<*>) {
                errors.add(
                    ParseError(
                        message = "Nested structures are not supported in environment files. Key '$keyStr' must have a scalar value.",
                        filePath = filePath,
                        line = lineNumber,
                        column = null
                    )
                )
                continue
            }

            values[keyStr] = value?.toString() ?: ""
        }

        return if (errors.isNotEmpty()) {
            ParseResult.Failure(errors)
        } else {
            ParseResult.Success(
                EnvironmentConfig(
                    name = environmentName,
                    values = values,
                    format = EnvFileFormat.YAML,
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
            "$key: ${yamlQuoteIfNeeded(value)}"
        }
    }

    /**
     * Finds the 1-based line number where a key is defined.
     */
    private fun findKeyLine(lines: List<String>, key: String): Int {
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("$key:") || trimmed.startsWith("\"$key\":") || trimmed.startsWith("'$key':")) {
                return index + 1
            }
        }
        return 1
    }

    /**
     * Quotes a YAML value if it contains characters that need quoting.
     */
    private fun yamlQuoteIfNeeded(value: String): String {
        if (value.isEmpty()) {
            return "\"\""
        }

        // Values that need quoting: contain special YAML characters, look like numbers/booleans,
        // start/end with whitespace, or contain colons/hashes
        val needsQuoting = value.contains(':') ||
            value.contains('#') ||
            value.contains('{') ||
            value.contains('}') ||
            value.contains('[') ||
            value.contains(']') ||
            value.contains(',') ||
            value.contains('&') ||
            value.contains('*') ||
            value.contains('!') ||
            value.contains('|') ||
            value.contains('>') ||
            value.contains('\'') ||
            value.contains('"') ||
            value.contains('%') ||
            value.contains('@') ||
            value.contains('`') ||
            value.startsWith(' ') ||
            value.endsWith(' ') ||
            value.lowercase() in YAML_BOOLEAN_LITERALS ||
            value.equals("null", ignoreCase = true) ||
            value.equals("~") ||
            value.toDoubleOrNull() != null ||
            value.toLongOrNull() != null

        return if (needsQuoting) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            value
        }
    }

    private companion object {
        /**
         * Tokens YAML 1.1 resolves to booleans. Values matching any of these (ignoring case)
         * are quoted on output so they survive a round-trip through other YAML tooling.
         */
        val YAML_BOOLEAN_LITERALS = setOf("true", "false", "yes", "no", "on", "off", "y", "n")
    }
}
