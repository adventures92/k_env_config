package adven.kenv.config.plugin

import org.gradle.api.logging.Logger
import java.io.File

class GitignoreChecker(private val logger: Logger) {

    fun check(kenvDirectory: File) {
        val gitignore = kenvDirectory.resolve(".gitignore")
        if (!gitignore.exists()) {
            logger.warn(
                "KEnv: No .gitignore found in ${kenvDirectory.path}. " +
                "Consider adding one with patterns like:\n" +
                "  env.production.*\n" +
                "  env.global.*"
            )
        }
    }
}
