# Getting Started

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

Ensure `mavenCentral()` and `gradlePluginPortal()` are in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

## Project Setup

### 1. Create the kenv directory

Create a `kenv/` directory in your module root:

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

Create `kenv/schema.kenv.yaml`:

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

In your `build.gradle.kts`:

```kotlin
kenvConfig {
    directory.set(file("kenv"))
    environments.set(listOf("dev", "production"))
    generatedPackageName.set("com.example.config")
}
```

### 5. Generate and use

Run the generation task:

```bash
./gradlew kenvGenerate
```

Then use the generated code:

```kotlin
import com.example.config.EnvConfig

// Set active environment (KMP approach)
EnvConfig.setActiveEnvironment("dev")

// Access values with flat syntax
println(EnvConfig.Server.API_URL)       // "http://localhost:8080"
println(EnvConfig.Server.API_PORT)      // 8080
println(EnvConfig.DEBUG_MODE)           // true
println(EnvConfig.App.APP_NAME)         // "My App"

// Or access per-environment directly
println(EnvConfig.Production.Server.API_URL)  // "https://api.myapp.com"
```

## Next Steps

- [Schema Reference](schema-reference.md) — All supported types and fields
- [Plugin Configuration](plugin-configuration.md) — Full DSL options
- [Android Variant Mapping](variant-mapping.md) — Automatic per-build-type generation
- [Runtime Environment Selection](runtime-selection.md) — KMP environment switching
