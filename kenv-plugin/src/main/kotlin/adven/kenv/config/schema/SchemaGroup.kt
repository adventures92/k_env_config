package adven.kenv.config.schema

/**
 * Represents a named group of variables for organizational clarity in the generated code.
 *
 * @property name The group name (e.g., "database")
 * @property variables The variables belonging to this group
 */
data class SchemaGroup(
    val name: String,
    val variables: List<SchemaVariable>
)
