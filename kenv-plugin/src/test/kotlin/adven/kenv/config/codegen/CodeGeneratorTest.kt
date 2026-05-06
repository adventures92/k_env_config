package adven.kenv.config.codegen

import adven.kenv.config.env.EnvFileFormat
import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class CodeGeneratorTest : FunSpec({

    val generator = DefaultCodeGenerator("com.test.config")

    val baseSchema = Schema(
        environments = listOf("dev", "production"),
        variables = listOf(
            SchemaVariable(
                name = "APP_NAME",
                type = SchemaType.STRING,
                scope = VariableScope.GLOBAL,
                description = null
            ),
            SchemaVariable(
                name = "API_HOST",
                type = SchemaType.URL,
                scope = VariableScope.ENVIRONMENT,
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

    val devConfig = EnvironmentConfig(
        name = "dev",
        values = mapOf(
            "API_HOST" to "http://localhost:8080",
            "DEBUG" to "true",
            "DB_HOST" to "localhost"
        ),
        format = EnvFileFormat.DOT_ENV,
        sourceFile = "env.dev.env"
    )

    val prodConfig = EnvironmentConfig(
        name = "production",
        values = mapOf(
            "API_HOST" to "https://api.example.com",
            "DEBUG" to "false",
            "DB_HOST" to "db.example.com"
        ),
        format = EnvFileFormat.DOT_ENV,
        sourceFile = "env.production.env"
    )

    val configs = mapOf("dev" to devConfig, "production" to prodConfig)

    val globalConfig = GlobalConfig(
        values = mapOf(
            "APP_NAME" to "MyApp",
            "DB_PORT" to "5432"
        ),
        format = EnvFileFormat.DOT_ENV,
        sourceFile = "env.global.env"
    )

    test("output formatting - uses 4-space indentation for nested content") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // Top-level properties should be indented with 4 spaces
        output shouldContain "    val APP_NAME: String"

        // Nested environment objects should be indented with 4 spaces
        output shouldContain "    object Dev {"

        // Properties inside environment objects should be indented with 8 spaces
        output shouldContain "        val API_HOST: String"
    }

    test("type mapping - String type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_STRING", SchemaType.STRING, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_STRING" to "hello"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_STRING: String = \"hello\""
    }

    test("type mapping - Int type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_INT", SchemaType.INT, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_INT" to "42"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_INT: Int = 42"
    }

    test("type mapping - Long type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_LONG", SchemaType.LONG, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_LONG" to "123456789"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_LONG: Long = 123456789L"
    }

    test("type mapping - Double type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_DOUBLE", SchemaType.DOUBLE, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_DOUBLE" to "3.14"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_DOUBLE: Double = 3.14"
    }

    test("type mapping - Float type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_FLOAT", SchemaType.FLOAT, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_FLOAT" to "2.5"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_FLOAT: Float = 2.5f"
    }

    test("type mapping - Boolean type") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_BOOL", SchemaType.BOOLEAN, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_BOOL" to "true"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_BOOL: Boolean = true"
    }

    test("type mapping - URL type maps to String") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_URL", SchemaType.URL, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_URL" to "https://example.com"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_URL: String = \"https://example.com\""
    }

    test("group nesting - groups produce nested objects") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // Group "database" should produce "object Database"
        output shouldContain "object Database {"
        // Group variables should be inside the group object
        output shouldContain "val DB_PORT: Int"
        output shouldContain "val DB_HOST: String"
    }

    test("group nesting - group object is nested inside environment objects in multi-env mode") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // In multi-environment mode, groups appear inside each environment object
        // The Database object should appear within Dev and Production objects
        val devSection = output.substringAfter("object Dev {").substringBefore("    }")
        devSection shouldContain "object Database {"
        devSection shouldContain "val DB_HOST: String"
    }

    test("active environment - produces flat object without nested environment objects") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = "dev",
            className = "EnvConfig"
        )

        // Should have top-level object
        output shouldContain "object EnvConfig {"

        // Should NOT have nested environment objects
        output shouldNotContain "object Dev {"
        output shouldNotContain "object Production {"

        // Should contain the active environment's values
        output shouldContain "val API_HOST: String = \"http://localhost:8080\""
        output shouldContain "val DEBUG: Boolean = true"
    }

    test("active environment - includes global values") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = "dev",
            className = "EnvConfig"
        )

        // Global variables should still appear with their values
        output shouldContain "val APP_NAME: String = \"MyApp\""
    }

    test("multi-environment - produces nested objects per environment") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // Should have top-level object
        output shouldContain "object EnvConfig {"

        // Should have nested environment objects
        output shouldContain "object Dev {"
        output shouldContain "object Production {"

        // Global variables should be at top level (not inside environment objects)
        // They appear before the first environment object
        val beforeDev = output.substringBefore("object Dev {")
        beforeDev shouldContain "val APP_NAME: String = \"MyApp\""
    }

    test("multi-environment - environment-scoped variables have per-environment values") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // Dev environment should have its values
        val devSection = output.substringAfter("object Dev {")
        devSection shouldContain "\"http://localhost:8080\""

        // Production environment should have its values
        val prodSection = output.substringAfter("object Production {")
        prodSection shouldContain "\"https://api.example.com\""
    }

    test("global config value is used for global-scoped variables") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // APP_NAME should use the global config value
        output shouldContain "val APP_NAME: String = \"MyApp\""
        // DB_PORT should use the global config value
        output shouldContain "val DB_PORT: Int = 5432"
    }

    test("package declaration - includes package when specified") {
        val generatorWithPackage = DefaultCodeGenerator(packageName = "com.example.config")

        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_VAR", SchemaType.STRING, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_VAR" to "value"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generatorWithPackage.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "package com.example.config"
    }

    test("string escaping - special characters are escaped in string values") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_STRING", SchemaType.STRING, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig(
            "dev",
            mapOf("MY_STRING" to "hello \"world\""),
            EnvFileFormat.DOT_ENV,
            "env.dev.env"
        )

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "val MY_STRING: String = \"hello \\\"world\\\"\""
    }

    test("active environment - groups are at top level, not nested in environment objects") {
        val output = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = "dev",
            className = "EnvConfig"
        )

        // In active environment mode, groups should be directly inside the top-level object
        output shouldContain "object Database {"
        output shouldContain "val DB_HOST: String = \"localhost\""
        output shouldContain "val DB_PORT: Int = 5432"
    }

    test("KDoc - variable with description has KDoc comment before property") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("API_HOST", SchemaType.STRING, VariableScope.ENVIRONMENT, "The API host URL")
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("API_HOST" to "http://localhost"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "/** The API host URL */"
        output shouldContain "val API_HOST: String = \"http://localhost\""
    }

    test("KDoc - variable without description has no KDoc comment") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("API_HOST", SchemaType.STRING, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("API_HOST" to "http://localhost"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, "dev", "EnvConfig")

        // In active-environment (flat) mode, no KDoc should appear for variables without description
        val lines = output.lines()
        val propLineIndex = lines.indexOfFirst { it.contains("val API_HOST: String") }
        propLineIndex shouldNotBe -1
        // The line before the property should NOT be a KDoc comment
        if (propLineIndex > 0) {
            val prevLine = lines[propLineIndex - 1].trim()
            (prevLine.startsWith("/**") && prevLine.endsWith("*/")) shouldBe false
        }
        output shouldContain "val API_HOST: String = \"http://localhost\""
    }

    test("KDoc - escapes */ to &#42;/ in description") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_VAR", SchemaType.STRING, VariableScope.ENVIRONMENT, "end of comment */")
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_VAR" to "value"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "/** end of comment &#42;/ */"
        output shouldNotContain "/** end of comment */ */"
    }

    test("KDoc - escapes @ to &#64; in description") {
        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_VAR", SchemaType.STRING, VariableScope.ENVIRONMENT, "see @param for details")
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_VAR" to "value"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generator.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        output shouldContain "/** see &#64;param for details */"
        output shouldNotContain "/** see @param for details */"
    }

    test("package declaration - generated code always starts with package declaration") {
        val generatorWithPackage = DefaultCodeGenerator(packageName = "com.example.myapp")

        val schema = Schema(
            environments = listOf("dev"),
            variables = listOf(
                SchemaVariable("MY_VAR", SchemaType.STRING, VariableScope.ENVIRONMENT, null)
            ),
            groups = emptyList()
        )
        val config = EnvironmentConfig("dev", mapOf("MY_VAR" to "value"), EnvFileFormat.DOT_ENV, "env.dev.env")

        val output = generatorWithPackage.generate(schema, mapOf("dev" to config), null, null, "EnvConfig")

        // First non-empty line should be the package declaration
        val firstNonEmptyLine = output.lines().first { it.isNotBlank() }
        firstNonEmptyLine shouldContain "package com.example.myapp"
    }

    test("package declaration - is always present regardless of generation mode") {
        val activeEnvOutput = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = "dev",
            className = "EnvConfig"
        )

        val multiEnvOutput = generator.generate(
            schema = baseSchema,
            configs = configs,
            globalConfig = globalConfig,
            activeEnvironment = null,
            className = "EnvConfig"
        )

        // Both modes should start with the package declaration
        activeEnvOutput shouldContain "package com.test.config"
        multiEnvOutput shouldContain "package com.test.config"

        val activeFirstLine = activeEnvOutput.lines().first { it.isNotBlank() }
        activeFirstLine shouldContain "package com.test.config"

        val multiFirstLine = multiEnvOutput.lines().first { it.isNotBlank() }
        multiFirstLine shouldContain "package com.test.config"
    }
})
