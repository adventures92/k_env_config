# KEnv Config

[![Maven Central](https://img.shields.io/maven-central/v/io.github.adventures92/kenv-config)](https://central.sonatype.com/artifact/io.github.adventures92/kenv-config)

Type-safe, schema-driven environment configuration for Kotlin Multiplatform and Android.

KEnv Config is a Gradle plugin that generates Kotlin objects from a YAML schema and environment files at compile time. Missing or mistyped values fail the build — not your users at runtime.

```kotlin
// Generated — fully type-safe, with IDE documentation
EnvConfig.setActiveEnvironment("production")
val url = EnvConfig.Server.API_BASE_URL  // "https://api.example.com"
val port = EnvConfig.Server.API_PORT     // 443
val debug = EnvConfig.DEBUG_MODE         // false
```

---

## Installation

### Option 1: Version Catalog (TOML) — Recommended

Add to your `gradle/libs.versions.toml`:

```toml
[versions]
kenvConfig = "<latest>"

[plugins]
kenvConfig = { id = "io.github.adventures92.kenv-config", version.ref = "kenvConfig" }
```

Then in your module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kenvConfig)
}
```

### Option 2: Direct Plugin Application

In your module's `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.adventures92.kenv-config") version "<latest>"
}
```

### Repository Setup

Make sure `mavenCentral()` and `gradlePluginPortal()` are in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

---

## Quick Start

### 1. Create the kenv directory

```
my-app/
├── kenv/
│   ├── schema.kenv.yaml
│   ├── env.dev.env
│   ├── env.production.env
│   └── env.global.env
├── src/
└── build.gradle.kts
```

### 2. Define your schema

`kenv/schema.kenv.yaml`:

```yaml
environments:
  - dev
  - production

variables:
  DEBUG_MODE:
    type: Boolean
    scope: environment
    description: "Enable debug logging"

groups:
  server:
    API_URL:
      type: Url
      scope: environment
      description: "Backend API base URL"
    API_PORT:
      type: Int
      scope: environment
      description: "Backend API port"
  app:
    APP_NAME:
      type: String
      scope: global
      description: "Application display name"
```

### 3. Create environment files

`kenv/env.dev.env`:
```dotenv
DEBUG_MODE=true
API_URL=http://localhost:8080
API_PORT=8080
```

`kenv/env.production.env`:
```dotenv
DEBUG_MODE=false
API_URL=https://api.myapp.com
API_PORT=443
```

`kenv/env.global.env`:
```dotenv
APP_NAME=My App
```

### 4. Configure the plugin

```kotlin
kenvConfig {
    directory.set(file("kenv"))
    environments.set(listOf("dev", "production"))
    generatedPackageName.set("com.example.config")
}
```

### 5. Use in your code

```kotlin
import com.example.config.EnvConfig

// Option A: Set active environment, then use flat access (KMP)
EnvConfig.setActiveEnvironment("dev")
println(EnvConfig.Server.API_URL)   // "http://localhost:8080"
println(EnvConfig.App.APP_NAME)     // "My App"

// Option B: Access per-environment objects directly
println(EnvConfig.Dev.Server.API_URL)         // "http://localhost:8080"
println(EnvConfig.Production.Server.API_URL)  // "https://api.myapp.com"
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Compile-time validation** | Missing or mistyped values fail the build with clear error messages |
| **7 types** | String, Int, Long, Double, Float, Boolean, Url |
| **Global + environment scopes** | Shared values vs per-environment values |
| **Groups** | Organize variables into nested Kotlin objects |
| **KDoc generation** | Schema `description` fields become IDE documentation |
| **Runtime environment selection** | `setActiveEnvironment()` for KMP projects |
| **Android variant mapping** | `buildType("debug") uses "dev"` for automatic per-variant codegen |
| **Unified directory** | Single `kenv/` directory for schema and all env files |
| **Multi-format** | Environment files in `.env`, `.yaml`, `.yml`, or `.toml` |
| **Incremental builds** | Cacheable Gradle task, skipped when inputs unchanged |
| **CI-friendly** | Switch environment via `-PactiveEnvironment=production` |

---

## Android Variant Mapping

For Android projects, map build types directly to environments — no runtime selection needed:

```kotlin
kenvConfig {
    directory.set(file("kenv"))
    environments.set(listOf("dev", "production"))
    generatedPackageName.set("com.example.config")

    variantMapping {
        buildType("debug") uses "dev"
        buildType("release") uses "production"
    }
}
```

The generated code is flat — just `EnvConfig.Server.API_URL` with the correct value baked in per build type.

---

## Documentation

Full documentation: [adventures92.github.io/kenv-config](https://adventures92.github.io/kenv-config/)

- [Getting Started](https://adventures92.github.io/kenv-config/getting-started.html)
- [Schema Reference](https://adventures92.github.io/kenv-config/schema-reference.html)
- [Plugin Configuration](https://adventures92.github.io/kenv-config/plugin-configuration.html)
- [Android Variant Mapping](https://adventures92.github.io/kenv-config/variant-mapping.html)
- [Runtime Environment Selection](https://adventures92.github.io/kenv-config/runtime-selection.html)
- [Code Generation](https://adventures92.github.io/kenv-config/code-generation.html)

---

## Example App

The `composeApp` module is a working Kotlin Multiplatform Compose app (Android + iOS) that demonstrates all plugin features including runtime environment switching via a dropdown.

```bash
# Run generation
./gradlew :composeApp:kenvGenerate

# Build Android
./gradlew :composeApp:assembleDebug
```

---

## Requirements

- Kotlin 2.0+
- Gradle 8.0+
- JDK 17+

## License

[Apache License 2.0](./LICENSE)
