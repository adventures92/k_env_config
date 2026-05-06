package adven.kenv.config.model

/**
 * Represents the result of parsing a schema or environment file.
 * Either a successful parse with the resulting value, or a failure with error details.
 */
sealed class ParseResult<out T> {
    /**
     * Successful parse result containing the parsed value.
     */
    data class Success<T>(val value: T) : ParseResult<T>()

    /**
     * Failed parse result containing one or more errors with location information.
     */
    data class Failure(val errors: List<ParseError>) : ParseResult<Nothing>()
}
