package adven.kenv.config.plugin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker
import kotlin.io.path.createTempDirectory

class GitignoreCheckerTest : FunSpec({

    test("logs warning when .gitignore is missing from kenv directory") {
        val tempDir = createTempDirectory("kenv-gitignore-test")
        try {
            val logger = TestLogger()
            val checker = GitignoreChecker(logger)

            checker.check(tempDir.toFile())

            logger.warnings.size shouldBe 1
            val warning = logger.warnings.first()
            warning shouldContain ".gitignore"
            warning shouldContain "env.production.*"
            warning shouldContain "env.global.*"
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("silent when .gitignore is present in kenv directory") {
        val tempDir = createTempDirectory("kenv-gitignore-test")
        try {
            // Create a .gitignore file in the directory
            tempDir.resolve(".gitignore").toFile().writeText("env.production.*\nenv.global.*\n")

            val logger = TestLogger()
            val checker = GitignoreChecker(logger)

            checker.check(tempDir.toFile())

            logger.warnings.shouldBeEmpty()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("never creates or modifies files in the kenv directory") {
        val tempDir = createTempDirectory("kenv-gitignore-test")
        try {
            // Record the initial state of the directory (empty)
            val filesBefore = tempDir.toFile().listFiles()?.map { it.name }?.toSet() ?: emptySet()

            val logger = TestLogger()
            val checker = GitignoreChecker(logger)

            checker.check(tempDir.toFile())

            // Verify no files were created
            val filesAfter = tempDir.toFile().listFiles()?.map { it.name }?.toSet() ?: emptySet()
            filesAfter shouldBe filesBefore
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    test("never creates or modifies files even when .gitignore already exists") {
        val tempDir = createTempDirectory("kenv-gitignore-test")
        try {
            // Create a .gitignore with known content
            val gitignoreFile = tempDir.resolve(".gitignore").toFile()
            val originalContent = "*.secret\n"
            gitignoreFile.writeText(originalContent)
            val lastModified = gitignoreFile.lastModified()

            // Small delay to ensure any modification would have a different timestamp
            Thread.sleep(50)

            val logger = TestLogger()
            val checker = GitignoreChecker(logger)

            checker.check(tempDir.toFile())

            // Verify .gitignore was not modified
            gitignoreFile.readText() shouldBe originalContent
            gitignoreFile.lastModified() shouldBe lastModified
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
})

/**
 * A minimal test Logger implementation that captures warn() messages.
 * All other logging methods are no-ops.
 */
private class TestLogger : Logger {
    val warnings = mutableListOf<String>()

    override fun getName(): String = "TestLogger"

    // Lifecycle and quiet (Gradle-specific)
    override fun isLifecycleEnabled(): Boolean = false
    override fun lifecycle(message: String?) {}
    override fun lifecycle(message: String?, vararg objects: Any?) {}
    override fun lifecycle(message: String?, throwable: Throwable?) {}
    override fun isQuietEnabled(): Boolean = false
    override fun quiet(message: String?) {}
    override fun quiet(message: String?, vararg objects: Any?) {}
    override fun quiet(message: String?, throwable: Throwable?) {}
    override fun isEnabled(level: LogLevel?): Boolean = level == LogLevel.WARN
    override fun log(level: LogLevel?, message: String?) {
        if (level == LogLevel.WARN) warnings.add(message ?: "")
    }
    override fun log(level: LogLevel?, message: String?, vararg params: Any?) {
        if (level == LogLevel.WARN) warnings.add(message ?: "")
    }
    override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {
        if (level == LogLevel.WARN) warnings.add(message ?: "")
    }

    // Trace
    override fun isTraceEnabled(): Boolean = false
    override fun isTraceEnabled(marker: Marker?): Boolean = false
    override fun trace(msg: String?) {}
    override fun trace(format: String?, arg: Any?) {}
    override fun trace(format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(format: String?, vararg arguments: Any?) {}
    override fun trace(msg: String?, t: Throwable?) {}
    override fun trace(marker: Marker?, msg: String?) {}
    override fun trace(marker: Marker?, format: String?, arg: Any?) {}
    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {}
    override fun trace(marker: Marker?, msg: String?, t: Throwable?) {}

    // Debug
    override fun isDebugEnabled(): Boolean = false
    override fun isDebugEnabled(marker: Marker?): Boolean = false
    override fun debug(msg: String?) {}
    override fun debug(format: String?, arg: Any?) {}
    override fun debug(format: String?, arg1: Any?, arg2: Any?) {}
    override fun debug(format: String?, vararg arguments: Any?) {}
    override fun debug(msg: String?, t: Throwable?) {}
    override fun debug(marker: Marker?, msg: String?) {}
    override fun debug(marker: Marker?, format: String?, arg: Any?) {}
    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {}

    // Info
    override fun isInfoEnabled(): Boolean = false
    override fun isInfoEnabled(marker: Marker?): Boolean = false
    override fun info(msg: String?) {}
    override fun info(format: String?, arg: Any?) {}
    override fun info(format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(format: String?, vararg arguments: Any?) {}
    override fun info(msg: String?, t: Throwable?) {}
    override fun info(marker: Marker?, msg: String?) {}
    override fun info(marker: Marker?, format: String?, arg: Any?) {}
    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun info(marker: Marker?, msg: String?, t: Throwable?) {}

    // Warn — capture messages
    override fun isWarnEnabled(): Boolean = true
    override fun isWarnEnabled(marker: Marker?): Boolean = true
    override fun warn(msg: String?) { warnings.add(msg ?: "") }
    override fun warn(format: String?, arg: Any?) { warnings.add(format ?: "") }
    override fun warn(format: String?, arg1: Any?, arg2: Any?) { warnings.add(format ?: "") }
    override fun warn(format: String?, vararg arguments: Any?) { warnings.add(format ?: "") }
    override fun warn(msg: String?, t: Throwable?) { warnings.add(msg ?: "") }
    override fun warn(marker: Marker?, msg: String?) { warnings.add(msg ?: "") }
    override fun warn(marker: Marker?, format: String?, arg: Any?) { warnings.add(format ?: "") }
    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) { warnings.add(format ?: "") }
    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) { warnings.add(format ?: "") }
    override fun warn(marker: Marker?, msg: String?, t: Throwable?) { warnings.add(msg ?: "") }

    // Error
    override fun isErrorEnabled(): Boolean = false
    override fun isErrorEnabled(marker: Marker?): Boolean = false
    override fun error(msg: String?) {}
    override fun error(format: String?, arg: Any?) {}
    override fun error(format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(format: String?, vararg arguments: Any?) {}
    override fun error(msg: String?, t: Throwable?) {}
    override fun error(marker: Marker?, msg: String?) {}
    override fun error(marker: Marker?, format: String?, arg: Any?) {}
    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {}
    override fun error(marker: Marker?, msg: String?, t: Throwable?) {}
}
