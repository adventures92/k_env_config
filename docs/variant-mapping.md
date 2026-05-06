# Android Variant Mapping

Variant mapping automatically generates environment-specific configuration for each Android build type or product flavor. This eliminates the need for `-PactiveEnvironment` flags or runtime environment selection on Android.

## How It Works

1. You declare which build type maps to which environment
2. The plugin generates a **flat** `EnvConfig` object per variant
3. Each variant's generated code is wired into its source set
4. At compile time, the correct environment values are baked in

## Configuration

```kotlin
kenvConfig {
    directory.set(file("kenv"))
    environments.set(listOf("dev", "staging", "production"))
    generatedPackageName.set("com.example.config")

    variantMapping {
        buildType("debug") uses "dev"
        buildType("release") uses "production"
    }
}
```

### Build Type Mapping

```kotlin
variantMapping {
    buildType("debug") uses "dev"
    buildType("release") uses "production"
}
```

### Product Flavor Mapping

```kotlin
variantMapping {
    flavor("free") uses "dev"
    flavor("paid") uses "production"
}
```

### Combined

```kotlin
variantMapping {
    buildType("debug") uses "dev"
    buildType("release") uses "production"
    flavor("staging") uses "staging"
}
```

## Generated Output

With variant mapping, the generated code is a **flat object** — no nested environment objects:

```kotlin
// Generated for debug build type (using "dev" environment)
package com.example.config

object EnvConfig {
    val APP_VERSION: String = "2.0.0"
    val DEBUG_MODE: Boolean = true

    object Server {
        val API_BASE_URL: String = "http://localhost:8080"
        val API_PORT: Int = 8080
    }
}
```

## Usage in Code

With variant mapping, access is straightforward:

```kotlin
// No environment prefix needed — values are resolved at compile time
val url = EnvConfig.Server.API_BASE_URL
val port = EnvConfig.Server.API_PORT
val debug = EnvConfig.DEBUG_MODE
```

The same code compiles differently for debug vs release builds, with each getting the correct environment values.

## Source Set Wiring

For KMP Android projects, you may need to manually wire the generated source directory:

```kotlin
kotlin {
    sourceSets {
        androidMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kenv/debug/kotlin"))
        }
    }
}

// Wire task dependencies
tasks.matching { it.name == "compileDebugKotlinAndroid" }.configureEach {
    dependsOn("kenvGenerateDebug")
}
tasks.matching { it.name == "compileReleaseKotlinAndroid" }.configureEach {
    dependsOn("kenvGenerateRelease")
}
```

## Validation

The plugin validates that all mapped environments exist in the declared `environments` list:

```kotlin
// This will fail at configuration time:
kenvConfig {
    environments.set(listOf("dev", "production"))
    variantMapping {
        buildType("debug") uses "staging"  // Error: "staging" not in environments
    }
}
```

Error message:
```
Variant mapping error: build type 'debug' maps to environment 'staging'
which is not in declared environments: [dev, production]
```

## When to Use Variant Mapping vs Runtime Selection

| Approach | Best For |
|----------|----------|
| **Variant mapping** | Android-only projects where build types align with environments |
| **Runtime selection** | KMP projects, dynamic environment switching, server-driven config |
| **Direct access** | When you need to compare values across environments |

See [Runtime Environment Selection](runtime-selection.md) for the KMP approach.
