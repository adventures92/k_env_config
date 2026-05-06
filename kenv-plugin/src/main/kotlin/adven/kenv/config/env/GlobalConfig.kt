package adven.kenv.config.env

/**
 * Represents the parsed contents of a global values file (env.global.<ext>).
 * Contains values for global-scoped variables shared across all environments.
 *
 * @property values Raw key-value pairs for global variables
 * @property format The file format this config was parsed from
 * @property sourceFile The file path for error reporting
 */
data class GlobalConfig(
    val values: Map<String, String>,
    val format: EnvFileFormat,
    val sourceFile: String
)
