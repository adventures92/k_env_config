# KEnv Config

Type-safe, schema-driven environment configuration for Kotlin Multiplatform and Android.

KEnv Config is a Gradle plugin that generates Kotlin objects from YAML schema files and environment-specific value files. It catches missing values at compile time, supports multiple environments, and provides IDE documentation through KDoc generation.

## Installation

### Version Catalog (TOML) — Recommended

```toml
# gradle/libs.versions.toml
[versions]
kenvConfig = "<latest>"

[plugins]
kenvConfig = { id = "io.github.adventures92.kenv-config", version.ref = "kenvConfig" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kenvConfig)
}
```

### Direct Application

```kotlin
// build.gradle.kts
plugins {
    id("io.github.adventures92.kenv-config") version "<latest>"
}
```

## Quick Start

```kotlin
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

- [Getting Started](getting-started.md) — Full setup walkthrough
- [Schema Reference](schema-reference.md) — Types, scopes, groups, env file formats
- [Plugin Configuration](plugin-configuration.md) — DSL options, tasks, error messages
- [Android Variant Mapping](variant-mapping.md) — Per-build-type code generation
- [Runtime Environment Selection](runtime-selection.md) — KMP environment switching
- [Code Generation](code-generation.md) — Generated code structure and behavior

## Features

- **Type-safe** — Generated Kotlin objects with correct types (String, Int, Long, Double, Float, Boolean, Url)
- **Compile-time validation** — Missing values are caught during build, not at runtime
- **Multi-environment** — Dev, staging, production — as many as you need
- **KMP compatible** — Works in commonMain with runtime environment selection
- **Android variant mapping** — Automatic per-build-type code generation
- **KDoc generation** — Schema descriptions become IDE-visible documentation
- **Groups** — Organize variables into logical nested objects
- **Unified directory** — Single `kenv/` directory for all config files
- **Multi-format** — Environment files in `.env`, `.yaml`, `.yml`, or `.toml`
- **Incremental builds** — Cacheable task, skipped when inputs unchanged
