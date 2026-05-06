package adven.kenv.config.schema

import adven.kenv.config.model.ParseResult

/**
 * Parses and prints schema definitions for environment variable configuration.
 */
interface SchemaParser {
    /**
     * Parses a YAML schema string into a [Schema] model.
     *
     * @param content The raw YAML content of the schema file
     * @param filePath The file path used for error reporting
     * @return [ParseResult.Success] with the parsed schema, or [ParseResult.Failure] with error details
     */
    fun parse(content: String, filePath: String): ParseResult<Schema>

    /**
     * Prints a [Schema] model back into a valid YAML string.
     *
     * @param schema The schema to serialize
     * @return A formatted YAML string representation of the schema
     */
    fun print(schema: Schema): String
}
