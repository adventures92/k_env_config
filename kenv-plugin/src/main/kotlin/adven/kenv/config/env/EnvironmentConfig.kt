package adven.kenv.config.env

/**
 * Represents the parsed contents of an environment-specific configuration file.
 *
 * @property name The environment name (e.g., "production")
 * @property values Raw key-value pairs from the environment file
 * @property format The file format this config was parsed from
 * @property sourceFile The file path for error reporting
 */
data class EnvironmentConfig(
    val name: String,
    val values: Map<String, String>,
    val format: EnvFileFormat,
    val sourceFile: String
)
