package adven.kenv.config.env

/**
 * Supported environment file formats.
 */
enum class EnvFileFormat {
    /** Standard .env format with KEY=value pairs */
    DOT_ENV,
    /** YAML 1.2 format */
    YAML,
    /** TOML 1.0 format */
    TOML
}
