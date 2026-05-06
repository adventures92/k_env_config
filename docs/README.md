# KEnv Config

Type-safe, schema-driven environment configuration for Kotlin Multiplatform and Android projects.

KEnv Config is a Gradle plugin that generates Kotlin objects from YAML schema files and environment-specific value files. It catches missing values at compile time, supports multiple environments, and provides IDE documentation through KDoc generation.

## Quick Start

```kotlin
// build.gradle.kts
plugins {
    id("io.github.adventures92.kenv-config")
}

kenvConfig {
    directory.set(file("kenv"))
    environments.set(listOf("dev", "production"))
    generatedPackageName.set("com.example.config")
}
```

Then access your config in code:

```kotlin
// Flat access after setting active environment (KMP)
EnvConfig.setActiveEnvironment("production")
val apiUrl = EnvConfig.Server.API_BASE_URL

// Or direct per-environment access
val devUrl = EnvConfig.Dev.Server.API_BASE_URL
val prodUrl = EnvConfig.Production.Server.API_BASE_URL
```

## Documentation

- [Getting Started](getting-started.md)
- [Schema Reference](schema-reference.md)
- [Plugin Configuration](plugin-configuration.md)
- [Android Variant Mapping](variant-mapping.md)
- [Runtime Environment Selection](runtime-selection.md)
- [Code Generation](code-generation.md)
- [Migration Guide (v1 → v2)](migration-guide.md)

## Features

- **Type-safe** — Generated Kotlin objects with correct types (String, Int, Long, Double, Float, Boolean, Url)
- **Compile-time validation** — Missing values are caught during build, not at runtime
- **Multi-environment** — Dev, staging, production — as many as you need
- **KMP compatible** — Works in commonMain with runtime environment selection
- **Android variant mapping** — Automatic per-build-type code generation
- **KDoc generation** — Schema descriptions become IDE-visible documentation
- **Groups** — Organize variables into logical nested objects
- **Unified directory** — Single `kenv/` directory for all config files
