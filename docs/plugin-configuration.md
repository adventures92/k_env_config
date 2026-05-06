# Plugin Configuration

## DSL Reference

The plugin is configured via the `kenvConfig` block in your `build.gradle.kts`:

```kotlin
kenvConfig {
    // Required: list of environment names
    environments.set(listOf("dev", "staging", "production"))

    // Required: package name for generated code
    generatedPackageName.set("com.example.config")

    // Optional: directory containing schema and env files (default: "kenv/")
    directory.set(file("kenv"))

    // Optional: generated class name (default: "EnvConfig")
    generatedClassName.set("EnvConfig")

    // Optional: Android variant mapping
    variantMapping {
        buildType("debug") uses "dev"
        buildType("release") uses "production"
    }
}
```

## Properties

### `directory`

- **Type:** `DirectoryProperty`
- **Default:** `kenv/` relative to the project directory
- **Description:** The single directory containing `schema.kenv.yaml` and all environment files.

### `environments`

- **Type:** `ListProperty<String>`
- **Required:** Yes
- **Description:** List of environment names to process. Each name must have a corresponding `env.<name>.<ext>` file in the kenv directory.

### `generatedPackageName`

- **Type:** `Property<String>`
- **Required:** Yes
- **Description:** Package declaration for the generated Kotlin source file. Build fails if not set.

### `generatedClassName`

- **Type:** `Property<String>`
- **Default:** `"EnvConfig"`
- **Description:** Name of the generated Kotlin object class.

### `variantMapping`

- **Type:** `VariantMappingDsl`
- **Required:** No
- **Description:** Maps Android build types or product flavors to environment names. See [Android Variant Mapping](variant-mapping.md).

## Gradle Tasks

### `kenvGenerate`

The default generation task. Produces multi-environment output with nested objects per environment plus flat access via `setActiveEnvironment()`.

```bash
./gradlew kenvGenerate
```

Supports the `-PactiveEnvironment=<name>` flag to generate a single flat object for one environment:

```bash
./gradlew kenvGenerate -PactiveEnvironment=production
```

### `kenvGenerate<BuildType>` (with variant mapping)

When variant mapping is configured, per-variant tasks are registered:

```bash
./gradlew kenvGenerateDebug    # Generates flat config using "dev" environment
./gradlew kenvGenerateRelease  # Generates flat config using "production" environment
```

## Output Location

| Mode | Output Directory |
|------|-----------------|
| Default (multi-env) | `build/generated/kenv/commonMain/kotlin/` |
| Active environment (`-P` flag) | `build/generated/kenv/commonMain/kotlin/` |
| Variant mapping (debug) | `build/generated/kenv/debug/kotlin/` |
| Variant mapping (release) | `build/generated/kenv/release/kotlin/` |

## Error Messages

| Condition | Error |
|-----------|-------|
| `generatedPackageName` not set | `"generatedPackageName is required. Set it in the kenvConfig block."` |
| Schema file missing | `"Schema file not found: expected schema.kenv.yaml in <dir>"` |
| Variable has `default` field | `"Variable '<name>' contains a 'default' field which is not supported in v2..."` |
| Missing env-scoped variable | `"Missing required variable '<name>' in environment '<env>'"` |
| Missing global variable | `"Missing global variable '<name>': define in env.global.<ext>"` |
| Type mismatch | `"Type mismatch for '<name>' in environment '<env>': expected <type>, got '<value>'"` |
| Invalid variant mapping | `"Variant mapping error: build type '<name>' maps to environment '<env>' which is not in declared environments: [...]"` |

## Gitignore Advisory

The plugin logs a warning if no `.gitignore` exists in the kenv directory:

```
KEnv: No .gitignore found in <dir>. Consider adding one with patterns like:
  env.production.*
  env.global.*
```

This is advisory only — the plugin never creates or modifies files.
