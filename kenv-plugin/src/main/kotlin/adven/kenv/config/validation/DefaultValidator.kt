package adven.kenv.config.validation

import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.Schema
import adven.kenv.config.schema.SchemaType
import adven.kenv.config.schema.SchemaVariable
import adven.kenv.config.schema.VariableScope

/**
 * Default implementation of [Validator] that performs a single-pass validation
 * collecting all errors and warnings before returning.
 */
class DefaultValidator : Validator {

    override fun validate(
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        activeEnvironment: String?
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate activeEnvironment if provided
        if (activeEnvironment != null && activeEnvironment !in schema.environments) {
            errors.add(
                ValidationError.InvalidEnvironment(
                    environmentName = activeEnvironment,
                    validEnvironments = schema.environments
                )
            )
        }

        // Collect all variables from top-level and groups
        val allVariables = getAllVariables(schema)
        val allVariableNames = allVariables.map { it.name }.toSet()

        // Validate environment-scoped variables
        val environmentVariables = allVariables.filter { it.scope == VariableScope.ENVIRONMENT }
        for (variable in environmentVariables) {
            for ((envName, config) in configs) {
                val value = config.values[variable.name]
                if (value == null) {
                    errors.add(
                        ValidationError.MissingVariable(
                            variableName = variable.name,
                            environmentName = envName
                        )
                    )
                } else if (!canParse(value, variable.type)) {
                    errors.add(
                        ValidationError.TypeMismatch(
                            variableName = variable.name,
                            expectedType = variable.type,
                            actualValue = value,
                            environmentName = envName
                        )
                    )
                }
            }
        }

        // Validate global-scoped variables
        val globalVariables = allVariables.filter { it.scope == VariableScope.GLOBAL }
        for (variable in globalVariables) {
            val globalValue = globalConfig?.values?.get(variable.name)

            if (globalValue == null) {
                errors.add(
                    ValidationError.MissingGlobalVariable(
                        variableName = variable.name
                    )
                )
            } else if (!canParse(globalValue, variable.type)) {
                errors.add(
                    ValidationError.TypeMismatch(
                        variableName = variable.name,
                        expectedType = variable.type,
                        actualValue = globalValue,
                        environmentName = null
                    )
                )
            }
        }

        // Check for undeclared variables in environment configs
        for ((envName, config) in configs) {
            for (key in config.values.keys) {
                if (key !in allVariableNames) {
                    warnings.add(
                        ValidationWarning(
                            variableName = key,
                            environmentName = envName,
                            message = "Variable '$key' in environment '$envName' is not declared in schema"
                        )
                    )
                }
            }
        }

        // Check for undeclared variables in global config
        if (globalConfig != null) {
            for (key in globalConfig.values.keys) {
                if (key !in allVariableNames) {
                    warnings.add(
                        ValidationWarning(
                            variableName = key,
                            environmentName = null,
                            message = "Variable '$key' in global config is not declared in schema"
                        )
                    )
                }
            }
        }

        return ValidationResult(errors = errors, warnings = warnings)
    }

    /**
     * Collects all variables from the schema, including top-level variables
     * and variables within groups.
     */
    private fun getAllVariables(schema: Schema): List<SchemaVariable> {
        val variables = mutableListOf<SchemaVariable>()
        variables.addAll(schema.variables)
        for (group in schema.groups) {
            variables.addAll(group.variables)
        }
        return variables
    }
}

/**
 * Checks whether a string value can be parsed as the given [SchemaType].
 *
 * @param value The raw string value to check
 * @param type The expected schema type
 * @return true if the value can be parsed as the given type, false otherwise
 */
fun canParse(value: String, type: SchemaType): Boolean = when (type) {
    SchemaType.STRING -> true
    SchemaType.INT -> value.toIntOrNull() != null
    SchemaType.LONG -> value.toLongOrNull() != null
    SchemaType.DOUBLE -> value.toDoubleOrNull() != null
    SchemaType.FLOAT -> value.toFloatOrNull() != null
    SchemaType.BOOLEAN -> value.lowercase() in listOf("true", "false")
    SchemaType.URL -> value.isNotEmpty()
}
