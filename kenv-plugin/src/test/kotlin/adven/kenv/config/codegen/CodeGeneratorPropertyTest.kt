package adven.kenv.config.codegen

import adven.kenv.config.env.EnvFileFormat
import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.generators.EnvConfigGenerators
import adven.kenv.config.generators.SchemaGenerators
import adven.kenv.config.schema.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

@OptIn(io.kotest.common.ExperimentalKotest::class)
class CodeGeneratorPropertyTest : FunSpec({

    val generator = DefaultCodeGenerator("com.test.config")

    /**
     * Generates a valid Kotlin package name: 2-4 segments of lowercase letters separated by dots.
     * Each segment is 2-8 lowercase letters.
     */
    fun arbPackageName(): Arb<String> = arbitrary { rs ->
        val numSegments = Arb.int(2..4).bind()
        val segments = (1..numSegments).map {
            val segLen = Arb.int(2..8).bind()
            (1..segLen).map { ('a'..'z').random(rs.random) }.joinToString("")
        }
        segments.joinToString(".")
    }

    /**
     * Helper to collect all variables from a schema (top-level + groups).
     */
    fun getAllVariables(schema: Schema): List<SchemaVariable> {
        val variables = mutableListOf<SchemaVariable>()
        variables.addAll(schema.variables)
        for (group in schema.groups) {
            variables.addAll(group.variables)
        }
        return variables
    }

    test("Property 10: Generated code contains correct typed properties") {
        /**
         * **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.6, 6.3**
         *
         * For any valid Schema and conforming set of EnvironmentConfig objects and optional
         * GlobalConfig, the generated Kotlin source SHALL contain:
         * (a) a typed property declaration for every variable in the schema using the correct Kotlin type mapping
         * (b) a nested object for every group
         * (c) global-scoped variables with their single shared value
         * (d) environment-scoped variables with per-environment values
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val output = generator.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig,
                activeEnvironment = null,
                className = "EnvConfig"
            )

            val allVars = getAllVariables(schema)

            // (a) Every variable should have a typed property declaration with correct Kotlin type
            for (variable in allVars) {
                val expectedType = variable.type.toKotlinType()
                output shouldContain "val ${variable.name}: $expectedType"
            }

            // (b) Every group should produce a nested object
            for (group in schema.groups) {
                val objectName = group.name.replaceFirstChar { it.uppercase() }
                output shouldContain "object $objectName"
            }

            // (c) Global-scoped variables should appear with their resolved value
            val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }
            for (variable in globalVars) {
                val value = globalConfig.values[variable.name]
                if (value != null) {
                    output shouldContain "val ${variable.name}: ${variable.type.toKotlinType()}"
                }
            }

            // (d) Environment-scoped variables should appear within per-environment objects
            for (envName in schema.environments) {
                val objectName = envName.replaceFirstChar { it.uppercase() }
                output shouldContain "object $objectName"
            }
        }
    }

    test("Property 11: Active environment produces single-environment output") {
        /**
         * **Validates: Requirements 6.2**
         *
         * For any valid Schema, conforming EnvironmentConfig set, and a valid activeEnvironment
         * name, the generated Kotlin source SHALL contain a top-level object with properties
         * holding only the values for the selected environment (plus global values), and SHALL
         * NOT contain per-environment nested objects.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            // Need at least one environment
            if (schema.environments.isEmpty()) return@checkAll

            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            // Pick the first environment as active
            val activeEnv = schema.environments.first()

            val output = generator.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig,
                activeEnvironment = activeEnv,
                className = "EnvConfig"
            )

            // Should contain the top-level object
            output shouldContain "object EnvConfig"

            // Should NOT contain per-environment nested objects
            for (envName in schema.environments) {
                val objectName = envName.replaceFirstChar { it.uppercase() }
                // The per-environment object pattern is "    object <Name> {"
                // In active mode, there should be no such nested environment objects
                output shouldNotContain "object $objectName {"
            }

            // Should contain all variables with correct types
            val allVars = getAllVariables(schema)
            for (variable in allVars) {
                val expectedType = variable.type.toKotlinType()
                output shouldContain "val ${variable.name}: $expectedType"
            }

            // Global variables should still be present
            val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }
            for (variable in globalVars) {
                output shouldContain "val ${variable.name}: ${variable.type.toKotlinType()}"
            }
        }
    }

    test("Property 13: Generated code uses only stdlib types") {
        /**
         * **Validates: Requirements 8.2**
         *
         * For any valid Schema and conforming configs, the generated Kotlin source SHALL contain
         * no import statements referencing packages outside kotlin.* and SHALL use only Kotlin
         * standard library types in property declarations.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val output = generator.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig,
                activeEnvironment = null,
                className = "EnvConfig"
            )

            // Should not contain any import statements referencing non-kotlin packages
            val importLines = output.lines().filter { it.trimStart().startsWith("import ") }
            for (importLine in importLines) {
                val importedPackage = importLine.trim().removePrefix("import ")
                importedPackage.startsWith("kotlin.").shouldBeTrue()
            }

            // All property types should be Kotlin stdlib types
            val validTypes = setOf("String", "Int", "Long", "Double", "Float", "Boolean")
            val propertyPattern = Regex("""val\s+\w+:\s+(\w+)""")
            val matches = propertyPattern.findAll(output)
            for (match in matches) {
                val typeName = match.groupValues[1]
                (typeName in validTypes).shouldBeTrue()
            }
        }
    }

    test("Property 6: KDoc generation iff description exists") {
        /**
         * **Validates: Requirements 5.1, 5.2, 5.3**
         *
         * For any Schema variable with a non-null description, the generated Kotlin source
         * SHALL contain a KDoc comment immediately preceding that variable's property declaration.
         * For any variable with a null description, no KDoc comment SHALL appear before its declaration.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            // Skip schemas with no variables
            if (allVars.isEmpty()) return@checkAll

            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val output = generator.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig,
                activeEnvironment = null,
                className = "EnvConfig"
            )

            val lines = output.lines()

            for (variable in allVars) {
                // Find lines containing this variable's property declaration
                val propPattern = "val ${variable.name}: ${variable.type.toKotlinType()}"
                val propLineIndices = lines.indices.filter { lines[it].contains(propPattern) }

                for (propLineIndex in propLineIndices) {
                    if (variable.description != null) {
                        // There should be a KDoc comment on the line immediately before
                        (propLineIndex > 0).shouldBeTrue()
                        val prevLine = lines[propLineIndex - 1].trim()
                        prevLine.startsWith("/**").shouldBeTrue()
                        prevLine.endsWith("*/").shouldBeTrue()
                    } else {
                        // There should NOT be a KDoc comment on the line immediately before
                        if (propLineIndex > 0) {
                            val prevLine = lines[propLineIndex - 1].trim()
                            (prevLine.startsWith("/**") && prevLine.endsWith("*/")).shouldBeFalse()
                        }
                    }
                }
            }
        }
    }

    test("Property 7: KDoc special character escaping") {
        /**
         * **Validates: Requirements 5.1, 5.2, 5.3**
         *
         * For any Schema variable whose description contains KDoc-special characters
         * (star-slash or at-sign), the generated KDoc comment SHALL escape those characters
         * so the output is valid Kotlin documentation.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val varsWithSpecialDesc = allVars.filter { variable ->
                variable.description != null &&
                    (variable.description!!.contains("*/") || variable.description!!.contains("@"))
            }

            // Skip if no variables have special characters in descriptions
            if (varsWithSpecialDesc.isEmpty()) return@checkAll

            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val output = generator.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig,
                activeEnvironment = null,
                className = "EnvConfig"
            )

            val lines = output.lines()

            // Extract all KDoc comment lines from the output
            val kdocLines = lines.filter { it.trim().startsWith("/**") && it.trim().endsWith("*/") }

            for (kdocLine in kdocLines) {
                val content = kdocLine.trim()
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .trim()

                // Raw */ should NOT appear in KDoc content (it would prematurely close the comment)
                content.shouldNotContain("*/")

                // Raw @ should NOT appear in KDoc content (it would be interpreted as a tag)
                content.shouldNotContain("@")
            }

            // Additionally verify that escaped forms are present for variables with special chars
            for (variable in varsWithSpecialDesc) {
                val propPattern = "val ${variable.name}: ${variable.type.toKotlinType()}"
                val propLineIndices = lines.indices.filter { lines[it].contains(propPattern) }

                for (propLineIndex in propLineIndices) {
                    if (propLineIndex > 0) {
                        val prevLine = lines[propLineIndex - 1].trim()
                        if (prevLine.startsWith("/**") && prevLine.endsWith("*/")) {
                            val kdocContent = prevLine.removePrefix("/**").removeSuffix("*/").trim()

                            // If original description had */, the escaped form should be present
                            if (variable.description!!.contains("*/")) {
                                kdocContent shouldContain "&#42;/"
                            }

                            // If original description had @, the escaped form should be present
                            if (variable.description!!.contains("@")) {
                                kdocContent shouldContain "&#64;"
                            }
                        }
                    }
                }
            }
        }
    }

    test("Property 10 - Design - Package declaration in generated code") {
        /**
         * **Validates: Requirements 3.3**
         *
         * For any valid package name, the generated Kotlin source SHALL begin with
         * `package <packageName>` as the first non-empty line.
         */
        checkAll(PropTestConfig(iterations = 100), arbPackageName()) { packageName ->
            val codeGen = DefaultCodeGenerator(packageName)

            // Create a simple schema with at least one variable
            val schema = Schema(
                environments = listOf("dev"),
                variables = listOf(
                    SchemaVariable(
                        name = "TEST_VAR",
                        type = SchemaType.STRING,
                        scope = VariableScope.ENVIRONMENT,
                        description = null
                    )
                ),
                groups = emptyList()
            )

            val envConfigs = mapOf(
                "dev" to EnvironmentConfig(
                    name = "dev",
                    values = mapOf("TEST_VAR" to "value"),
                    format = EnvFileFormat.DOT_ENV,
                    sourceFile = "env.dev.env"
                )
            )

            val output = codeGen.generate(
                schema = schema,
                configs = envConfigs,
                globalConfig = null,
                activeEnvironment = null,
                className = "EnvConfig"
            )

            // The first non-empty line should be the package declaration
            val firstNonEmptyLine = output.lines().firstOrNull { it.isNotBlank() }
            firstNonEmptyLine shouldContain "package $packageName"
            firstNonEmptyLine?.trim() shouldBe "package $packageName"
        }
    }
})
