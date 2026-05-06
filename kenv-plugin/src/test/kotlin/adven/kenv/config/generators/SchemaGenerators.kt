package adven.kenv.config.generators

import adven.kenv.config.schema.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.arbitrary.map

/**
 * Custom Kotest Arb generators for Schema model objects.
 */
object SchemaGenerators {

    /**
     * YAML boolean-like words that must be avoided as variable names because YAML parsers
     * interpret them as boolean values (e.g., ON -> true, OFF -> false).
     */
    private val YAML_RESERVED_WORDS = setOf(
        "ON", "OFF", "YES", "NO", "TRUE", "FALSE",
        "Y", "N"
    )

    /**
     * Generates a valid variable name: uppercase letters, digits, underscores, starting with a letter.
     * Avoids YAML-reserved boolean words (ON, OFF, YES, NO, TRUE, FALSE, Y, N).
     */
    fun arbVariableName(): Arb<String> = arbitrary {
        var name: String
        do {
            val firstChar = ('A'..'Z').random(it.random)
            val length = it.random.nextInt(1, 10)
            val restChars = (1..length).map {
                val chars = ('A'..'Z') + ('0'..'9') + listOf('_')
                chars.random()
            }.joinToString("")
            name = "$firstChar$restChars"
        } while (name in YAML_RESERVED_WORDS)
        name
    }

    /**
     * Generates a simple lowercase environment name (letters only).
     */
    fun arbEnvironmentName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a simple lowercase group name (letters only).
     */
    fun arbGroupName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a SchemaType.
     */
    fun arbSchemaType(): Arb<SchemaType> = Arb.enum<SchemaType>()

    /**
     * Generates a VariableScope.
     */
    fun arbVariableScope(): Arb<VariableScope> = Arb.enum<VariableScope>()

