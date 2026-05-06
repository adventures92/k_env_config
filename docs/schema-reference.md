# Schema Reference

The schema file (`schema.kenv.yaml`) declares the structure of your configuration: which variables exist, their types, scopes, and documentation.

## File Location

The schema must be named `schema.kenv.yaml` and placed in the kenv directory (default: `kenv/`).

## Structure

```yaml
environments:
  - <env_name>
  - <env_name>

variables:
  <VARIABLE_NAME>:
    type: <Type>
    scope: <Scope>
    description: "<optional description>"

groups:
  <group_name>:
    <VARIABLE_NAME>:
      type: <Type>
      scope: <Scope>
      description: "<optional description>"
```

## Environments

The `environments` list declares all environment names. Each environment must have a corresponding env file (`env.<name>.<ext>`).

```yaml
environments:
  - dev
  - staging
  - production
```

## Variables

### Type

| Type | Kotlin Type | Example Value |
|------|-------------|---------------|
| `String` | `String` | `"hello"` |
| `Int` | `Int` | `42` |
| `Long` | `Long` | `123456789` |
| `Double` | `Double` | `3.14` |
| `Float` | `Float` | `2.5` |
| `Boolean` | `Boolean` | `true` / `false` |
| `Url` | `String` | `"https://example.com"` |

### Scope

| Scope | Meaning | Value Source |
|-------|---------|--------------|
| `environment` | Different per environment | `env.<name>.<ext>` files |
| `global` | Same across all environments | `env.global.<ext>` file |

If `scope` is omitted, it defaults to `environment`.

### Description

Optional. When provided, generates a KDoc comment above the property in the generated code:

```yaml
variables:
  API_URL:
    type: Url
    scope: environment
    description: "Backend API base URL including protocol"
```

Generates:

```kotlin
/** Backend API base URL including protocol */
val API_URL: String = "https://api.example.com"
```

Special characters (`*/`, `@`) are automatically escaped in KDoc output.

## Groups

Groups organize variables into nested Kotlin objects:

```yaml
groups:
  database:
    DB_HOST:
      type: String
      scope: environment
    DB_PORT:
      type: Int
      scope: global
```

Generates:

```kotlin
object Database {
    val DB_HOST: String = "localhost"
    val DB_PORT: Int = 5432
}
```

Access via: `EnvConfig.Database.DB_HOST`

## Environment Files

### Supported Formats

| Extension | Format |
|-----------|--------|
| `.env` | Dotenv (KEY=VALUE) |
| `.yaml` / `.yml` | YAML mapping |
| `.toml` | TOML |

### Naming Convention

- Environment files: `env.<environment_name>.<ext>`
- Global file: `env.global.<ext>`

Examples:
```
kenv/
├── schema.kenv.yaml
├── env.dev.env
├── env.staging.yaml
├── env.production.toml
└── env.global.env
```

### Dotenv Format

```dotenv
# Comments are supported
API_URL=https://api.example.com
API_PORT=443
DEBUG_MODE=false
```

### YAML Format

```yaml
API_URL: https://api.example.com
API_PORT: 443
DEBUG_MODE: false
```

### TOML Format

```toml
API_URL = "https://api.example.com"
API_PORT = 443
DEBUG_MODE = false
```

## Validation Rules

1. **All environment-scoped variables** must have a value in every env file
2. **All global-scoped variables** must have a value in the global env file
3. **Type checking** — values must parse as their declared type
4. **No defaults** — the `default` field is not supported in v2; all values must be explicit
5. **Undeclared variables** in env files produce warnings (not errors)
