package adven.kenv.config.plugin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import org.gradle.testfixtures.ProjectBuilder

@OptIn(io.kotest.common.ExperimentalKotest::class)
class VariantMappingDslTest : FunSpec({

    /**
     * Generates a simple lowercase build type or flavor name (letters only, 3-10 chars).
     */
    fun arbBuildTypeName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generates a simple lowercase environment name (letters only, 3-10 chars).
     */
    fun arbEnvironmentName(): Arb<String> = arbitrary {
        val length = it.random.nextInt(3, 10)
        (1..length).map { ('a'..'z').random() }.joinToString("")
    }

    test("Property 8: Variant mapping stores and retrieves correctly") {
        /**
         * **Validates: Requirements 4.2, 4.3**
         *
         * For any set of build type names and environment names, configuring
         * `buildType(name) uses env` SHALL result in `buildTypeMappings[name] == env`.
         * Similarly for `flavor(name) uses env` and `flavorMappings`.
         */
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(Arb.pair(arbBuildTypeName(), arbEnvironmentName()), range = 1..5),
            Arb.list(Arb.pair(arbBuildTypeName(), arbEnvironmentName()), range = 1..5)
        ) { buildTypePairs, flavorPairs ->
            val project = ProjectBuilder.builder().build()
            val dsl = project.objects.newInstance(VariantMappingDsl::class.java)

            // Configure build type mappings
            val expectedBuildTypeMappings = mutableMapOf<String, String>()
            for ((name, env) in buildTypePairs) {
                dsl.buildType(name) uses env
                expectedBuildTypeMappings[name] = env
            }

            // Configure flavor mappings
            val expectedFlavorMappings = mutableMapOf<String, String>()
            for ((name, env) in flavorPairs) {
                dsl.flavor(name) uses env
                expectedFlavorMappings[name] = env
            }

            // Assert buildTypeMappings contains correct entries
            dsl.buildTypeMappings shouldContainExactly expectedBuildTypeMappings

            // Assert flavorMappings contains correct entries
            dsl.flavorMappings shouldContainExactly expectedFlavorMappings

            // Assert isConfigured is true after configuration
            dsl.isConfigured shouldBe true
        }
    }
})
