package adven.kenv.config.plugin

import java.io.File

data class DiscoveredFiles(
    val schemaFile: File,
    val envFiles: Map<String, File>,  // environment name -> file
    val globalFile: File?
)

class DirectoryDiscovery {

    private val supportedExtensions = listOf("env", "yaml", "yml", "toml")

    fun discover(directory: File, environments: List<String>): DiscoveredFiles {
        val schemaFile = directory.resolve("schema.kenv.yaml")

        val envFiles = environments.associateWith { envName ->
            discoverEnvFile(directory, envName)
                ?: throw IllegalStateException(
                    "Environment file not found for '$envName': " +
                    "expected env.$envName.<env|yaml|yml|toml> in ${directory.absolutePath}"
                )
        }

        val globalFile = discoverGlobalFile(directory)

        return DiscoveredFiles(
            schemaFile = schemaFile,
            envFiles = envFiles,
            globalFile = globalFile
        )
    }

    fun discoverEnvFile(directory: File, envName: String): File? {
        for (ext in supportedExtensions) {
            val file = directory.resolve("env.$envName.$ext")
            if (file.exists()) return file
        }
        return null
    }

    fun discoverGlobalFile(directory: File): File? {
        for (ext in supportedExtensions) {
            val file = directory.resolve("env.global.$ext")
            if (file.exists()) return file
        }
        return null
    }
}
