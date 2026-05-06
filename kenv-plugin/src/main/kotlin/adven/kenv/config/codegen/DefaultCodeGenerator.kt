package adven.kenv.config.codegen

import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.Schema
import adven.kenv.config.schema.SchemaGroup
import adven.kenv.config.schema.SchemaType
import adven.kenv.config.schema.SchemaVariable
import adven.kenv.config.schema.VariableScope

/**
 * Default implementation of [CodeGenerator] that produces Kotlin object classes
 * using only Kotlin standard library types.
 *
 * Supports two generation modes:
 * - Multi-environment (activeEnvironment is null): generates nested objects per environment
 * - Active environment (activeEnvironment is set): generates a single flat object
 *
 * @param packageName Required package declaration for the generated file
 */
class DefaultCodeGenerator(
    private val packageName: String
) : CodeGenerator {

    override fun generate(
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        activeEnvironment: String?,
        className: String
    ): String {
        val builder = StringBuilder()

        builder.appendLine("package $packageName")
        builder.appendLine()

        if (activeEnvironment != null) {
            generateActiveEnvironment(builder, schema, configs, globalConfig, activeEnvironment, className)
        } else {
            generateMultiEnvironment(builder, schema, configs, globalConfig, className)
        }

        return builder.toString()
    }

    /**
     * Generates a single flat object containing global values and the active environment's values.
     */
    private fun generateActiveEnvironment(
        builder: StringBuilder,
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        activeEnvironment: String,
        className: String
    ) {
        val envConfig = configs[activeEnvironment]
        val envValues = envConfig?.values ?: emptyMap()

        builder.appendLine("object $className {")

        // Global-scoped top-level variables
        val globalVars = schema.variables.filter { it.scope == VariableScope.GLOBAL }
        for (variable in globalVars) {
            val value = resolveGlobalValue(variable, globalConfig)
            if (value != null) {
                emitKDoc(builder, variable.description, "    ")
                builder.appendLine("    val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
            }
        }

        // Environment-scoped top-level variables
        val envVars = schema.variables.filter { it.scope == VariableScope.ENVIRONMENT }
        for (variable in envVars) {
            val value = resolveEnvironmentValue(variable, envValues)
            if (value != null) {
                emitKDoc(builder, variable.description, "    ")
                builder.appendLine("    val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
            }
        }

        // Groups
        for (group in schema.groups) {
            builder.appendLine()
            generateGroupObject(builder, group, envValues, globalConfig, indent = 1)
        }

        builder.appendLine("}")
    }

    /**
     * Generates nested objects per environment with global values at the top level,
     * plus a runtime-selectable flat API via setActiveEnvironment().
     */
    private fun generateMultiEnvironment(
        builder: StringBuilder,
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        className: String
    ) {
        builder.appendLine("object $className {")
        builder.appendLine()

        // --- Runtime environment selection API ---
        builder.appendLine("    private var _activeEnvironment: String? = null")
        builder.appendLine()
        builder.appendLine("    /**")
        builder.appendLine("     * Sets the active environment for flat property access.")
        builder.appendLine("     * After calling this, environment-scoped properties can be accessed")
        builder.appendLine("     * directly (e.g., `$className.Server.API_URL`) without specifying the environment.")
        builder.appendLine("     *")
        builder.appendLine("     * @param environment One of: ${schema.environments.joinToString(", ") { "\"$it\"" }}")
        builder.appendLine("     * @throws IllegalArgumentException if the environment name is not valid")
        builder.appendLine("     */")
        builder.appendLine("    fun setActiveEnvironment(environment: String) {")
        builder.appendLine("        require(environment in listOf(${schema.environments.joinToString(", ") { "\"$it\"" }})) {")
        builder.appendLine("            \"Invalid environment '\${'$'}environment'. Valid environments: ${schema.environments.joinToString(", ")}\"")
        builder.appendLine("        }")
        builder.appendLine("        _activeEnvironment = environment")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    /** Returns the currently active environment name, or null if not set. */")
        builder.appendLine("    val activeEnvironment: String? get() = _activeEnvironment")
        builder.appendLine()

        // --- Global-scoped top-level variables (always accessible) ---
        val globalVars = schema.variables.filter { it.scope == VariableScope.GLOBAL }
        for (variable in globalVars) {
            val value = resolveGlobalValue(variable, globalConfig)
            if (value != null) {
                emitKDoc(builder, variable.description, "    ")
                builder.appendLine("    val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
            }
        }

        // --- Environment-scoped top-level variables (flat, runtime-resolved) ---
        val envVars = schema.variables.filter { it.scope == VariableScope.ENVIRONMENT }
        if (envVars.isNotEmpty()) {
            builder.appendLine()
            for (variable in envVars) {
                emitKDoc(builder, variable.description, "    ")
                builder.appendLine("    val ${variable.name}: ${variable.type.toKotlinType()}")
                builder.appendLine("        get() = when (_activeEnvironment) {")
                for (envName in schema.environments) {
                    val envConfig = configs[envName]
                    val value = envConfig?.values?.get(variable.name)
                    if (value != null) {
                        builder.appendLine("            \"$envName\" -> ${formatValue(value, variable.type)}")
                    }
                }
                builder.appendLine("            else -> throw IllegalStateException(")
                builder.appendLine("                \"Active environment not set. Call $className.setActiveEnvironment() first.\"")
                builder.appendLine("            )")
                builder.appendLine("        }")
            }
        }

        // --- Groups with flat runtime-resolved access ---
        for (group in schema.groups) {
            builder.appendLine()
            generateRuntimeGroupObject(builder, group, configs, globalConfig, schema.environments, className, indent = 1)
        }

        // --- Per-environment nested objects (for direct access when needed) ---
        builder.appendLine()
        builder.appendLine("    // --- Per-environment objects (direct access without setActiveEnvironment) ---")
        for (envName in schema.environments) {
            val envConfig = configs[envName]
            val envValues = envConfig?.values ?: emptyMap()
            val objectName = capitalizeEnvironmentName(envName)

            builder.appendLine()
            builder.appendLine("    object $objectName {")

            // Environment-scoped top-level variables
            for (variable in envVars) {
                val value = resolveEnvironmentValue(variable, envValues)
                if (value != null) {
                    emitKDoc(builder, variable.description, "        ")
                    builder.appendLine("        val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
                }
            }

            // Groups within environment
            for (group in schema.groups) {
                val hasEnvVars = group.variables.any { it.scope == VariableScope.ENVIRONMENT }
                val hasGlobalVars = group.variables.any { it.scope == VariableScope.GLOBAL }
                if (hasEnvVars || hasGlobalVars) {
                    builder.appendLine()
                    generateGroupObject(builder, group, envValues, globalConfig, indent = 2)
                }
            }

            builder.appendLine("    }")
        }

        builder.appendLine("}")
    }

    /**
     * Generates a group object with runtime-resolved environment-scoped properties.
     */
    private fun generateRuntimeGroupObject(
        builder: StringBuilder,
        group: SchemaGroup,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        environments: List<String>,
        className: String,
        indent: Int
    ) {
        val indentStr = "    ".repeat(indent)
        val innerIndent = "    ".repeat(indent + 1)
        val objectName = capitalizeEnvironmentName(group.name)

        builder.appendLine("${indentStr}object $objectName {")

        for (variable in group.variables) {
            when (variable.scope) {
                VariableScope.GLOBAL -> {
                    val value = resolveGlobalValue(variable, globalConfig)
                    if (value != null) {
                        emitKDoc(builder, variable.description, innerIndent)
                        builder.appendLine("${innerIndent}val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
                    }
                }
                VariableScope.ENVIRONMENT -> {
                    emitKDoc(builder, variable.description, innerIndent)
                    builder.appendLine("${innerIndent}val ${variable.name}: ${variable.type.toKotlinType()}")
                    builder.appendLine("${innerIndent}    get() = when (_activeEnvironment) {")
                    for (envName in environments) {
                        val envConfig = configs[envName]
                        val value = envConfig?.values?.get(variable.name)
                        if (value != null) {
                            builder.appendLine("${innerIndent}        \"$envName\" -> ${formatValue(value, variable.type)}")
                        }
                    }
                    builder.appendLine("${innerIndent}        else -> throw IllegalStateException(")
                    builder.appendLine("${innerIndent}            \"Active environment not set. Call $className.setActiveEnvironment() first.\"")
                    builder.appendLine("${innerIndent}        )")
                    builder.appendLine("${innerIndent}    }")
                }
            }
        }

        builder.appendLine("${indentStr}}")
    }

    /**
     * Generates a nested object for a schema group.
     */
    private fun generateGroupObject(
        builder: StringBuilder,
        group: SchemaGroup,
        envValues: Map<String, String>,
        globalConfig: GlobalConfig?,
        indent: Int
    ) {
        val indentStr = "    ".repeat(indent)
        val innerIndent = "    ".repeat(indent + 1)
        val objectName = capitalizeEnvironmentName(group.name)

        builder.appendLine("${indentStr}object $objectName {")

        for (variable in group.variables) {
            val value = when (variable.scope) {
                VariableScope.GLOBAL -> resolveGlobalValue(variable, globalConfig)
                VariableScope.ENVIRONMENT -> resolveEnvironmentValue(variable, envValues)
            }
            if (value != null) {
                emitKDoc(builder, variable.description, innerIndent)
                builder.appendLine("${innerIndent}val ${variable.name}: ${variable.type.toKotlinType()} = ${formatValue(value, variable.type)}")
            }
        }

        builder.appendLine("${indentStr}}")
    }

    /**
     * Emits a KDoc comment line before a property declaration when a description is present.
     */
    private fun emitKDoc(builder: StringBuilder, description: String?, indent: String) {
        if (description != null) {
            val escaped = escapeKDoc(description)
            builder.appendLine("$indent/** $escaped */")
        }
    }

    /**
     * Escapes special KDoc characters in description text.
     */
    private fun escapeKDoc(text: String): String {
        return text
            .replace("*/", "&#42;/")
            .replace("@", "&#64;")
    }

    /**
     * Resolves the value for a global-scoped variable.
     */
    private fun resolveGlobalValue(variable: SchemaVariable, globalConfig: GlobalConfig?): String? {
        return globalConfig?.values?.get(variable.name)
    }

    /**
     * Resolves the value for an environment-scoped variable.
     */
    private fun resolveEnvironmentValue(variable: SchemaVariable, envValues: Map<String, String>): String? {
        return envValues[variable.name]
    }

    /**
     * Formats a raw string value as a Kotlin literal based on the schema type.
     */
    private fun formatValue(value: String, type: SchemaType): String {
        return when (type) {
            SchemaType.STRING, SchemaType.URL -> "\"${escapeString(value)}\""
            SchemaType.INT -> value.toIntOrNull()?.toString() ?: value
            SchemaType.LONG -> "${value.toLongOrNull() ?: value}L"
            SchemaType.DOUBLE -> {
                val d = value.toDoubleOrNull()
                if (d != null) {
                    // Ensure there's a decimal point
                    val str = d.toString()
                    if ('.' in str) str else "$str.0"
                } else {
                    value
                }
            }
            SchemaType.FLOAT -> "${value.toFloatOrNull() ?: value}f"
            SchemaType.BOOLEAN -> value.lowercase()
        }
    }

    /**
     * Escapes special characters in a string for use in a Kotlin string literal.
     */
    private fun escapeString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("$", "\\$")
    }

    /**
     * Capitalizes an environment name for use as a Kotlin object name.
     * e.g., "dev" → "Dev", "production" → "Production"
     */
    private fun capitalizeEnvironmentName(name: String): String {
        return name.replaceFirstChar { it.uppercase() }
    }
}
