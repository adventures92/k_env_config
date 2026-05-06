package adven.kenv.config.generators

import adven.kenv.config.env.EnvFileFormat
import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.*
import adven.kenv.config.validation.canParse
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Custom Kotest Arb generators for EnvironmentConfig and related models.
 */
object EnvConfigGenerators {

    /**
     * Generates a valid environment variable key: uppercase letters, digits, underscores,
     * starting with a letter.
     */
    fun arbEnvKey(): Arb<String> = arbitrary {
        val firstChar = ('A'..'Z').random(it.random)
        val length = it.random.nextInt(1, 10)
        val restChars = (1..length).map {
            val chars = ('A'..'Z') + ('0'..'9') + listOf('_')
            chars.random()
        }.joinToString("")
        "$firstChar$restChars"
    }

    /**
     * Generates a simple alphanumeric value that is safe for round-trip in all formats.
     * Avoids special characters that could break quoting in .env, YAML, or TOML.
     */
    fun arbSafeValue(): Arb<String> = arbitrary {
        val length = it.random.nextInt(1, 20)
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Generates a valid environment name (lowercase letters only).
     */
    fun arbEnvironmentName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a valid EnvironmentConfig with safe key-value pairs for a given format.
     * Keys and values are constrained to survive round-trip in all formats.
     */
    fun arbEnvironmentConfig(format: EnvFileFormat): Arb<EnvironmentConfig> = arbitrary {
        val name = arbEnvironmentName().bind()
        val numPairs = Arb.int(1..5).bind()
        val usedKeys = mutableSetOf<String>()
        val values = mutableMapOf<String, String>()

        repeat(numPairs) {
            var key: String
            do {
                key = arbEnvKey().bind()
            } while (key in usedKeys)
            usedKeys.add(key)
            values[key] = arbSafeValue().bind()
        }

        EnvironmentConfig(
            name = name,
            values = values,
            format = format,
            sourceFile = "env.$name.${formatExtension(format)}"
        )
    }

    /**
     * Generates a valid EnvironmentConfig for any supported format.
     */
    fun arbEnvironmentConfig(): Arb<EnvironmentConfig> = arbitrary {
        val format = Arb.enum<EnvFileFormat>().bind()
        arbEnvironmentConfig(format).bind()
    }

    /**
     * Generates a value that is valid for the given SchemaType.
     */
    fun arbValidValueForType(type: SchemaType): Arb<String> = when (type) {
        SchemaType.STRING -> arbSafeValue()
        SchemaType.INT -> Arb.int(-9999..9999).map { it.toString() }
        SchemaType.LONG -> Arb.long(-99999L..99999L).map { it.toString() }
        SchemaType.DOUBLE -> Arb.double(range = -999.0..999.0)
            .filter { it.isFinite() }
            .map { String.format("%.2f", it) }
        SchemaType.FLOAT -> Arb.float(range = -999.0f..999.0f)
            .filter { it.isFinite() }
            .map { String.format("%.2f", it) }
        SchemaType.BOOLEAN -> Arb.of("true", "false")
        SchemaType.URL -> Arb.of(
            "http://localhost",
            "https://example.com",
            "http://api.test.io/v1",
            "https://myapp.dev:8080"
        )
    }

    /**
     * Generates a value that does NOT parse as the given SchemaType.
     * Only applicable for types that can actually fail (not STRING or URL).
     */
    fun arbInvalidValueForType(type: SchemaType): Arb<String> = when (type) {
        SchemaType.STRING -> Arb.of("valid") // STRING always parses, shouldn't be called
        SchemaType.INT -> Arb.of("notanint", "3.14", "abc", "12.5", "true", "")
        SchemaType.LONG -> Arb.of("notalong", "3.14", "abc", "12.5", "true", "")
        SchemaType.DOUBLE -> Arb.of("notadouble", "abc", "true", "xyz", "")
        SchemaType.FLOAT -> Arb.of("notafloat", "abc", "true", "xyz", "")
        SchemaType.BOOLEAN -> Arb.of("notabool", "yes", "no", "1", "0", "maybe", "abc")
        SchemaType.URL -> Arb.of("") // URL only fails on empty string
    }

    /**
     * Generates a valid EnvironmentConfig that conforms to all schema requirements.
     * Every environment-scoped variable is present with a valid value.
     */
    fun arbConformingConfig(schema: Schema): Arb<EnvironmentConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }
        val values = mutableMapOf<String, String>()

        for (variable in envVars) {
            // Always provide a value for environment-scoped variables
            values[variable.name] = arbValidValueForType(variable.type).bind()
        }

