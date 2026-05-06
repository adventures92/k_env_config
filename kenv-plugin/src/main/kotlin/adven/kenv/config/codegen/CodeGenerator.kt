package adven.kenv.config.codegen

import adven.kenv.config.env.EnvironmentConfig
import adven.kenv.config.env.GlobalConfig
import adven.kenv.config.schema.Schema

/**
 * Generates Kotlin source code from validated schema and environment configurations.
 *
 * The generator produces a Kotlin object class with typed properties for each declared
 * environment variable. It supports two modes:
 * - Multi-environment: nested objects per environment (e.g., `EnvConfig.Dev`, `EnvConfig.Production`)
 * - Active environment: a single flat object with only the selected environment's values
 */
interface CodeGenerator {
    /**
     * Generates Kotlin source code for the given schema and configurations.
     *
     * @param schema The parsed schema definition
     * @param configs Map of environment name to its parsed configuration
     * @param globalConfig Optional global values configuration
     * @param activeEnvironment If set, generates code for only this environment
     * @param className The name of the generated object class (e.g., "EnvConfig")
     * @return The generated Kotlin source code as a string
     */
    fun generate(
        schema: Schema,
        configs: Map<String, EnvironmentConfig>,
        globalConfig: GlobalConfig?,
        activeEnvironment: String?,
        className: String
    ): String
}
