package adven.kenv.config.validation

/**
 * Represents a non-fatal validation warning.
 * Warnings do not prevent the build from proceeding.
 *
 * @property variableName The variable that triggered the warning
 * @property environmentName The environment where the warning occurred (null for global file)
 * @property message Descriptive warning message
 */
data class ValidationWarning(
    val variableName: String,
    val environmentName: String?,
    val message: String
)
