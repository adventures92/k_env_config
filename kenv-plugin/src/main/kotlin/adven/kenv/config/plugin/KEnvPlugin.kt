package adven.kenv.config.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * KEnv Config Gradle plugin v2.
 *
 * Provides schema-based, type-safe environment variable management for Kotlin Multiplatform
 * and Android projects. Registers the `kenvConfig` DSL extension and a `kenvGenerate` task
 * that orchestrates schema parsing, environment file discovery, validation, and Kotlin code
 * generation using the unified directory convention.
 *
 * Usage:
 * ```kotlin
 * plugins {
 *     id("io.github.adventures92.kenv-config")
 * }
 *
 * kenvConfig {
 *     directory.set(file("kenv"))
 *     environments.set(listOf("dev", "staging", "production"))
 *     generatedPackageName.set("com.example.config")
 *
 *     // Optional: Android variant mapping
 *     variantMapping {
 *         buildType("debug") uses "dev"
 *         buildType("release") uses "production"
 *     }
 * }
 * ```
 */
class KEnvPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Register the DSL extension
        val extension = project.extensions.create("kenvConfig", KEnvExtension::class.java)

        // Set default directory convention: kenv/ relative to project directory
        extension.directory.convention(project.layout.projectDirectory.dir("kenv"))

        // Register the default code generation task (used when variant mapping is NOT configured)
        val generateTask = project.tasks.register("kenvGenerate", KEnvGenerateTask::class.java) { task ->
            task.kenvDirectory.set(extension.directory)
            task.environments.set(extension.environments)
            task.generatedClassName.set(extension.generatedClassName)
            task.generatedPackageName.set(extension.generatedPackageName)

            // Support activeEnvironment override via Gradle property (-PactiveEnvironment=...)
            task.activeEnvironment.set(
                project.providers.gradleProperty("activeEnvironment")
            )

            // Set default output directory
            task.outputDirectory.set(
                project.layout.buildDirectory.dir("generated/kenv/commonMain/kotlin")
            )

            task.group = "kenv"
            task.description = "Generates type-safe Kotlin configuration from schema and environment files"
        }

        // Wire generated sources and task dependencies after project evaluation
        project.afterEvaluate {
            // Validate package name is set
            if (!extension.generatedPackageName.isPresent) {
                throw GradleException("generatedPackageName is required. Set it in the kenvConfig block.")
            }

            val variantMapping = extension.variantMapping

            if (variantMapping.isConfigured) {
                // Validate variant mappings against declared environments
                validateVariantMappings(extension)

                // If Android plugin is detected, register per-variant tasks
                if (isAndroidProject(project)) {
                    wireVariantMapping(project, extension, variantMapping)
                } else {
                    // Variant mapping configured but no Android plugin — fall back to default task
                    wireSourceSets(project, generateTask)
                    wireTaskDependencies(project, generateTask)
                }
            } else {
                // No variant mapping — use default kenvGenerate task with -PactiveEnvironment support
                wireSourceSets(project, generateTask)
                wireTaskDependencies(project, generateTask)
            }
        }
    }

    /**
     * Validates that all environments referenced in variant mappings exist in the
     * declared environments list.
     */
    private fun validateVariantMappings(extension: KEnvExtension) {
        val environments = extension.environments.get()
        val variantMapping = extension.variantMapping

        for ((buildType, env) in variantMapping.buildTypeMappings) {
            if (env !in environments) {
                throw GradleException(
                    "Variant mapping error: build type '$buildType' maps to " +
                        "environment '$env' which is not in declared environments: $environments"
                )
            }
        }

        for ((flavor, env) in variantMapping.flavorMappings) {
            if (env !in environments) {
                throw GradleException(
                    "Variant mapping error: flavor '$flavor' maps to " +
                        "environment '$env' which is not in declared environments: $environments"
                )
            }
        }
    }

    /**
     * Checks whether the project has an Android application or library plugin applied.
     */
    private fun isAndroidProject(project: Project): Boolean {
        return project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
    }

    /**
     * Registers per-variant generate tasks when variant mapping is configured and
     * the Android plugin is detected. Each mapped build type/flavor gets its own
     * task with `activeEnvironment` set to the mapped environment, outputting to
     * a variant-specific source set directory.
     */
    private fun wireVariantMapping(
        project: Project,
        extension: KEnvExtension,
        variantMapping: VariantMappingDsl
    ) {
        // Register per-build-type tasks
        for ((buildType, env) in variantMapping.buildTypeMappings) {
            val taskName = "kenvGenerate${buildType.replaceFirstChar { it.uppercase() }}"
            val variantOutputDir = project.layout.buildDirectory.dir("generated/kenv/$buildType/kotlin")

            val variantTask = project.tasks.register(taskName, KEnvGenerateTask::class.java) { task ->
                task.kenvDirectory.set(extension.directory)
                task.environments.set(extension.environments)
                task.generatedClassName.set(extension.generatedClassName)
                task.generatedPackageName.set(extension.generatedPackageName)
                task.activeEnvironment.set(env)
                task.outputDirectory.set(variantOutputDir)

                task.group = "kenv"
                task.description = "Generates KEnv config for build type '$buildType' using environment '$env'"
            }

            // Wire into the variant's source set and compile task
            wireAndroidVariantSourceSet(project, buildType, variantTask)
        }

        // Register per-flavor tasks
        for ((flavor, env) in variantMapping.flavorMappings) {
            val taskName = "kenvGenerate${flavor.replaceFirstChar { it.uppercase() }}"
            val variantOutputDir = project.layout.buildDirectory.dir("generated/kenv/$flavor/kotlin")

            val variantTask = project.tasks.register(taskName, KEnvGenerateTask::class.java) { task ->
                task.kenvDirectory.set(extension.directory)
                task.environments.set(extension.environments)
                task.generatedClassName.set(extension.generatedClassName)
                task.generatedPackageName.set(extension.generatedPackageName)
                task.activeEnvironment.set(env)
                task.outputDirectory.set(variantOutputDir)

                task.group = "kenv"
                task.description = "Generates KEnv config for flavor '$flavor' using environment '$env'"
            }

            // Wire into the flavor's source set and compile task
            wireAndroidVariantSourceSet(project, flavor, variantTask)
        }
    }

    /**
     * Wires a variant-specific generate task into the Android variant's source set
     * and compile task dependency chain.
     */
    private fun wireAndroidVariantSourceSet(
        project: Project,
        variantName: String,
        variantTask: org.gradle.api.tasks.TaskProvider<KEnvGenerateTask>
    ) {
        // Wire the generated output directory into the variant's kotlin source set
        val androidExtension = project.extensions.findByName("android") ?: return

        try {
            // Add generated sources to the variant's source set
            val sourceSetsMethod = androidExtension.javaClass.getMethod("getSourceSets")
            val sourceSets = sourceSetsMethod.invoke(androidExtension)
            val getByNameMethod = sourceSets.javaClass.getMethod("getByName", String::class.java)
            val variantSourceSet = getByNameMethod.invoke(sourceSets, variantName)
            val kotlinProperty = variantSourceSet.javaClass.getMethod("getKotlin")
            val kotlinSourceSet = kotlinProperty.invoke(variantSourceSet)
            val srcDirMethod = kotlinSourceSet.javaClass.getMethod("srcDir", Any::class.java)
            srcDirMethod.invoke(kotlinSourceSet, variantTask.flatMap { it.outputDirectory })
        } catch (_: Exception) {
            project.logger.warn("KEnv: Could not wire generated sources into Android source set for variant '$variantName'.")
        }

        // Wire task dependency: compile task depends on generate task
        val compileTaskName = "compileKotlin${variantName.replaceFirstChar { it.uppercase() }}"
        project.tasks.matching { it.name.contains(compileTaskName, ignoreCase = true) }.configureEach {
            it.dependsOn(variantTask)
        }
    }

    /**
     * Wires the generated source directory into the appropriate source set.
     * For KMP projects, adds to commonMain. For JVM-only projects, adds to main.
     */
    private fun wireSourceSets(
        project: Project,
        generateTask: org.gradle.api.tasks.TaskProvider<KEnvGenerateTask>
    ) {
        // Try KMP source sets first (commonMain)
        val kotlinExtension = project.extensions.findByName("kotlin")
        if (kotlinExtension != null) {
            try {
                val sourceSetsMethod = kotlinExtension.javaClass.getMethod("getSourceSets")
                val sourceSets = sourceSetsMethod.invoke(kotlinExtension)
                val getByNameMethod = sourceSets.javaClass.getMethod("getByName", String::class.java)
                val commonMain = getByNameMethod.invoke(sourceSets, "commonMain")
                val kotlinProperty = commonMain.javaClass.getMethod("getKotlin")
                val kotlinSourceSet = kotlinProperty.invoke(commonMain)
                val srcDirMethod = kotlinSourceSet.javaClass.getMethod("srcDir", Any::class.java)
                srcDirMethod.invoke(kotlinSourceSet, generateTask.flatMap { it.outputDirectory })
                return
            } catch (_: Exception) {
                // Fall through to try JVM source sets
            }
        }

        // Try JVM source sets (main)
        val javaConvention = project.extensions.findByName("sourceSets")
        if (javaConvention != null) {
            try {
                val getByNameMethod = javaConvention.javaClass.getMethod("getByName", String::class.java)
                val mainSourceSet = getByNameMethod.invoke(javaConvention, "main")
                val javaProperty = mainSourceSet.javaClass.getMethod("getJava")
                val javaSourceSet = javaProperty.invoke(mainSourceSet)
                val srcDirMethod = javaSourceSet.javaClass.getMethod("srcDir", Any::class.java)
                srcDirMethod.invoke(javaSourceSet, generateTask.flatMap { it.outputDirectory })
            } catch (_: Exception) {
                project.logger.warn("KEnv: Could not wire generated sources into source sets. Add the output directory manually.")
            }
        }
    }

    /**
     * Configures the kenvGenerate task to run before Kotlin compilation tasks.
     */
    private fun wireTaskDependencies(
        project: Project,
        generateTask: org.gradle.api.tasks.TaskProvider<KEnvGenerateTask>
    ) {
        // Try to wire before compileKotlinMetadata (KMP projects)
        project.tasks.matching { it.name == "compileKotlinMetadata" }.configureEach {
            it.dependsOn(generateTask)
        }

        // Also wire before compileKotlin (JVM projects or fallback)
        project.tasks.matching { it.name == "compileKotlin" }.configureEach {
            it.dependsOn(generateTask)
        }

        // Wire before all compileKotlin* tasks for multi-target KMP
        project.tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
            it.dependsOn(generateTask)
        }
    }
}
