package adven.kenv.config.schema

/**
 * Determines whether a variable is shared across all environments or environment-specific.
 */
enum class VariableScope {
    /** Single value shared across all environments, defined via schema default or env.global file */
    GLOBAL,
    /** Must be defined in every declared environment file */
    ENVIRONMENT
}
