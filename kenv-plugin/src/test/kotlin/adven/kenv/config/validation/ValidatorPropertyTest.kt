package adven.kenv.config.validation

import adven.kenv.config.generators.EnvConfigGenerators
import adven.kenv.config.generators.SchemaGenerators
import adven.kenv.config.schema.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

@OptIn(io.kotest.common.ExperimentalKotest::class)
class ValidatorPropertyTest : FunSpec({

    val validator = DefaultValidator()

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

    test("Property 4: Missing environment-scoped variable detection") {
        /**
         * **Validates: Requirements 2.2, 2.5**
         *
         * For any Schema and EnvironmentConfig pair, the validator SHALL emit a MissingVariable
         * error for a variable if and only if that variable has scope: environment AND is absent
         * from the config's values. No default fallback — all variables are strictly required.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }

            // Skip schemas with no environment-scoped variables
            if (envVars.isEmpty()) return@checkAll

            // Generate an incomplete config for one of the schema's environments
            val envName = schema.environments.first()
            val incompleteConfig = EnvConfigGenerators.arbIncompleteConfig(schema).bind()
            val configWithEnvName = incompleteConfig.copy(name = envName)

            // Provide a conforming global config so global vars don't interfere
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val result = validator.validate(
                schema = schema,
                configs = mapOf(envName to configWithEnvName),
                globalConfig = globalConfig
            )

            // Check: for each env var, MissingVariable error iff absent from config
            for (variable in envVars) {
                val isMissing = variable.name !in configWithEnvName.values
                val hasMissingError = result.errors.any { error ->
                    error is ValidationError.MissingVariable &&
                        error.variableName == variable.name &&
                        error.environmentName == envName
                }
                hasMissingError shouldBe isMissing
            }
        }
    }

    test("Property 5: Missing global-scoped variable detection") {
        /**
         * **Validates: Requirements 2.2, 2.4**
         *
         * For any Schema and GlobalConfig pair, the validator SHALL emit a MissingGlobalVariable
         * error for a variable if and only if that variable has scope: global AND is absent from
         * the global config's values. No default fallback — all variables are strictly required.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }

            // Skip schemas with no global-scoped variables
            if (globalVars.isEmpty()) return@checkAll

            // Generate an incomplete global config
            val incompleteGlobal = EnvConfigGenerators.arbIncompleteGlobalConfig(schema).bind()

            // Provide conforming env configs so env vars don't interfere
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            val result = validator.validate(
                schema = schema,
                configs = envConfigs,
                globalConfig = incompleteGlobal
            )

            // Check: for each global var, MissingGlobalVariable error iff absent
            for (variable in globalVars) {
                val isMissing = variable.name !in incompleteGlobal.values
                val hasMissingError = result.errors.any { error ->
                    error is ValidationError.MissingGlobalVariable &&
                        error.variableName == variable.name
                }
                hasMissingError shouldBe isMissing
            }
        }
    }

    test("Property 6: Type mismatch detection") {
        /**
         * **Validates: Requirements 3.2**
         *
         * For any Schema variable with a declared type and for any value that does not parse
         * as that type, the validator SHALL emit a TypeMismatch error.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }
            val mismatchableVars = envVars.filter {
                it.type != SchemaType.STRING && it.type != SchemaType.URL
            }

            // Skip schemas with no mismatchable environment variables
            if (mismatchableVars.isEmpty()) return@checkAll

            // Generate a mismatched config
            val envName = schema.environments.first()
            val mismatchedConfig = EnvConfigGenerators.arbMismatchedConfig(schema).bind()
                .copy(name = envName)

            // Provide a conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val result = validator.validate(
                schema = schema,
                configs = mapOf(envName to mismatchedConfig),
                globalConfig = globalConfig
            )

            // Check: for each env var with a value that doesn't parse, there should be a TypeMismatch
            for (variable in envVars) {
                val value = mismatchedConfig.values[variable.name] ?: continue
                val parsesCorrectly = canParse(value, variable.type)
                val hasTypeMismatchError = result.errors.any { error ->
                    error is ValidationError.TypeMismatch &&
                        error.variableName == variable.name &&
                        error.expectedType == variable.type &&
                        error.actualValue == value &&
                        error.environmentName == envName
                }
                hasTypeMismatchError shouldBe !parsesCorrectly
            }
        }
    }

    test("Property 7: Valid configurations pass validation") {
        /**
         * **Validates: Requirements 2.2, 2.3**
         *
         * For any Schema and set of EnvironmentConfig objects where every environment-scoped
         * variable is present in all environment files, every global-scoped variable is present
         * in the global config, and every value parses as its declared type, the validator SHALL
         * return isValid = true. All variables must be present — no default fallback.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            // Generate conforming configs for all environments
            val envConfigs = schema.environments.associateWith { envName ->
                EnvConfigGenerators.arbConformingConfig(schema).bind().copy(name = envName)
            }

            // Generate conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val result = validator.validate(
                schema = schema,
                configs = envConfigs,
                globalConfig = globalConfig
            )

            result.isValid.shouldBeTrue()
            result.errors.shouldBeEmpty()
        }
    }

    test("Property 8: Undeclared variable warnings") {
        /**
         * **Validates: Requirements 3.5**
         *
         * For any Schema and EnvironmentConfig pair, for every key in the config's values that
         * does not appear in the schema's variable declarations (including group variables),
         * the validator SHALL emit a warning.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val allVarNames = allVars.map { it.name }.toSet()

            // Generate a config with extra undeclared variables
            val envName = schema.environments.first()
            val extraConfig = EnvConfigGenerators.arbExtraVarsConfig(schema).bind()
                .copy(name = envName)

            // Provide a conforming global config
            val globalConfig = EnvConfigGenerators.arbConformingGlobalConfig(schema).bind()

            val result = validator.validate(
                schema = schema,
                configs = mapOf(envName to extraConfig),
                globalConfig = globalConfig
            )

            // Check: every undeclared key should produce a warning
            val undeclaredKeys = extraConfig.values.keys.filter { it !in allVarNames }
            for (key in undeclaredKeys) {
                val hasWarning = result.warnings.any { warning ->
                    warning.variableName == key && warning.environmentName == envName
                }
                hasWarning shouldBe true
            }

            // Check: no warning for declared keys
            val declaredKeys = extraConfig.values.keys.filter { it in allVarNames }
            for (key in declaredKeys) {
                val hasWarning = result.warnings.any { warning ->
                    warning.variableName == key && warning.environmentName == envName
                }
                hasWarning shouldBe false
            }
        }
    }

    test("Property 9: Exhaustive error reporting") {
        /**
         * **Validates: Requirements 2.3, 2.4, 2.5**
         *
         * For any Schema and set of EnvironmentConfig objects containing N total violations,
         * the validator SHALL report exactly N errors in a single validation pass.
         * All variables are strictly required — no default fallback.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            val allVars = getAllVariables(schema)
            val envVars = allVars.filter { it.scope == VariableScope.ENVIRONMENT }
            val globalVars = allVars.filter { it.scope == VariableScope.GLOBAL }

            // Use a single environment for simplicity
            val envName = schema.environments.first()

            // Generate a config that may have missing vars and type mismatches
            val incompleteConfig = EnvConfigGenerators.arbIncompleteConfig(schema).bind()
                .copy(name = envName)

            // Generate an incomplete global config
            val incompleteGlobal = EnvConfigGenerators.arbIncompleteGlobalConfig(schema).bind()

            val result = validator.validate(
                schema = schema,
                configs = mapOf(envName to incompleteConfig),
                globalConfig = incompleteGlobal
            )

            // Count expected violations manually
            var expectedErrors = 0

            // Missing or type-mismatched environment-scoped variables (strict, no default fallback)
            for (variable in envVars) {
                val value = incompleteConfig.values[variable.name]
                if (value == null) {
                    expectedErrors++
                } else if (!canParse(value, variable.type)) {
                    expectedErrors++
                }
            }

            // Missing or type-mismatched global-scoped variables (strict, no default fallback)
            for (variable in globalVars) {
                val globalValue = incompleteGlobal.values[variable.name]
                if (globalValue == null) {
                    expectedErrors++
                } else if (!canParse(globalValue, variable.type)) {
                    expectedErrors++
                }
            }

            result.errors.size shouldBe expectedErrors
        }
    }

    test("Property 12: Invalid active environment produces error") {
        /**
         * **Validates: Requirements 6.5**
         *
         * For any Schema with declared environments and for any environment name not in that set,
         * setting activeEnvironment to that name SHALL produce an InvalidEnvironment error.
         */
        checkAll(PropTestConfig(iterations = 100), SchemaGenerators.arbSchema()) { schema ->
            // Generate an environment name that is NOT in the schema's environments
            var invalidEnvName: String
            do {
                invalidEnvName = SchemaGenerators.arbEnvironmentName().bind()
            } while (invalidEnvName in schema.environments)

            val result = validator.validate(
                schema = schema,
                configs = emptyMap(),
                globalConfig = null,
                activeEnvironment = invalidEnvName
            )

            // Should contain an InvalidEnvironment error
            val invalidEnvErrors = result.errors.filterIsInstance<ValidationError.InvalidEnvironment>()
            invalidEnvErrors shouldHaveSize 1
            invalidEnvErrors.first().environmentName shouldBe invalidEnvName
            invalidEnvErrors.first().validEnvironments shouldBe schema.environments
        }
    }
})
