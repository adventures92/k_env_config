# KEnv Config Plugin

Schema-based, type-safe environment variable management for Kotlin Multiplatform projects.

KEnv Config generates Kotlin object classes from a YAML schema and environment files at compile time, giving you type-safe access to configuration values across Android, iOS, JVM, and native targets — with zero runtime dependencies.

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Schema File Format](#schema-file-format)
- [Environment File Format](#environment-file-format)
- [Global Values File](#global-values-file)
- [Plugin Configuration (DSL Reference)](#plugin-configuration-dsl-reference)
- [Active Environment Selection](#active-environment-selection)
- [Generated Code](#generated-code)
- [Full Example](#full-example)
- [Error Messages](#error-messages)
- [Incremental Builds](#incremental-builds)
- [Troubleshooting](#troubleshooting)

---

## Installation

### Step 1: Include the plugin build

In your project's `settings.gradle.kts`, add the plugin as an included build:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("kenv-plugin")  // path to the kenv-plugin module
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

If the plugin is published to a Maven repository, you can instead add it via the plugin portal:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### Step 2: Apply the plugin

In your module's `build.gradle.kts`:

```kotlin
plugins {
    // your other plugins...
    id("io.github.adventures92.kenv-config")
}
```

### Step 3: Configure the plugin

```kotlin
kenvConfig {
    schemaFile.set(file("schema.kenv.yaml"))
    envDirectory.set(file("env"))
    environments.set(listOf("dev", "production"))
}
```

That's it. The plugin registers a `kenvGenerate` task that runs automatically before compilation.

---

## Quick Start

### 1. Apply the plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.adventures92.kenv-config")
}
```

### 2. Create a schema file

```yaml
# schema.kenv.yaml
environments:
  - dev
  - staging
  - production

variables:
  APP_NAME:
    type: String
    scope: global
    default: "MyApp"

  API_HOST:
    type: Url
    scope: environment

  DEBUG_ENABLED:
    type: Boolean
    scope: environment

groups:
  database:
    DB_HOST:
      type: String
      scope: environment
    DB_PORT:
      type: Int
      scope: global
      default: "5432"
```

### 3. Create environment files

```env
# env/env.dev.env
API_HOST=http://localhost:8080
DEBUG_ENABLED=true
DB_HOST=localhost
```

```env
# env/env.production.env
API_HOST=https://api.example.com
DEBUG_ENABLED=false
DB_HOST=db.example.com
```

### 4. Configure the plugin

```kotlin
// build.gradle.kts
kenvConfig {
    schemaFile.set(file("schema.kenv.yaml"))
    envDirectory.set(file("env"))
    environments.set(listOf("dev", "staging", "production"))
    generatedClassName.set("EnvConfig")
    generatedPackageName.set("com.example.config")
}
```

### 5. Use the generated code

```kotlin
// Multi-environment mode (default)
val devHost = EnvConfig.Dev.API_HOST        // "http://localhost:8080"
val prodHost = EnvConfig.Production.API_HOST // "https://api.example.com"
val appName = EnvConfig.APP_NAME             // "MyApp" (global)
val dbPort = EnvConfig.Dev.Database.DB_PORT  // 5432 (global with default)

// Active environment mode (flat object)
val host = EnvConfig.API_HOST                // value for the active environment
```

---

## Schema File Format

The schema file is a YAML document that declares your environment variables, their types, and scoping rules.

### Structure

```yaml
environments:
  - <env_name>
  - ...

variables:
  <VARIABLE_NAME>:
    type: <Type>
    scope: <global|environment>   # optional, defaults to "environment"
    default: "<value>"            # optional
    description: "<text>"         # optional

groups:
  <group_name>:
    <VARIABLE_NAME>:
      type: <Type>
      scope: <global|environment>
      default: "<value>"
      description: "<text>"
```

### Supported Types

| Schema Type | Kotlin Type | Example Values | Notes |
|-------------|-------------|----------------|-------|
| `String`    | `String`    | `"hello"`, `""` | Any string value |
| `Int`       | `Int`       | `42`, `-1` | 32-bit integer |
| `Long`      | `Long`      | `9999999999` | 64-bit integer |
| `Double`    | `Double`    | `3.14`, `-0.5` | 64-bit floating point |
| `Float`     | `Float`     | `1.5`, `0.0` | 32-bit floating point |
| `Boolean`   | `Boolean`   | `true`, `false` | Case-insensitive |
| `Url`       | `String`    | `https://example.com` | Non-empty string |

### Variable Scope

- **`environment`** (default): The variable must be defined in every declared environment file. Each environment can have a different value.
- **`global`**: The variable has a single value shared across all environments. Defined via `default` in the schema or in a global values file (`env.global.<ext>`).

### Groups

Groups organize related variables into nested Kotlin objects:

```yaml
groups:
  database:
    DB_HOST:
      type: String
      scope: environment
    DB_PORT:
      type: Int
      scope: global
      default: "5432"
  auth:
    AUTH_CLIENT_ID:
      type: String
      scope: environment
```

This generates:

```kotlin
object EnvConfig {
    object Dev {
        object Database {
            val DB_HOST: String = "localhost"
            val DB_PORT: Int = 5432
        }
        object Auth {
            val AUTH_CLIENT_ID: String = "dev-client-id"
        }
    }
}
```

### Default Values

Variables can declare a `default` value in the schema. If an environment file doesn't provide a value for that variable, the default is used:

```yaml
variables:
  MAX_RETRIES:
    type: Int
    scope: environment
    default: "3"    # Used when an env file omits MAX_RETRIES
```

For global-scoped variables, the default is the primary way to provide a value (unless overridden by a global values file).

---

## Environment File Format

Environment files follow the naming convention: **`env.<name>.<ext>`**

| Pattern | Example |
|---------|---------|
| `env.<envName>.env` | `env.dev.env`, `env.production.env` |
| `env.<envName>.yaml` | `env.staging.yaml` |
| `env.<envName>.yml` | `env.staging.yml` |
| `env.<envName>.toml` | `env.production.toml` |

The plugin auto-discovers files in the configured `envDirectory` by matching this naming pattern.

### .env format

```env
# Comments start with #
API_HOST=http://localhost:8080
DEBUG_ENABLED=true
SECRET_KEY="value with spaces"    # inline comment
EMPTY_VALUE=
```

Rules:
- One `KEY=VALUE` pair per line
- Lines starting with `#` are comments
- Values can be quoted with `"` or `'` (quotes are stripped)
- Inline comments after `#` are supported (but not inside quoted values)
- Empty values are allowed (`KEY=`)

### YAML format

```yaml
# env.dev.yaml
API_HOST: http://localhost:8080
DEBUG_ENABLED: "true"
DB_HOST: localhost
```

Rules:
- Flat key-value pairs only (no nested structures)
- All values are treated as strings

### TOML format

```toml
# env.dev.toml
API_HOST = "http://localhost:8080"
DEBUG_ENABLED = "true"
DB_HOST = "localhost"
```

Rules:
- Flat key-value pairs only (no tables/sections)
- All values are treated as strings

All values across all formats are stored as strings internally and validated against the schema's type declarations at compile time.

---

## Global Values File

For global-scoped variables that don't have a `default` in the schema, provide values in a global file:

```env
# env/env.global.env
APP_SECRET_KEY=shared-secret-across-all-envs
ANALYTICS_ID=UA-12345
```

The global values file follows the same naming convention: `env.global.<ext>` and supports `.env`, `.yaml`, and `.toml` formats.

The plugin auto-discovers `env.global.<ext>` in the `envDirectory`. You can also set it explicitly:

```kotlin
kenvConfig {
    globalValuesFile.set(file("env/env.global.yaml"))
}
```

**Resolution order for global variables:**
1. Value from `env.global.<ext>` (highest priority)
2. `default` value from the schema

If a global variable has neither a default nor a value in the global file, the build fails with a clear error.

---

## Plugin Configuration (DSL Reference)

```kotlin
kenvConfig {
    // Required: path to the schema YAML file
    schemaFile.set(file("schema.kenv.yaml"))

    // Required: directory containing environment files
    envDirectory.set(file("env"))

    // Required: list of environment names to process
    environments.set(listOf("dev", "staging", "production"))

    // Optional: compile only a single environment (see Active Environment below)
    activeEnvironment.set("dev")

    // Optional: explicit path to the global values file
    // (auto-discovered from envDirectory if not set)
    globalValuesFile.set(file("env/env.global.yaml"))

    // Optional: name of the generated Kotlin object (default: "EnvConfig")
    generatedClassName.set("EnvConfig")

    // Optional: package name for the generated source file
    // (if not set, the class is generated at the root package)
    generatedPackageName.set("com.example.config")
}
```

### Configuration Properties

| Property | Required | Default | Description |
|----------|----------|---------|-------------|
| `schemaFile` | Yes | — | Path to the YAML schema file |
| `envDirectory` | Yes | — | Directory containing `env.<name>.<ext>` files |
| `environments` | Yes | — | List of environment names to process |
| `activeEnvironment` | No | — | If set, generates a flat object for one environment only |
| `globalValuesFile` | No | auto-discovered | Explicit path to `env.global.<ext>` |
| `generatedClassName` | No | `"EnvConfig"` | Name of the generated Kotlin object |
| `generatedPackageName` | No | root package | Package declaration for the generated file |

---

## Active Environment Selection

By default, KEnv generates nested objects for all declared environments. To compile only a single environment's values into a flat object:

### Via DSL

```kotlin
kenvConfig {
    activeEnvironment.set("production")
}
```

### Via Gradle property (CI/CD)

```bash
./gradlew build -PactiveEnvironment=production
```

The Gradle property takes precedence over the DSL value, making it easy to switch environments in CI pipelines without modifying build files.

### Generated code difference

**Without `activeEnvironment`** (multi-environment mode):
```kotlin
object EnvConfig {
    val APP_NAME: String = "MyApp"  // global

    object Dev {
        val API_HOST: String = "http://localhost:8080"
        val DEBUG_ENABLED: Boolean = true

        object Database {
            val DB_HOST: String = "localhost"
            val DB_PORT: Int = 5432
        }
    }

    object Production {
        val API_HOST: String = "https://api.example.com"
        val DEBUG_ENABLED: Boolean = false

        object Database {
            val DB_HOST: String = "db.example.com"
            val DB_PORT: Int = 5432
        }
    }
}
```

**With `activeEnvironment = "production"`** (single-environment mode):
```kotlin
object EnvConfig {
    val APP_NAME: String = "MyApp"
    val API_HOST: String = "https://api.example.com"
    val DEBUG_ENABLED: Boolean = false

    object Database {
        val DB_HOST: String = "db.example.com"
        val DB_PORT: Int = 5432
    }
}
```

---

## Generated Code

The generated code is a Kotlin `object` placed in `commonMain`, so it's accessible from all platform targets (Android, iOS, JVM, native).

### Accessing values

```kotlin
// Global variables (same value everywhere)
val appName = EnvConfig.APP_NAME

// Environment-specific variables (multi-env mode)
val devHost = EnvConfig.Dev.API_HOST
val prodDb = EnvConfig.Production.Database.DB_HOST

// Grouped variables
val dbPort = EnvConfig.Dev.Database.DB_PORT
```

### Output location

The generated file is placed at:
```
build/generated/kenv/commonMain/kotlin/<package-path>/<ClassName>.kt
```

For example, with `generatedPackageName = "com.example.config"` and `generatedClassName = "AppConfig"`:
```
build/generated/kenv/commonMain/kotlin/com/example/config/AppConfig.kt
```

### Source set wiring

The plugin automatically:
1. Adds the generated source directory to `commonMain` (KMP) or `main` (JVM)
2. Makes `kenvGenerate` run before all `compileKotlin*` tasks

No manual source set configuration is needed.

---

## Full Example

### Project structure

```
my-kmp-app/
├── settings.gradle.kts
├── build.gradle.kts
├── kenv-plugin/                    # Plugin source (or use published artifact)
├── app/
│   ├── build.gradle.kts
│   ├── schema.kenv.yaml
│   ├── env/
│   │   ├── env.dev.env
│   │   ├── env.staging.yaml
│   │   ├── env.production.yaml
│   │   └── env.global.env
│   └── src/
│       └── commonMain/
│           └── kotlin/
│               └── App.kt
```

### settings.gradle.kts

```kotlin
rootProject.name = "my-kmp-app"

pluginManagement {
    includeBuild("kenv-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":app")
```

### app/build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.adventures92.kenv-config")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm()
}

kenvConfig {
    schemaFile.set(file("schema.kenv.yaml"))
    envDirectory.set(file("env"))
    environments.set(listOf("dev", "staging", "production"))
    generatedClassName.set("AppConfig")
    generatedPackageName.set("com.myapp.config")
}
```

### app/schema.kenv.yaml

```yaml
environments:
  - dev
  - staging
  - production

variables:
  APP_NAME:
    type: String
    scope: global
    default: "MyKMPApp"
    description: "Application display name"

  API_BASE_URL:
    type: Url
    scope: environment
    description: "Base URL for the API"

  DEBUG_MODE:
    type: Boolean
    scope: environment
    default: "false"

  MAX_RETRIES:
    type: Int
    scope: global
    default: "3"

groups:
  auth:
    AUTH_CLIENT_ID:
      type: String
      scope: environment
    AUTH_REDIRECT_URL:
      type: Url
      scope: environment

  analytics:
    ANALYTICS_KEY:
      type: String
      scope: global
```

### app/env/env.global.env

```env
ANALYTICS_KEY=UA-123456-1
```

### app/env/env.dev.env

```env
API_BASE_URL=http://localhost:3000
DEBUG_MODE=true
AUTH_CLIENT_ID=dev-client-id
AUTH_REDIRECT_URL=http://localhost:3000/callback
```

### app/env/env.production.yaml

```yaml
API_BASE_URL: "https://api.myapp.com"
DEBUG_MODE: "false"
AUTH_CLIENT_ID: "prod-client-abc123"
AUTH_REDIRECT_URL: "https://myapp.com/auth/callback"
```

### app/src/commonMain/kotlin/App.kt

```kotlin
package com.myapp

import com.myapp.config.AppConfig

fun initializeApp() {
    // Global values
    println("App: ${AppConfig.APP_NAME}")
    println("Max retries: ${AppConfig.MAX_RETRIES}")

    // Environment-specific values
    println("Dev API: ${AppConfig.Dev.API_BASE_URL}")
    println("Prod API: ${AppConfig.Production.API_BASE_URL}")

    // Grouped values
    println("Dev auth client: ${AppConfig.Dev.Auth.AUTH_CLIENT_ID}")
    println("Analytics key: ${AppConfig.Dev.Analytics.ANALYTICS_KEY}")
}
```

---

## Error Messages

KEnv provides clear compile-time errors when configuration is invalid:

| Error | Cause | Fix |
|-------|-------|-----|
| `Missing required variable 'X' in environment 'Y'` | Environment-scoped variable without a default is absent from an env file | Add the variable to the env file or add a `default` in the schema |
| `Missing global variable 'X'` | Global-scoped variable has no default and no value in `env.global.<ext>` | Add a `default` in the schema or create/update the global values file |
| `Type mismatch for 'X' in environment 'Y': expected Int, got 'abc'` | Value doesn't parse as the declared type | Fix the value in the env file |
| `Invalid activeEnvironment 'qa'` | The `-PactiveEnvironment` value doesn't match any declared environment | Use one of the environment names from the schema |
| `Schema file not found` | The `schemaFile` path doesn't exist | Check the path in `kenvConfig` |
| `Environment file not found for 'X'` | No `env.X.<ext>` file found in `envDirectory` | Create the file or check the directory path |

Undeclared variables in environment files produce **warnings** (build continues):

```
Warning: Variable 'LEGACY_FLAG' in environment 'dev' is not declared in schema
```

---

## Incremental Builds

The plugin supports Gradle's incremental build system via `@CacheableTask`. The `kenvGenerate` task is skipped (UP-TO-DATE) when:

- The schema file hasn't changed
- No environment files have changed
- The plugin configuration hasn't changed

This keeps your builds fast during normal development.

---

## Troubleshooting

### Generated code not found / unresolved reference

Make sure:
1. The plugin is applied: `id("io.github.adventures92.kenv-config")` in your `plugins` block
2. The `kenvConfig` block is present with valid paths
3. Run `./gradlew kenvGenerate` manually to check for errors
4. The generated file should appear at `build/generated/kenv/commonMain/kotlin/`

### Plugin not found

If you get "Plugin 'io.github.adventures92.kenv-config' not found":
1. Ensure `includeBuild("kenv-plugin")` is in your `settings.gradle.kts` under `pluginManagement`
2. Verify the `kenv-plugin` directory exists and contains a valid `build.gradle.kts`

### Environment file not discovered

The plugin looks for files matching `env.<name>.<ext>` in the configured `envDirectory`. Check:
1. The file follows the naming convention exactly (e.g., `env.dev.env`, not `dev.env`)
2. The extension is one of: `.env`, `.yaml`, `.yml`, `.toml`
3. The environment name in the filename matches what's in `environments.set(listOf(...))`

### Type validation errors

All values in environment files are strings. The plugin validates them against the schema type at compile time. Common issues:
- Boolean values must be exactly `true` or `false` (case-insensitive)
- Int/Long values must be valid integers (no decimals)
- Empty strings fail validation for all types except `String` and `Url`

### Mixing formats

You can use different formats for different environments. For example:
- `env.dev.env` (dotenv format for local development)
- `env.production.yaml` (YAML for production, managed by CI)
- `env.global.toml` (TOML for global values)

The plugin detects the format from the file extension.
