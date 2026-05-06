package adven.kenv.config.schema

/**
 * Supported type annotations for schema variables.
 * Each type maps to a corresponding Kotlin standard library type.
 */
enum class SchemaType {
    STRING, INT, LONG, DOUBLE, FLOAT, BOOLEAN, URL;

    /**
     * Returns the Kotlin type name corresponding to this schema type.
     */
    fun toKotlinType(): String = when (this) {
        STRING -> "String"
        INT -> "Int"
        LONG -> "Long"
        DOUBLE -> "Double"
        FLOAT -> "Float"
        BOOLEAN -> "Boolean"
        URL -> "String"
    }
}
