package adven.kenv.config.schema

/**
 * Represents the complete schema definition for environment variables.
 *
 * @property environments List of declared environment names (e.g., "dev", "staging", "production")
 * @property variables Top-level variables declared outside of any group
 * @property groups Named groups of variables for organizational clarity
 */
data class Schema(
    val environments: List<String>,
    val variables: List<SchemaVariable>,
    val groups: List<SchemaGroup>
)
