package adven.kenv.config.env

import adven.kenv.config.model.ParseResult

/**
 * Interface for parsing and printing environment configuration files.
 * Each supported format (.env, .yaml, .toml) has its own implementation.
 */
interface EnvFileParser {
    /**
     * Parses the content of an environment file into an [EnvironmentConfig].
     *
     * @param content The raw file content to parse
     * @param filePath The file path for error reporting
     * @param environmentName The environment name to assign to the resulting config
     * @return A [ParseResult] containing either the parsed config or error details
     */
    fun parse(content: String, filePath: String, environmentName: String): ParseResult<EnvironmentConfig>

    /**
     * Prints an [EnvironmentConfig] back into the file format string representation.
     *
     * @param config The environment config to serialize
     * @return The formatted file content as a string
     */
    fun print(config: EnvironmentConfig): String
}
