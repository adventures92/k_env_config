package adven.kenv.config.plugin

import adven.kenv.config.codegen.DefaultCodeGenerator
import adven.kenv.config.env.DotEnvParser
import adven.kenv.config.env.EnvFileFormat
import adven.kenv.config.env.EnvFileNaming
import adven.kenv.config.env.EnvFileParser
import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.env.TomlEnvParser
import adven.kenv.config.env.YamlEnvParser
import adven.kenv.config.model.ParseResult
import adven.kenv.config.schema.YamlSchemaParser
import adven.kenv.config.validation.DefaultValidator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that orchestrates schema parsing, environment file discovery,
 * validation, and Kotlin code generation using the unified directory convention.
 *
 * Annotated with @CacheableTask for incremental build support — the task
 * will be skipped when inputs haven't changed.
 */
@CacheableTask
abstract class KEnvGenerateTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kenvDirectory: DirectoryProperty

    @get:Input
    abstract val environments: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val activeEnvironment: Property<String>

    @get:Input
    @get:Optional
    abstract val generatedClassName: Property<String>

    @get:Input
    abstract val generatedPackageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = kenvDirectory.get().asFile

        // 0. Gitignore check (advisory only)
        GitignoreChecker(logger).check(dir)

        // 1. Validate schema file exists
        val schemaFile = dir.resolve("schema.kenv.yaml")
        if (!schemaFile.exists()) {
            throw GradleException(
                "Schema file not found: expected schema.kenv.yaml in ${dir.absolutePath}"
            )
        }

        // 2. Use DirectoryDiscovery to find env files and global file
        val discovery = DirectoryDiscovery()
        val discoveredFiles = discovery.discover(dir, environments.get())

        // 3. Parse schema (rejects defaults)
        val schemaParser = YamlSchemaParser()
        val schemaContent = schemaFile.readText()
        val schemaResult = schemaParser.parse(schemaContent, schemaFile.path)

        val schema = when (schemaResult) {
            is ParseResult.Success -> schemaResult.value
            is ParseResult.Failure -> {
                for (error in schemaResult.errors) {
                    logger.error("Schema parse error at ${error.filePath}:${error.line}: ${error.message}")
                }
                throw GradleException("Failed to parse schema file: ${schemaFile.path}")
            }
        }

        // 4. Parse env files
        val envNames = environments.get()
        val configs = mutableMapOf<String, EnvironmentConfig>()

        for (envName in envNames) {
            val envFile = discoveredFiles.envFiles[envName]
                ?: throw GradleException(
                    "Environment file not found for '$envName': expected env.$envName.<env|yaml|yml|toml> in ${dir.absolutePath}"
                )

            val format = EnvFileNaming.detectFormat(envFile.name)
                ?: throw GradleException("Unsupported environment file format: ${envFile.name}")

            val parser = getParserForFormat(format)
            val content = envFile.readText()
            val result = parser.parse(content, envFile.path, envName)

            when (result) {
                is ParseResult.Success -> configs[envName] = result.value
                is ParseResult.Failure -> {
                    for (error in result.errors) {
                        logger.error("Parse error in ${error.filePath}:${error.line}: ${error.message}")
                    }
                    throw GradleException("Failed to parse environment file: ${envFile.path}")
                }
            }
        }

        // 5. Parse global file (if present)
        val globalConfig = discoveredFiles.globalFile?.let { globalFile ->
            parseGlobalFile(globalFile)
        }

        // 6. Validate (strict, no default fallback)
        val validator = DefaultValidator()
        val activeEnv = activeEnvironment.orNull
        val validationResult = validator.validate(schema, configs, globalConfig, activeEnv)

        if (!validationResult.isValid) {
            for (error in validationResult.errors) {
                logger.error(formatValidationError(error))
            }
            throw GradleException("Environment configuration validation failed with ${validationResult.errors.size} error(s)")
        }

        // Log warnings
        for (warning in validationResult.warnings) {
            logger.warn("Warning: ${warning.message}")
        }

        // 7. Generate code (with KDoc, package required)
        val className = generatedClassName.getOrElse("EnvConfig")
        val packageName = generatedPackageName.get()
        val codeGenerator = DefaultCodeGenerator(packageName)

        val generatedCode = codeGenerator.generate(
            schema = schema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = activeEnv,
            className = className
        )

        // 8. Write output
        val outputDir = outputDirectory.get().asFile
        val packageDir = outputDir.resolve(packageName.replace('.', '/'))
        packageDir.mkdirs()

        val outputFile = packageDir.resolve("$className.kt")
        outputFile.writeText(generatedCode)

        logger.lifecycle("KEnv: Generated ${outputFile.absolutePath}")
    }

    /**
     * Parses a global values file into a GlobalConfig.
     */
    private fun parseGlobalFile(file: java.io.File): GlobalConfig {
        val format = EnvFileNaming.detectFormat(file.name)
            ?: throw GradleException("Unsupported global values file format: ${file.name}")

        val parser = getParserForFormat(format)
        val content = file.readText()
        val result = parser.parse(content, file.path, "global")

        return when (result) {
            is ParseResult.Success -> GlobalConfig(
                values = result.value.values,
                format = format,
                sourceFile = file.path
            )
            is ParseResult.Failure -> {
                for (error in result.errors) {
                    logger.error("Parse error in ${error.filePath}:${error.line}: ${error.message}")
                }
                throw GradleException("Failed to parse global values file: ${file.path}")
            }
        }
    }

    /**
     * Returns the appropriate parser for the given file format.
     */
    private fun getParserForFormat(format: EnvFileFormat): EnvFileParser {
        return when (format) {
            EnvFileFormat.DOT_ENV -> DotEnvParser()
            EnvFileFormat.YAML -> YamlEnvParser()
            EnvFileFormat.TOML -> TomlEnvParser()
        }
    }

    /**
     * Formats a validation error into a human-readable string for logger output.
     */
    private fun formatValidationError(error: adven.kenv.config.validation.ValidationError): String {
        return when (error) {
            is adven.kenv.config.validation.ValidationError.MissingVariable ->
                "Missing required variable '${error.variableName}' in environment '${error.environmentName}'"
            is adven.kenv.config.validation.ValidationError.MissingGlobalVariable ->
                "Missing global variable '${error.variableName}': define in env.global.<ext>"
            is adven.kenv.config.validation.ValidationError.TypeMismatch -> {
                val location = if (error.environmentName != null) {
                    "in environment '${error.environmentName}'"
                } else {
                    "for global variable"
                }
                "Type mismatch for '${error.variableName}' $location: expected ${error.expectedType}, got '${error.actualValue}'"
            }
            is adven.kenv.config.validation.ValidationError.InvalidEnvironment ->
                "Invalid activeEnvironment '${error.environmentName}'. Valid environments: ${error.validEnvironments}"
        }
    }
}
