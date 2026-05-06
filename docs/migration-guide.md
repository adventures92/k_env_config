# Migration Guide: v1 → v2

This guide covers breaking changes when upgrading from KEnv Config v1 to v2.

## Summary of Breaking Changes

| Area | v1 | v2 |
|------|----|----|
| Directory structure | Separate `schemaFile`, `envDirectory`, `globalValuesFile` | Single `directory` property |
| Default values | Supported in schema (`default: "value"`) | Removed — all values must be in env files |
| Package name | Optional | Required |
| DSL properties | `schemaFile`, `envDirectory`, `globalValuesFile`, `activeEnvironment` | `directory`, `environments`, `generatedPackageName`, `generatedClassName`, `variantMapping` |

## Step-by-Step Migration

### 1. Restructure Files into `kenv/` Directory

**Before (v1):**
```
my-app/
├── schema.kenv.yaml
├── env/
│   ├── env.dev.env
│   ├── env.production.env
│   └── env.global.env
└── build.gradle.kts
```

**After (v2):**
```
my-app/
├── kenv/
│   ├── schema.kenv.yaml
│   ├── env.dev.env
│   ├── env.production.env
│   └── env.global.env
└── build.gradle.kts
```

Move all files into a single `kenv/` directory.

### 2. Remove Default Values from Schema

**Before (v1):**
```yaml
variables:
  APP_NAME:
    type: String
    scope: global
    default: "My App"
```

**After (v2):**
```yaml
variables:
  APP_NAME:
    type: String
    scope: global
```

Move the default value into `env.global.env`:
```dotenv
APP_NAME=My App
```

The v2 parser will reject any schema containing a `default` field with a clear error message.

### 3. Update build.gradle.kts DSL

**Before (v1):**
```kotlin
kenvConfig {
    schemaFile.set(file("schema.kenv.yaml"))
    envDirectory.set(file("env"))
    environments.set(listOf("dev", "production"))
    activeEnvironment.set("dev")
    generatedPackageName.set("com.example.config")  // was optional
}
```

**After (v2):**
```kotlin
kenvConfig {
    directory.set(file("kenv"))  // or omit for default "kenv/"
    environments.set(listOf("dev", "production"))
    generatedPackageName.set("com.example.config")  // now required
}
```

Removed properties:
- `schemaFile` — discovered automatically as `schema.kenv.yaml` in the directory
- `envDirectory` — same as `directory`
- `globalValuesFile` — discovered automatically as `env.global.<ext>` in the directory
- `activeEnvironment` — use `-PactiveEnvironment=dev` flag or variant mapping instead

### 4. Add Package Name (if missing)

In v2, `generatedPackageName` is required. If you didn't set it in v1, add it now:

```kotlin
kenvConfig {
    generatedPackageName.set("com.example.config")
}
```

### 5. Ensure All Values Are Explicit

In v1, variables with defaults could be absent from env files. In v2, every variable must have a value:

- **Environment-scoped variables** → must be in every `env.<name>.<ext>` file
- **Global-scoped variables** → must be in `env.global.<ext>`

The build will fail with clear error messages identifying any missing values.

### 6. (Optional) Add Variant Mapping

If you were using `-PactiveEnvironment` with Android build types, consider migrating to variant mapping:

```kotlin
kenvConfig {
    variantMapping {
        buildType("debug") uses "dev"
        buildType("release") uses "production"
    }
}
```

This eliminates the need for Gradle property flags and generates flat config per variant.

## New Features in v2

After migrating, you gain access to:

- **Runtime environment selection** — `EnvConfig.setActiveEnvironment("dev")` for KMP
- **Variant mapping** — Automatic per-build-type generation for Android
- **KDoc generation** — Add `description` fields to your schema for IDE documentation
- **Gitignore advisory** — Warning when `.gitignore` is missing in kenv directory
- **Better error messages** — File paths and line numbers in all parse errors
