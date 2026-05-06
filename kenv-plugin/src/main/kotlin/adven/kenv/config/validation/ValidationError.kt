package adven.kenv.config.validation

import adven.kenv.config.schema.SchemaType

/**
 * Sealed class representing the different types of validation errors
 * that can occur when checking environment configurations against a schema.
 */
sealed class ValidationError {
    /**
     * An environment-scoped variable is missing from an environment file
     * and has no default value in the schema.
     */
    data class MissingVariable(
        val variableName: String,
        val environmentName: String
    ) : ValidationError()

    /**
     * A global-scoped variable has no default value in the schema
     * and is absent from the global values file.
     */
    data class MissingGlobalVariable(
        val variableName: String
    ) : ValidationError()

    /**
     * A variable's value does not parse as its declared type.
     */
    data class TypeMismatch(
        val variableName: String,
        val expectedType: SchemaType,
        val actualValue: String,
        val environmentName: String?
    ) : ValidationError()

    /**
     * The active environment name does not match any declared environment in the schema.
     */
    data class InvalidEnvironment(
        val environmentName: String,
        val validEnvironments: List<String>
    ) : ValidationError()
}
