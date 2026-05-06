# Runtime Environment Selection

In Kotlin Multiplatform projects where Android build variants aren't available (or when you need dynamic environment switching), the generated code provides a runtime selection API.

## How It Works

The generated `EnvConfig` object includes:

1. **`setActiveEnvironment(name)`** — Sets which environment's values are returned by flat accessors
2. **Flat property accessors** — Environment-scoped properties that delegate to the active environment
3. **Per-environment nested objects** — Direct access to any environment's values without setting an active one

## Setting the Active Environment

Call `setActiveEnvironment()` once at app startup:

```kotlin
// In your app initialization (Application.onCreate, main(), etc.)
EnvConfig.setActiveEnvironment("production")
```

After this call, all environment-scoped flat accessors return the production values:

```kotlin
EnvConfig.Server.API_BASE_URL  // → "https://api.example.com"
EnvConfig.Server.API_PORT      // → 443
EnvConfig.DEBUG_MODE           // → false
```

## Switching at Runtime

You can call `setActiveEnvironment()` multiple times to switch environments:

```kotlin
// Switch to dev
EnvConfig.setActiveEnvironment("dev")
println(EnvConfig.Server.API_BASE_URL)  // → "http://localhost:8080"

// Switch to production
EnvConfig.setActiveEnvironment("production")
println(EnvConfig.Server.API_BASE_URL)  // → "https://api.example.com"
```

## Checking the Active Environment

```kotlin
val current = EnvConfig.activeEnvironment  // "production" or null if not set
```

## Error Handling

If you access a flat property without setting an active environment:

```kotlin
// Throws IllegalStateException:
// "Active environment not set. Call EnvConfig.setActiveEnvironment() first."
val url = EnvConfig.Server.API_BASE_URL
```

If you pass an invalid environment name:

```kotlin
// Throws IllegalArgumentException:
// "Invalid environment 'staging'. Valid environments: dev, production"
EnvConfig.setActiveEnvironment("staging")
```

## Direct Per-Environment Access

You can always access any environment's values directly without setting an active environment:

```kotlin
// No setActiveEnvironment() needed
val devUrl = EnvConfig.Dev.Server.API_BASE_URL
val prodUrl = EnvConfig.Production.Server.API_BASE_URL
```

This is useful when you need to compare values across environments or display multiple environments simultaneously.

## Global Variables

Global-scoped variables are always accessible regardless of the active environment:

```kotlin
// These work without setActiveEnvironment()
val appName = EnvConfig.Identity.APP_NAME
val version = EnvConfig.APP_VERSION
```

## KMP Usage Pattern

A typical KMP app initialization:

```kotlin
// commonMain
fun initApp(environment: String) {
    EnvConfig.setActiveEnvironment(environment)
    // Now all flat accessors work
}

// androidMain
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        initApp(if (BuildConfig.DEBUG) "dev" else "production")
        // ...
    }
}

// iosMain
fun MainViewController() = ComposeUIViewController {
    initApp("production")  // Or read from Info.plist, launch args, etc.
    App()
}
```

## Thread Safety

The active environment is stored in a simple mutable variable. If you need thread-safe access in a concurrent environment, synchronize calls to `setActiveEnvironment()` or set it once during initialization before any concurrent reads.

## Comparison with Variant Mapping

| Feature | Runtime Selection | Variant Mapping |
|---------|-------------------|-----------------|
| Platform | All (KMP) | Android only |
| Resolution time | Runtime | Compile time |
| Switching | Dynamic | Fixed per build |
| Code size | All environments included | Only one environment |
| Use case | KMP, dynamic config | Android build types |

For Android-only projects where environments align with build types, prefer [variant mapping](variant-mapping.md) for smaller APK size and compile-time guarantees.
