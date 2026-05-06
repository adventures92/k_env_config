package adven.kenv.config.validation

/**
 * The result of validating environment configurations against a schema.
 * Contains all errors and warnings collected in a single validation pass.
 *
 * @property errors List of validation errors that prevent the build from proceeding
 * @property warnings List of non-fatal validation warnings
 */
data class ValidationResult(
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>
) {
    /** Whether the validation passed with no errors. */
    val isValid: Boolean get() = errors.isEmpty()
}
