package adven.kenv.config.model

/**
 * Represents a parsing error with location information for IDE integration.
 *
 * @property message Descriptive error message
 * @property filePath Path to the file where the error occurred
 * @property line Line number where the error occurred (1-based)
 * @property column Optional column number where the error occurred (1-based)
 */
data class ParseError(
    val message: String,
    val filePath: String,
    val line: Int,
    val column: Int?
)
