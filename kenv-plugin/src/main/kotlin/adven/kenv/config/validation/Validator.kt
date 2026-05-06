package adven.kenv.config.validation

import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.Schema

/**
 * Validates environment configurations against a schema definition.
 * Collects all errors and warnings in a single pass before returning.
 */
interface Validator {
    /**
     * Validates environment configs and optional global config against the schema.
     *
     * @param schema The parsed schema definition
     * @param configs Map of environment name to its parsed config
     * @param globalConfig Optional global values file config
     * @param activeEnvironment Optional active environment name to validate
     * @return ValidationResult containing all errors and warnings
     */
    fun validate(
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        activeEnvironment: String? = null
    ): ValidationResult
}
