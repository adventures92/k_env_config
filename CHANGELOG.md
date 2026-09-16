# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This is a product document, not a work log. A change belongs here if a user of the plugin could
observe it — generated output, the `kenvConfig` DSL, parsing behaviour, error messages, published
coordinates. Build, CI and refactoring changes do not; git history holds those.

## [Unreleased]

### Fixed

- The published javadoc jar is no longer empty. `0.2.0` shipped one containing only a manifest:
  Maven Central requires the file to exist and never inspects it, so an empty stub passes
  validation silently. Dokka now generates it, so the API reference on javadoc.io resolves.
  The sources jar was always complete, so this affected the rendered reference only.

## [0.2.0] - 2026-09-16

### Fixed

- Schema files are no longer rewritten by YAML 1.1 implicit typing. The schema parser kept
  SnakeYAML's defaults, so a variable named `ON` or `NO` silently became `true`/`false` — KEnv
  variables are conventionally SCREAMING_SNAKE_CASE, and YAML 1.1 resolves those tokens as
  booleans in any casing. The same applied to environment names (`no` became `"false"`), group
  names, and unquoted descriptions (`1.10` became `"1.1"`). This is the bug previously fixed for
  *env* files, which had survived in the schema parser because the hardening lived privately
  inside the env parser; both now share one loader.

- YAML env files no longer have their values silently retyped. SnakeYAML's YAML 1.1 implicit
  resolver was coercing unquoted scalars before they were converted back to `String`, so a file
  did not mean what it showed: `FEATURE: on` became `"true"`, `REGION: no` became `"false"`,
  `API_VERSION: 1.10` became `"1.1"`, and `API_PORT: 0755` became `"493"`. Values are now read as
  literal text. Printing quotes the YAML 1.1 boolean aliases (`yes`/`no`/`on`/`off`/`y`/`n`) so a
  round trip is stable.
- Env files load through `SafeConstructor`, which blocks arbitrary type instantiation from
  explicit tags.
- The published POM's `url` and `scm` entries pointed at `github.com/adventures92/kenv-config`,
  which does not exist. They now point at `k_env_config`. Affects the repository links shown on
  Maven Central and followed by tooling; `0.1.0` shipped with the broken ones and cannot be
  corrected in place, since a published POM is immutable.

## [0.1.0] - 2026-05-06

Initial release. Generates Kotlin objects from a YAML schema plus environment-specific value
files, so a missing or mistyped value fails the build instead of surfacing at runtime.

### Added

- Compile-time validation of schema and env files, with located error messages
- Seven value types — `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Url`
- Global and per-environment variable scopes
- Groups, generated as nested Kotlin objects
- KDoc generation from the schema's `description` fields
- Runtime environment selection via `EnvConfig.setActiveEnvironment(...)`
- Android variant mapping — `buildType("debug") uses "dev"`
- A single `kenv/` directory holding the schema and every env file
- `.env`, YAML and TOML env file formats
- An incremental, cacheable `kenvGenerate` task, skipped when inputs are unchanged
- A `.gitignore` advisory warning when env files are not ignored

[Unreleased]: https://github.com/adventures92/k_env_config/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/adventures92/k_env_config/releases/tag/v0.2.0
[0.1.0]: https://github.com/adventures92/k_env_config/releases/tag/v0.1.0
