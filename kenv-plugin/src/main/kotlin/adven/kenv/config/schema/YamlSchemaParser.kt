package adven.kenv.config.schema

import adven.kenv.config.model.ParseError
import adven.kenv.config.model.ParseResult
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.MarkedYAMLException

/**
 * YAML-based implementation of [SchemaParser] using SnakeYAML.
 */
class YamlSchemaParser : SchemaParser {

    private val yaml = Yaml()

    override fun parse(content: String, filePath: String): ParseResult<Schema> {
        val root: Map<String, Any?>
        try {
            val parsed = yaml.load<Any>(content)
            if (parsed == null) {
                return ParseResult.Failure(
                    listOf(
                        ParseError(
                            message = "Schema file is empty",
                            filePath = filePath,
                            line = 1,
                            column = null
                        )
                    )
                )
            }
            if (parsed !is Map<*, *>) {
                return ParseResult.Failure(
                    listOf(
                        ParseError(
                            message = "Schema must be a YAML mapping at the top level",
                            filePath = filePath,
                            line = 1,
                            column = null
                        )
                    )
                )
            }
            @Suppress("UNCHECKED_CAST")
            root = parsed as Map<String, Any?>
        } catch (e: MarkedYAMLException) {
            val line = (e.problemMark?.line ?: 0) + 1
            val column = (e.problemMark?.column ?: 0) + 1
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = "Invalid YAML: ${e.problem ?: e.message}",
                        filePath = filePath,
                        line = line,
                        column = column
                    )
                )
            )
        } catch (e: Exception) {
            return ParseResult.Failure(
                listOf(
                    ParseError(
                        message = "Invalid YAML: ${e.message}",
                        filePath = filePath,
                        line = 1,
                        column = null
                    )
                )
            )
        }

        val errors = mutableListOf<ParseError>()

        // Parse environments (required)
        val environments = parseEnvironments(root, filePath, errors)
        if (errors.isNotEmpty()) {
            return ParseResult.Failure(errors)
        }

        // Parse top-level variables
        val variables = parseVariables(root["variables"], filePath, errors)

        // Parse groups
        val groups = parseGroups(root["groups"], filePath, errors)

        if (errors.isNotEmpty()) {
            return ParseResult.Failure(errors)
        }

        return ParseResult.Success(
            Schema(
                environments = environments,
                variables = variables,
                groups = groups
            )
        )
    }

    override fun print(schema: Schema): String {
        val sb = StringBuilder()

        // Print environments
        sb.appendLine("environments:")
        for (env in schema.environments) {
            sb.appendLine("  - $env")
        }

        // Print top-level variables
        if (schema.variables.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("variables:")
            for (variable in schema.variables) {
                printVariable(sb, variable, indent = "  ")
            }
        }

        // Print groups
        if (schema.groups.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("groups:")
            for (group in schema.groups) {
                sb.appendLine("  ${group.name}:")
                for (variable in group.variables) {
                    printVariable(sb, variable, indent = "    ")
                }
            }
        }

        return sb.toString()
    }

    private fun printVariable(sb: StringBuilder, variable: SchemaVariable, indent: String) {
        sb.appendLine("$indent${variable.name}:")
        sb.appendLine("$indent  type: ${typeToString(variable.type)}")
        sb.appendLine("$indent  scope: ${scopeToString(variable.scope)}")
        if (variable.description != null) {
            sb.appendLine("$indent  description: \"${variable.description}\"")
        }
    }

    private fun typeToString(type: SchemaType): String = when (type) {
        SchemaType.STRING -> "String"
        SchemaType.INT -> "Int"
        SchemaType.LONG -> "Long"
        SchemaType.DOUBLE -> "Double"
        SchemaType.FLOAT -> "Float"
        SchemaType.BOOLEAN -> "Boolean"
        SchemaType.URL -> "Url"
    }

    private fun scopeToString(scope: VariableScope): String = when (scope) {
        VariableScope.GLOBAL -> "global"
        VariableScope.ENVIRONMENT -> "environment"
    }

    private fun parseEnvironments(
        root: Map<String, Any?>,
        filePath: String,
        errors: MutableList<ParseError>
    ): List<String> {
        val envValue = root["environments"]
        if (envValue == null) {
            errors.add(
                ParseError(
                    message = "Missing required 'environments' list",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return emptyList()
        }
        if (envValue !is List<*>) {
            errors.add(
                ParseError(
                    message = "'environments' must be a list",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return emptyList()
        }
        if (envValue.isEmpty()) {
            errors.add(
                ParseError(
                    message = "'environments' list must not be empty",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return emptyList()
        }
        return envValue.mapNotNull { it?.toString() }
    }

    private fun parseVariables(
        variablesValue: Any?,
        filePath: String,
        errors: MutableList<ParseError>
    ): List<SchemaVariable> {
        if (variablesValue == null) return emptyList()
        if (variablesValue !is Map<*, *>) {
            errors.add(
                ParseError(
                    message = "'variables' must be a mapping",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return emptyList()
        }

        val variables = mutableListOf<SchemaVariable>()
        for ((key, value) in variablesValue) {
            val name = key?.toString() ?: continue
            val variable = parseVariable(name, value, filePath, errors)
            if (variable != null) {
                variables.add(variable)
            }
        }
        return variables
    }

    private fun parseVariable(
        name: String,
        value: Any?,
        filePath: String,
        errors: MutableList<ParseError>
    ): SchemaVariable? {
        if (value !is Map<*, *>) {
            errors.add(
                ParseError(
                    message = "Variable '$name' must be a mapping with at least a 'type' field",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return null
        }

        val typeStr = value["type"]?.toString()
        if (typeStr == null) {
            errors.add(
                ParseError(
                    message = "Variable '$name' is missing required 'type' field",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return null
        }

        val type = parseType(typeStr)
        if (type == null) {
            errors.add(
                ParseError(
                    message = "Unknown type '$typeStr' for variable '$name'. Valid types: String, Int, Long, Double, Float, Boolean, Url",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return null
        }

        val scopeStr = value["scope"]?.toString()
        val scope = if (scopeStr != null) {
            parseScope(scopeStr)
                ?: run {
                    errors.add(
                        ParseError(
                            message = "Unknown scope '$scopeStr' for variable '$name'. Valid scopes: global, environment",
                            filePath = filePath,
                            line = 1,
                            column = null
                        )
                    )
                    return null
                }
        } else {
            VariableScope.ENVIRONMENT
        }

        // Reject default field — not supported in v2
        if (value.containsKey("default")) {
            errors.add(
                ParseError(
                    message = "Variable '$name' contains a 'default' field which is not supported in v2. All values must be provided in environment files.",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return null
        }

        val description = value["description"]?.toString()

        return SchemaVariable(
            name = name,
            type = type,
            scope = scope,
            description = description
        )
    }

    private fun parseGroups(
        groupsValue: Any?,
        filePath: String,
        errors: MutableList<ParseError>
    ): List<SchemaGroup> {
        if (groupsValue == null) return emptyList()
        if (groupsValue !is Map<*, *>) {
            errors.add(
                ParseError(
                    message = "'groups' must be a mapping",
                    filePath = filePath,
                    line = 1,
                    column = null
                )
            )
            return emptyList()
        }

        val groups = mutableListOf<SchemaGroup>()
        for ((key, value) in groupsValue) {
            val groupName = key?.toString() ?: continue
            if (value !is Map<*, *>) {
                errors.add(
                    ParseError(
                        message = "Group '$groupName' must be a mapping of variables",
                        filePath = filePath,
                        line = 1,
                        column = null
                    )
                )
                continue
            }

            val groupVariables = mutableListOf<SchemaVariable>()
            for ((varKey, varValue) in value) {
                val varName = varKey?.toString() ?: continue
                val variable = parseVariable(varName, varValue, filePath, errors)
                if (variable != null) {
                    groupVariables.add(variable)
                }
            }
            groups.add(SchemaGroup(name = groupName, variables = groupVariables))
        }
        return groups
    }

    private fun parseType(typeStr: String): SchemaType? = when (typeStr.lowercase()) {
        "string" -> SchemaType.STRING
        "int" -> SchemaType.INT
        "long" -> SchemaType.LONG
        "double" -> SchemaType.DOUBLE
        "float" -> SchemaType.FLOAT
        "boolean" -> SchemaType.BOOLEAN
        "url" -> SchemaType.URL
        else -> null
    }

    private fun parseScope(scopeStr: String): VariableScope? = when (scopeStr.lowercase()) {
        "global" -> VariableScope.GLOBAL
        "environment" -> VariableScope.ENVIRONMENT
        else -> null
    }
}