    /**
     * Generates a simple alphanumeric string with spaces (no special YAML chars).
     */
    private fun arbSimpleAlphanumericString(minLen: Int, maxLen: Int): Arb<String> = arbitrary {
        val length = it.random.nextInt(minLen, maxLen + 1)
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ')
        (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Generates a description string that sometimes includes KDoc-special characters
     * (star-slash, at-sign) for testing KDoc escaping in Property 7.
     */
    fun arbDescription(): Arb<String> = arbitrary {
        val baseText = arbSimpleAlphanumericString(5, 20).bind()
        val includeSpecialChars = Arb.boolean().bind()
        if (includeSpecialChars) {
            val specialChars = listOf("*/", "@", "*/end", "@param", "some */ text", "email@host")
            val special = specialChars.random(it.random)
            val insertAtStart = Arb.boolean().bind()
            if (insertAtStart) "$special $baseText" else "$baseText $special"
        } else {
            baseText
        }
    }

    /**
     * Generates a SchemaVariable with a given name.
     */
    fun arbSchemaVariable(name: String? = null): Arb<SchemaVariable> = arbitrary {
        val varName = name ?: arbVariableName().bind()
        val type = arbSchemaType().bind()
        val scope = arbVariableScope().bind()
        val hasDescription = Arb.boolean().bind()
        val description = if (hasDescription) arbDescription().bind() else null

        SchemaVariable(
            name = varName,
            type = type,
            scope = scope,
            description = description
        )
    }

    /**
     * Generates a SchemaGroup with unique variable names.
     */
    fun arbSchemaGroup(): Arb<SchemaGroup> = arbitrary {
        val groupName = arbGroupName().bind()
        val numVars = Arb.int(1..3).bind()
        val usedNames = mutableSetOf<String>()
        val variables = (1..numVars).map {
            var varName: String
            do {
                varName = arbVariableName().bind()
            } while (varName in usedNames)
            usedNames.add(varName)
            arbSchemaVariable(varName).bind()
        }

        SchemaGroup(name = groupName, variables = variables)
    }

    /**
     * Generates a valid Schema object with unique variable names across all variables and groups,
     * and unique environment names and group names.
     */
    fun arbSchema(): Arb<Schema> = arbitrary {
        // Generate unique environment names
        val numEnvs = Arb.int(1..4).bind()
        val usedEnvNames = mutableSetOf<String>()
        val environments = (1..numEnvs).map {
            var envName: String
            do {
                envName = arbEnvironmentName().bind()
            } while (envName in usedEnvNames)
            usedEnvNames.add(envName)
            envName
        }

        // Generate top-level variables with unique names
        val numVars = Arb.int(0..4).bind()
        val usedVarNames = mutableSetOf<String>()
        val variables = (1..numVars).map {
            var varName: String
            do {
                varName = arbVariableName().bind()
            } while (varName in usedVarNames)
            usedVarNames.add(varName)
            arbSchemaVariable(varName).bind()
        }

        // Generate groups with unique group names and unique variable names
        val numGroups = Arb.int(0..3).bind()
        val usedGroupNames = mutableSetOf<String>()
        val groups = (1..numGroups).map {
            var groupName: String
            do {
                groupName = arbGroupName().bind()
            } while (groupName in usedGroupNames)
            usedGroupNames.add(groupName)

            val numGroupVars = Arb.int(1..3).bind()
            val groupVariables = (1..numGroupVars).map {
                var varName: String
                do {
                    varName = arbVariableName().bind()
                } while (varName in usedVarNames)
                usedVarNames.add(varName)
                arbSchemaVariable(varName).bind()
            }

            SchemaGroup(name = groupName, variables = groupVariables)
        }

        Schema(
            environments = environments,
            variables = variables,
            groups = groups
        )
    }

    /**
     * Generates a YAML schema string where variables omit the `scope` field.
     * Produces a valid schema with 1-4 variables, each having a type but no scope declaration.
     */
    fun arbSchemaYamlWithoutScope(): Arb<String> = arbitrary {
        val numEnvs = Arb.int(1..3).bind()
        val usedEnvNames = mutableSetOf<String>()
        val environments = (1..numEnvs).map {
            var envName: String
            do {
                envName = arbEnvironmentName().bind()
            } while (envName in usedEnvNames)
            usedEnvNames.add(envName)
            envName
        }

        val numVars = Arb.int(1..4).bind()
        val usedVarNames = mutableSetOf<String>()
        val variables = (1..numVars).map {
            var varName: String
            do {
                varName = arbVariableName().bind()
            } while (varName in usedVarNames)
            usedVarNames.add(varName)

            val type = arbSchemaType().bind()
            val typeStr = schemaTypeToYamlString(type)

            val hasDescription = Arb.boolean().bind()
            val description = if (hasDescription) arbDescription().bind() else null

            Triple(varName, typeStr, description)
        }

        // Build YAML string without scope field
        val sb = StringBuilder()
        sb.appendLine("environments:")
        for (env in environments) {
            sb.appendLine("  - $env")
        }
        sb.appendLine()
        sb.appendLine("variables:")
        for ((varName, typeStr, description) in variables) {
            sb.appendLine("  $varName:")
            sb.appendLine("    type: $typeStr")
            // Deliberately omit scope field
            if (description != null) {
                sb.appendLine("    description: \"$description\"")
            }
        }

        sb.toString()
    }

    /**
     * Generates a valid schema YAML string that includes at least one variable with a `default` field.
     * Used for Property 2 testing (parser should reject schemas with defaults).
     */
    fun arbSchemaYamlWithDefault(): Arb<String> = arbitrary {
        val numEnvs = Arb.int(1..3).bind()
        val usedEnvNames = mutableSetOf<String>()
        val environments = (1..numEnvs).map {
            var envName: String
            do {
                envName = arbEnvironmentName().bind()
            } while (envName in usedEnvNames)
            usedEnvNames.add(envName)
            envName
        }

        val numVars = Arb.int(1..4).bind()
        val usedVarNames = mutableSetOf<String>()
        // Pick at least one variable index to have a default
        val defaultIndex = Arb.int(0 until numVars).bind()

        val sb = StringBuilder()
        sb.appendLine("environments:")
        for (env in environments) {
            sb.appendLine("  - $env")
        }
        sb.appendLine()
        sb.appendLine("variables:")

        for (i in 0 until numVars) {
            var varName: String
            do {
                varName = arbVariableName().bind()
            } while (varName in usedVarNames)
            usedVarNames.add(varName)

            val type = arbSchemaType().bind()
            val typeStr = schemaTypeToYamlString(type)

            sb.appendLine("  $varName:")
            sb.appendLine("    type: $typeStr")

            // Add a default field on the designated index (and possibly others)
            val addDefault = if (i == defaultIndex) true else Arb.boolean().bind()
            if (addDefault) {
                val defaultValue = arbDefaultValueForYaml(type, it)
                sb.appendLine("    default: \"$defaultValue\"")
            }
        }

        sb.toString()
    }

    /**
     * Generates a default value string suitable for YAML embedding, based on the given type.
     * Used internally by arbSchemaYamlWithDefault().
     */
    private fun arbDefaultValueForYaml(type: SchemaType, rs: io.kotest.property.RandomSource): String {
        return when (type) {
            SchemaType.STRING -> {
                val length = rs.random.nextInt(3, 15)
                val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
                (1..length).map { chars.random(rs.random) }.joinToString("")
            }
            SchemaType.INT -> rs.random.nextInt(-999, 999).toString()
            SchemaType.LONG -> rs.random.nextLong(-9999, 9999).toString()
            SchemaType.DOUBLE -> String.format("%.2f", rs.random.nextDouble(-99.0, 99.0))
            SchemaType.FLOAT -> String.format("%.2f", rs.random.nextFloat() * 198.0f - 99.0f)
            SchemaType.BOOLEAN -> if (rs.random.nextBoolean()) "true" else "false"
            SchemaType.URL -> listOf(
                "http://localhost",
                "https://example.com",
                "http://api.test.io/v1",
                "https://myapp.dev:8080"
            ).random(rs.random)
        }
    }

    /**
     * Converts a SchemaType to its YAML string representation.
     */
    private fun schemaTypeToYamlString(type: SchemaType): String = when (type) {
        SchemaType.STRING -> "String"
        SchemaType.INT -> "Int"
        SchemaType.LONG -> "Long"
        SchemaType.DOUBLE -> "Double"
        SchemaType.FLOAT -> "Float"
        SchemaType.BOOLEAN -> "Boolean"
        SchemaType.URL -> "Url"
    }
}
