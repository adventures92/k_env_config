# Code Generation

This page describes the structure and behavior of the generated Kotlin code.

## Generated File Location

The generated file is placed at:
```
build/generated/kenv/commonMain/kotlin/<package_path>/<ClassName>.kt
```

For example, with `generatedPackageName.set("com.example.config")` and default class name:
```
build/generated/kenv/commonMain/kotlin/com/example/config/EnvConfig.kt
```

## Generated Structure

### Multi-Environment Mode (Default)

When no active environment is specified, the generated code includes:

1. **Runtime selection API** — `setActiveEnvironment()` and flat accessors
2. **Global properties** — Top-level vals for global-scoped variables
3. **Per-environment nested objects** — `EnvConfig.Dev`, `EnvConfig.Production`, etc.

```kotlin
package com.example.config

object EnvConfig {

    // --- Runtime selection ---
    private var _activeEnvironment: String? = null

    fun setActiveEnvironment(environment: String) { ... }
    val activeEnvironment: String? get() = _activeEnvironment

    // --- Global properties (always accessible) ---
    /** Application version */
    val APP_VERSION: String = "2.0.0"

    // --- Flat accessors (require setActiveEnvironment) ---
    /** Enable debug mode */
    val DEBUG_MODE: Boolean
        get() = when (_activeEnvironment) {
            "dev" -> true
            "production" -> false
            else -> throw IllegalStateException(...)
        }

    // --- Groups with flat accessors ---
    object Server {
        /** API base URL */
        val API_BASE_URL: String
            get() = when (_activeEnvironment) {
                "dev" -> "http://localhost:8080"
                "production" -> "https://api.example.com"
                else -> throw IllegalStateException(...)
            }
    }

    // --- Per-environment objects (direct access) ---
    object Dev {
        val DEBUG_MODE: Boolean = true
        object Server {
            val API_BASE_URL: String = "http://localhost:8080"
        }
    }

    object Production {
        val DEBUG_MODE: Boolean = false
        object Server {
            val API_BASE_URL: String = "https://api.example.com"
        }
    }
}
```

### Active Environment Mode (Flat)

When using `-PactiveEnvironment=dev` or variant mapping, the generated code is a simple flat object:

```kotlin
package com.example.config

object EnvConfig {
    /** Application version */
    val APP_VERSION: String = "2.0.0"
    /** Enable debug mode */
    val DEBUG_MODE: Boolean = true

    object Server {
        /** API base URL */
        val API_BASE_URL: String = "http://localhost:8080"
    }
}
```

## KDoc Generation

Every variable with a `description` field in the schema gets a KDoc comment:

```yaml
# Schema
API_URL:
  type: Url
  scope: environment
  description: "Backend API base URL including protocol"
```

```kotlin
// Generated
/** Backend API base URL including protocol */
val API_URL: String = "https://api.example.com"
```

### Special Character Escaping

KDoc-special characters are automatically escaped:

| Character | Escaped To |
|-----------|-----------|
| `*/` | `&#42;/` |
| `@` | `&#64;` |

## Type Mapping

| Schema Type | Kotlin Type | Generated Literal |
|-------------|-------------|-------------------|
| `String` | `String` | `"value"` |
| `Int` | `Int` | `42` |
| `Long` | `Long` | `123456789L` |
| `Double` | `Double` | `3.14` |
| `Float` | `Float` | `2.5f` |
| `Boolean` | `Boolean` | `true` / `false` |
| `Url` | `String` | `"https://..."` |

## String Escaping

Special characters in string values are properly escaped:

| Character | Escaped To |
|-----------|-----------|
| `\` | `\\` |
| `"` | `\"` |
| `\n` | `\\n` |
| `\r` | `\\r` |
| `\t` | `\\t` |
| `$` | `\\$` |

## Package Declaration

The generated file always starts with a `package` declaration using the configured `generatedPackageName`. This is required — the build fails if no package name is set.

## Caching

The generation task is annotated with `@CacheableTask`. Gradle will skip re-generation when inputs (kenv directory contents) haven't changed, making incremental builds fast.
