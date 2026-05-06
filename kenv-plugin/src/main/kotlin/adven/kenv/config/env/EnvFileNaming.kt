package adven.kenv.config.env

/**
 * Utility for working with environment file naming conventions.
 *
 * Environment files follow the pattern: `env.<name>.<ext>`
 * where `<name>` is the environment name (e.g., "dev", "production")
 * and `<ext>` is the file extension (e.g., "env", "yaml", "toml").
 *
 * The special name "global" identifies the global values file.
 */
object EnvFileNaming {

    private const val PREFIX = "env."
    private const val GLOBAL_NAME = "global"

    /**
     * Supported file extensions mapped to their format.
     */
    private val EXTENSION_MAP = mapOf(
        "env" to EnvFileFormat.DOT_ENV,
        "yaml" to EnvFileFormat.YAML,
        "yml" to EnvFileFormat.YAML,
        "toml" to EnvFileFormat.TOML
    )

    /**
     * Extracts the environment name from a filename following the `env.<name>.<ext>` pattern.
     *
     * @param filename The filename (without directory path) to extract from
     * @return The environment name, or null if the filename doesn't match the pattern
     */
    fun extractEnvironmentName(filename: String): String? {
        if (!filename.startsWith(PREFIX)) return null

        val withoutPrefix = filename.removePrefix(PREFIX)
        val lastDotIndex = withoutPrefix.lastIndexOf('.')
        if (lastDotIndex <= 0) return null

        val name = withoutPrefix.substring(0, lastDotIndex)
        val extension = withoutPrefix.substring(lastDotIndex + 1)

        // Verify the extension is supported
        if (extension !in EXTENSION_MAP) return null

        return name
    }

    /**
     * Determines the [EnvFileFormat] from a filename's extension.
     *
     * @param filename The filename to determine the format of
     * @return The detected format, or null if the extension is not supported
     */
    fun detectFormat(filename: String): EnvFileFormat? {
        val lastDotIndex = filename.lastIndexOf('.')
        if (lastDotIndex < 0) return null

        val extension = filename.substring(lastDotIndex + 1)
        return EXTENSION_MAP[extension]
    }

    /**
     * Checks whether the given filename represents a global values file (`env.global.<ext>`).
     *
     * @param filename The filename to check
     * @return true if this is a global values file
     */
    fun isGlobalFile(filename: String): Boolean {
        val name = extractEnvironmentName(filename)
        return name == GLOBAL_NAME
    }

    /**
     * Constructs a filename from an environment name and format.
     *
     * @param environmentName The environment name (e.g., "production")
     * @param format The desired file format
     * @return The constructed filename (e.g., "env.production.yaml")
     */
    fun buildFilename(environmentName: String, format: EnvFileFormat): String {
        val extension = when (format) {
            EnvFileFormat.DOT_ENV -> "env"
            EnvFileFormat.YAML -> "yaml"
            EnvFileFormat.TOML -> "toml"
        }
        return "$PREFIX$environmentName.$extension"
    }

    /**
     * Returns the list of supported file extensions.
     */
    fun supportedExtensions(): Set<String> = EXTENSION_MAP.keys
}
