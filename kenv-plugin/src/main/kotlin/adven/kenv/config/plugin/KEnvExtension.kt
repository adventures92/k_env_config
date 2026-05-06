package adven.kenv.config.plugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Gradle DSL extension for configuring the KEnv plugin v2.
 *
 * Usage in `build.gradle.kts`:
 * ```kotlin
 * kenvConfig {
 *     directory.set(file("kenv"))
 *     environments.set(listOf("dev", "staging", "production"))
 *     generatedPackageName.set("com.example.config")
 * }
 * ```
 */
abstract class KEnvExtension @Inject constructor(objects: ObjectFactory) {

    /** Single directory containing schema.kenv.yaml and all env files. Default: "kenv/" */
    abstract val directory: DirectoryProperty

    /** List of environment names to process. */
    abstract val environments: ListProperty<String>

    /** Required: package name for the generated Kotlin source file. */
    abstract val generatedPackageName: Property<String>

    /** Name of the generated Kotlin object class. Default: "EnvConfig". */
    val generatedClassName: Property<String> = objects.property(String::class.java).convention("EnvConfig")

    /** Android variant mapping configuration block. */
    val variantMapping: VariantMappingDsl = objects.newInstance(VariantMappingDsl::class.java)

    fun variantMapping(action: VariantMappingDsl.() -> Unit) {
        action(variantMapping)
    }
}
