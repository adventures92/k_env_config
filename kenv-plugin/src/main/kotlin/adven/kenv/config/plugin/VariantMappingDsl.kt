package adven.kenv.config.plugin

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class VariantMappingDsl @Inject constructor(objects: ObjectFactory) {

    private val _buildTypeMappings = mutableMapOf<String, String>()
    private val _flavorMappings = mutableMapOf<String, String>()

    val buildTypeMappings: Map<String, String> get() = _buildTypeMappings.toMap()
    val flavorMappings: Map<String, String> get() = _flavorMappings.toMap()

    val isConfigured: Boolean get() = _buildTypeMappings.isNotEmpty() || _flavorMappings.isNotEmpty()

    fun buildType(name: String): MappingBuilder = MappingBuilder { env ->
        _buildTypeMappings[name] = env
    }

    fun flavor(name: String): MappingBuilder = MappingBuilder { env ->
        _flavorMappings[name] = env
    }

    class MappingBuilder(private val setter: (String) -> Unit) {
        infix fun uses(environment: String) {
            setter(environment)
        }
    }
}
