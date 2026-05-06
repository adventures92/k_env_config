# KEnv Config

Schema-based, type-safe environment variable management for Kotlin Multiplatform projects.

KEnv Config generates Kotlin object classes from a YAML schema and environment files at compile time, giving you type-safe access to configuration values across Android, iOS, JVM, and native targets — with zero runtime dependencies.

## Features

- **Type-safe** — Variables are validated against declared types at compile time
- **Multiplatform** — Generated code lives in `commonMain`, accessible from all targets
- **Multi-environment** — Define separate configs for dev, staging, production, etc.
- **Multi-format** — Environment files in `.env`, `.yaml`, or `.toml`
- **Global variables** — Shared values across all environments with a single source of truth
- **Grouped variables** — Organize related variables into nested objects
- **Incremental builds** — Gradle caching means generation only runs when inputs change
- **CI-friendly** — Switch active environment via `-PactiveEnvironment=production`

## Project Structure

```
K_env_config/
├── kenv-plugin/       # The Gradle plugin (adven.kenv.config)
├── composeApp/        # Example KMP Compose app demonstrating the plugin
├── build.gradle.kts   # Root build file
└── settings.gradle.kts
```

## Quick Start

See the [KEnv Plugin documentation](./kenv-plugin/README.md) for full installation and usage instructions.

### TL;DR

1. Include the plugin in your build
2. Define a YAML schema with your variables and their types
3. Create environment files with values for each environment
4. Access generated type-safe config objects in your Kotlin code

```kotlin
// Generated at compile time — fully type-safe
val apiUrl: String = EnvConfig.Dev.Server.API_URL
val port: Int = EnvConfig.Dev.Server.API_PORT
val analytics: Boolean = EnvConfig.Production.Feature.ENABLE_ANALYTICS
```

## Example App

The `composeApp` module is a working Kotlin Multiplatform Compose application that demonstrates the plugin. It:

- Applies the `io.github.adventures92.kenv-config` plugin
- Defines a schema with `server` and `feature` variable groups
- Provides `dev` and `production` environment files
- Displays generated config values in the Compose UI

### Running the Example

**Android:**
```shell
./gradlew :composeApp:assembleDebug
```

**iOS:**
Open the `iosApp` directory in Xcode and run from there, or use the run configuration in your IDE.

## Building from Source

```shell
# Build everything (plugin + example app)
./gradlew build

# Run plugin tests only
./gradlew kenv-plugin:test

# Generate config for the example app
./gradlew :composeApp:kenvGenerate
```

## Requirements

- Kotlin 2.0+
- Gradle 8.0+
- JDK 17+

## License

See [LICENSE](./LICENSE) for details.
