package adven.kenv.config.schema

/**
 * Represents a single variable declaration within the schema.
 *
 * @property name The variable name (e.g., "API_HOST")
 * @property type The declared type for this variable
 * @property scope Whether this variable is global or environment-scoped
 * @property description Optional documentation comment for the variable
 */
data class SchemaVariable(
    val name: String,
    val type: SchemaType,
    val scope: VariableScope,
    val description: String?
)