        val envName = SchemaGenerators.arbEnvironmentName().bind()
        EnvironmentConfig(
            name = envName,
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.$envName.env"
        )
    }

    /**
     * Generates a GlobalConfig that conforms to all schema requirements.
     * Every global-scoped variable is present with a valid value.
     */
    fun arbConformingGlobalConfig(schema: Schema): Arb<GlobalConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }
        val values = mutableMapOf<String, String>()

        for (variable in globalVars) {
            // Always provide a value for global-scoped variables
            values[variable.name] = arbValidValueForType(variable.type).bind()
        }

        GlobalConfig(
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )
    }

    /**
     * Generates an EnvironmentConfig that is missing at least one required environment-scoped variable.
     * All environment-scoped variables are required (no default fallback in v2).
     */
    fun arbIncompleteConfig(schema: Schema): Arb<EnvironmentConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }

        // If no env vars, just return an empty config
        if (envVars.isEmpty()) {
            val envName = SchemaGenerators.arbEnvironmentName().bind()
            return@arbitrary EnvironmentConfig(
                name = envName,
                values = emptyMap(),
                format = EnvFileFormat.DOT_ENV,
                sourceFile = "env.$envName.env"
            )
        }

        val values = mutableMapOf<String, String>()
        // Include some but not all variables (at least one must be missing)
        val numToInclude = Arb.int(0 until envVars.size).bind()
        val shuffled = envVars.shuffled(it.random)
        for (i in 0 until numToInclude) {
            values[shuffled[i].name] = arbValidValueForType(shuffled[i].type).bind()
        }

        val envName = SchemaGenerators.arbEnvironmentName().bind()
        EnvironmentConfig(
            name = envName,
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.$envName.env"
        )
    }

    /**
     * Generates a GlobalConfig that is missing at least one required global-scoped variable.
     * All global-scoped variables are required (no default fallback in v2).
     */
    fun arbIncompleteGlobalConfig(schema: Schema): Arb<GlobalConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }

        // If no global vars, just return an empty config
        if (globalVars.isEmpty()) {
            return@arbitrary GlobalConfig(
                values = emptyMap(),
                format = EnvFileFormat.DOT_ENV,
                sourceFile = "env.global.env"
            )
        }

        val values = mutableMapOf<String, String>()
        // Include some but not all variables (at least one must be missing)
        val numToInclude = Arb.int(0 until globalVars.size).bind()
        val shuffled = globalVars.shuffled(it.random)
        for (i in 0 until numToInclude) {
            values[shuffled[i].name] = arbValidValueForType(shuffled[i].type).bind()
        }

        GlobalConfig(
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )
    }

    /**
     * Generates an EnvironmentConfig with type-mismatched values.
     * At least one environment-scoped variable will have a value that doesn't parse as its declared type.
     * Only targets variables with types that can actually fail (not STRING or URL).
     */
    fun arbMismatchedConfig(schema: Schema): Arb<EnvironmentConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }
        val mismatchableVars = envVars.filter {
            it.type != SchemaType.STRING && it.type != SchemaType.URL
        }

        val values = mutableMapOf<String, String>()

        // Provide valid values for all env vars first
        for (variable in envVars) {
            values[variable.name] = arbValidValueForType(variable.type).bind()
        }

        // Now corrupt at least one mismatchable variable
        if (mismatchableVars.isNotEmpty()) {
            val numToCorrupt = Arb.int(1..mismatchableVars.size).bind()
            val toCorrupt = mismatchableVars.shuffled(it.random).take(numToCorrupt)
            for (variable in toCorrupt) {
                values[variable.name] = arbInvalidValueForType(variable.type).bind()
            }
        }

        val envName = SchemaGenerators.arbEnvironmentName().bind()
        EnvironmentConfig(
            name = envName,
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.$envName.env"
        )
    }

    /**
     * Generates an EnvironmentConfig with extra undeclared variables not in the schema.
     * Also includes all required variables so validation doesn't fail on missing vars.
     */
    fun arbExtraVarsConfig(schema: Schema): Arb<EnvironmentConfig> = arbitrary {
        val allVars = getAllVariables(schema)
        val allVarNames = allVars.map { it.name }.toSet()
        val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }

        val values = mutableMapOf<String, String>()

        // Provide valid values for all env-scoped variables
        for (variable in envVars) {
            values[variable.name] = arbValidValueForType(variable.type).bind()
        }

        // Add extra undeclared variables
        val numExtra = Arb.int(1..3).bind()
        repeat(numExtra) {
            var key: String
            do {
                key = arbEnvKey().bind()
            } while (key in allVarNames || key in values)
            values[key] = arbSafeValue().bind()
        }

        val envName = SchemaGenerators.arbEnvironmentName().bind()
        EnvironmentConfig(
            name = envName,
            values = values,
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.$envName.env"
        )
    }

    /**
     * Collects all variables from the schema, including top-level and group variables.
     */
    private fun getAllVariables(schema: Schema): List<SchemaVariable> {
        val variables = mutableListOf<SchemaVariable>()
        variables.addAll(schema.variables)
        for (group in schema.groups) {
            variables.addAll(group.variables)
        }
        return variables
    }

    /**
     * Returns the file extension for a given format.
     */
    private fun formatExtension(format: EnvFileFormat): String = when (format) {
        EnvFileFormat.DOT_ENV -> "env"
        EnvFileFormat.YAML -> "yaml"
        EnvFileFormat.TOML -> "toml"
    }
}
