package adven.kenv.config.validation

import adven.kenv.config.env.EnvFileFormat
import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ValidatorTest : FunSpec({

    val validator = DefaultValidator()

    val baseSchema = Schema(
        environments = listOf("dev", "production"),
        variables = listOf(
            SchemaVariable(
                name = "API_HOST",
                type = SchemaType.URL,
                scope = VariableScope.ENVIRONMENT,
                description = null
            ),
            SchemaVariable(
                name = "APP_NAME",
                type = SchemaType.STRING,
                scope = VariableScope.GLOBAL,
                description = null
            ),
            SchemaVariable(
                name = "DEBUG",
                type = SchemaType.BOOLEAN,
                scope = VariableScope.ENVIRONMENT,
                description = null
            )
        ),
        groups = listOf(
            SchemaGroup(
                name = "database",
                variables = listOf(
                    SchemaVariable(
                        name = "DB_PORT",
                        type = SchemaType.INT,
                        scope = VariableScope.GLOBAL,
                        description = null
                    ),
                    SchemaVariable(
                        name = "DB_HOST",
                        type = SchemaType.STRING,
                        scope = VariableScope.ENVIRONMENT,
                        description = null
                    )
                )
            )
        )
    )

    test("strict validation - missing env variable produces error") {
        // In v2, all variables are required - no default fallback
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf(
                "API_HOST" to "http://localhost:8080",
                "DB_HOST" to "localhost"
                // DEBUG is absent - should produce error
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val globalConfig = GlobalConfig(
            values = mapOf(
                "APP_NAME" to "MyApp",
                "DB_PORT" to "5432"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )

        val result = validator.validate(
            schema = baseSchema,
            configs = mapOf("dev" to config),
            globalConfig = globalConfig
        )

        val missingDebugErrors = result.errors.filterIsInstance<ValidationError.MissingVariable>()
            .filter { it.variableName == "DEBUG" }
        missingDebugErrors shouldHaveSize 1
        missingDebugErrors.first().environmentName shouldBe "dev"
    }

    test("strict validation - all variables present produces no error") {
        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf(
                "API_HOST" to "http://localhost:8080",
                "DEBUG" to "true",
                "DB_HOST" to "localhost"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val globalConfig = GlobalConfig(
            values = mapOf(
                "APP_NAME" to "MyApp",
                "DB_PORT" to "5432"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )

        val result = validator.validate(
            schema = baseSchema,
            configs = mapOf("dev" to config),
            globalConfig = globalConfig
        )

        result.isValid.shouldBeTrue()
    }

    test("global variable resolution - missing global var produces error") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "SECRET_KEY",
                    type = SchemaType.STRING,
                    scope = VariableScope.GLOBAL,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to EnvironmentConfig("dev", emptyMap(), EnvFileFormat.DOT_ENV, "env.dev.env")),
            globalConfig = null
        )

        val missingGlobalErrors = result.errors.filterIsInstance<ValidationError.MissingGlobalVariable>()
        missingGlobalErrors shouldHaveSize 1
        missingGlobalErrors.first().variableName shouldBe "SECRET_KEY"
    }

    test("global variable resolution - global file value present produces no error") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "APP_NAME",
                    type = SchemaType.STRING,
                    scope = VariableScope.GLOBAL,
                    description = null
                ),
                SchemaVariable(
                    name = "PORT",
                    type = SchemaType.INT,
                    scope = VariableScope.GLOBAL,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val globalConfig = GlobalConfig(
            values = mapOf(
                "APP_NAME" to "OverriddenApp",
                "PORT" to "8080"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to EnvironmentConfig("dev", emptyMap(), EnvFileFormat.DOT_ENV, "env.dev.env")),
            globalConfig = globalConfig
        )

        val missingGlobalErrors = result.errors.filterIsInstance<ValidationError.MissingGlobalVariable>()
        missingGlobalErrors.shouldBeEmpty()
    }

    test("error message formatting - MissingVariable contains variable and environment name") {
        val schema = Schema(
            environments = listOf("production"),
            variables = listOf(
                SchemaVariable(
                    name = "API_KEY",
                    type = SchemaType.STRING,
                    scope = VariableScope.ENVIRONMENT,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val config = EnvironmentConfig(
            name = "production",
            values = emptyMap(),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.production.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("production" to config),
            globalConfig = null
        )

        result.errors shouldHaveSize 1
        val error = result.errors.first() as ValidationError.MissingVariable
        error.variableName shouldBe "API_KEY"
        error.environmentName shouldBe "production"
    }

    test("error message formatting - MissingGlobalVariable contains variable name") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "GLOBAL_SECRET",
                    type = SchemaType.STRING,
                    scope = VariableScope.GLOBAL,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to EnvironmentConfig("dev", emptyMap(), EnvFileFormat.DOT_ENV, "env.dev.env")),
            globalConfig = null
        )

        result.errors shouldHaveSize 1
        val error = result.errors.first() as ValidationError.MissingGlobalVariable
        error.variableName shouldBe "GLOBAL_SECRET"
    }

    test("error message formatting - MissingGlobalVariable when global config exists but variable absent") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "MISSING_VAR",
                    type = SchemaType.INT,
                    scope = VariableScope.GLOBAL,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val globalConfig = GlobalConfig(
            values = mapOf("OTHER_VAR" to "value"),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to EnvironmentConfig("dev", emptyMap(), EnvFileFormat.DOT_ENV, "env.dev.env")),
            globalConfig = globalConfig
        )

        result.errors shouldHaveSize 1
        val error = result.errors.first() as ValidationError.MissingGlobalVariable
        error.variableName shouldBe "MISSING_VAR"
    }

    test("strict validation - missing env variable in multiple environments produces error per environment") {
        val schema = Schema(
            environments = listOf("dev", "production"),
            variables = listOf(
                SchemaVariable(
                    name = "API_KEY",
                    type = SchemaType.STRING,
                    scope = VariableScope.ENVIRONMENT,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val devConfig = EnvironmentConfig(
            name = "dev",
            values = emptyMap(),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val prodConfig = EnvironmentConfig(
            name = "production",
            values = emptyMap(),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.production.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to devConfig, "production" to prodConfig),
            globalConfig = null
        )

        result.isValid.shouldBeFalse()
        val missingErrors = result.errors.filterIsInstance<ValidationError.MissingVariable>()
        missingErrors shouldHaveSize 2
        missingErrors.map { it.environmentName }.toSet() shouldBe setOf("dev", "production")
        missingErrors.forEach { it.variableName shouldBe "API_KEY" }
    }

    test("error message formatting - TypeMismatch contains variable, type, value, and environment") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "PORT",
                    type = SchemaType.INT,
                    scope = VariableScope.ENVIRONMENT,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf("PORT" to "not_a_number"),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to config),
            globalConfig = null
        )

        result.errors shouldHaveSize 1
        val error = result.errors.first() as ValidationError.TypeMismatch
        error.variableName shouldBe "PORT"
        error.expectedType shouldBe SchemaType.INT
        error.actualValue shouldBe "not_a_number"
        error.environmentName shouldBe "dev"
    }

    test("error message formatting - InvalidEnvironment contains name and valid list") {
        val schema = Schema(
            environments = listOf("dev", "staging", "production"),
            variables = emptyList(),
            groups = emptyList()
        )

        val result = validator.validate(
            schema = schema,
            configs = emptyMap(),
            globalConfig = null,
            activeEnvironment = "nonexistent"
        )

        val invalidEnvErrors = result.errors.filterIsInstance<ValidationError.InvalidEnvironment>()
        invalidEnvErrors shouldHaveSize 1
        invalidEnvErrors.first().environmentName shouldBe "nonexistent"
        invalidEnvErrors.first().validEnvironments shouldBe listOf("dev", "staging", "production")
    }

    test("warning for undeclared variables in environment config") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "API_HOST",
                    type = SchemaType.URL,
                    scope = VariableScope.ENVIRONMENT,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf(
                "API_HOST" to "http://localhost",
                "UNKNOWN_VAR" to "some_value",
                "ANOTHER_UNKNOWN" to "another_value"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to config),
            globalConfig = null
        )

        // Should be valid (warnings don't prevent build)
        result.isValid.shouldBeTrue()

        // Should have warnings for undeclared variables
        result.warnings shouldHaveSize 2
        val warningVarNames = result.warnings.map { it.variableName }.toSet()
        warningVarNames shouldBe setOf("UNKNOWN_VAR", "ANOTHER_UNKNOWN")

        // Each warning should reference the environment
        result.warnings.forEach { warning ->
            warning.environmentName shouldBe "dev"
            warning.message shouldContain warning.variableName
        }
    }

    test("warning for undeclared variables in global config") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "APP_NAME",
                    type = SchemaType.STRING,
                    scope = VariableScope.GLOBAL,
                    description = null
                )
            ),
            groups = emptyList()
        )

        val globalConfig = GlobalConfig(
            values = mapOf(
                "APP_NAME" to "TestApp",
                "UNDECLARED_GLOBAL" to "value"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.global.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to EnvironmentConfig("dev", emptyMap(), EnvFileFormat.DOT_ENV, "env.dev.env")),
            globalConfig = globalConfig
        )

        result.isValid.shouldBeTrue()
        val globalWarnings = result.warnings.filter { it.environmentName == null }
        globalWarnings shouldHaveSize 1
        globalWarnings.first().variableName shouldBe "UNDECLARED_GLOBAL"
    }

    test("no warning for declared variables including group variables") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable(
                    name = "API_HOST",
                    type = SchemaType.URL,
                    scope = VariableScope.ENVIRONMENT,
                    description = null
                )
            ),
            groups = listOf(
                SchemaGroup(
                    name = "database",
                    variables = listOf(
                        SchemaVariable(
                            name = "DB_HOST",
                            type = SchemaType.STRING,
                            scope = VariableScope.ENVIRONMENT,
                            description = null
                        )
                    )
                )
            )
        )

        val config = EnvironmentConfig(
            name = "dev",
            values = mapOf(
                "API_HOST" to "http://localhost",
                "DB_HOST" to "localhost"
            ),
            format = EnvFileFormat.DOT_ENV,
            sourceFile = "env.dev.env"
        )

        val result = validator.validate(
            schema = schema,
            configs = mapOf("dev" to config),
            globalConfig = null
        )

        result.isValid.shouldBeTrue()
        result.warnings.shouldBeEmpty()
    }
})
