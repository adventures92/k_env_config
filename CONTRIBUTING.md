# Contributing

Thanks for looking. This is a small, single-maintainer project, so the process is light.

## Branching

Trunk-based. `main` is the only long-lived branch, and it is always releasable.

```bash
git switch -c fix/schema-scope-validation main
# … work, then open a PR against main
```

Short-lived `feat/…`, `fix/…`, `docs/…`, `chore/…` branches, squash-merged so `main` stays one
commit per change. Releases are annotated `vX.Y.Z` tags on `main`, never on a branch.

## The shape of this repository

Two Gradle builds, which surprises people:

| | |
|---|---|
| `kenv-plugin/` | **The product.** A `kotlin("jvm")` Gradle plugin, published as `io.github.adventures92:kenv-config`. Its own build, pulled in by the root via `includeBuild`. |
| `composeApp/`, `iosApp/` | A demo that consumes the plugin, and the only end-to-end proof that code generation works. |

Because `kenv-plugin` is a separate build, it does **not** inherit the root version catalog
automatically — `kenv-plugin/settings.gradle.kts` wires it in explicitly. Add versions to
`gradle/libs.versions.toml`, never inline in a build script.

## Ground rules

- **Generated code is the product.** A change to what `EnvConfig.kt` looks like is a behaviour
  change even when no Kotlin API moved. It needs a test and a changelog entry.
- **Values are strings all the way through.** Env and schema files must never be silently retyped
  by the YAML layer — `FEATURE: on` means the string `on`. There are regression tests for this;
  do not weaken them.
- **Never commit a real env file.** The examples under `composeApp/kenv/` are deliberately fake.
- **Errors should say where.** A malformed input should produce a located, readable message, not
  a stack trace.

## Before you open a pull request

```bash
./gradlew kenv-plugin:test          # the plugin's own suite
./gradlew :composeApp:assembleDebug # the demo still builds
./gradlew :composeApp:kenvGenerate  # generation still works end to end
```

Add an entry under `## [Unreleased]` in `CHANGELOG.md` if a user could observe the change. CI
fixes and refactors do not belong there — git history holds those.

## CI

Every pull request runs the plugin test suite and builds the demo app. Both must be green before
a merge. See `.github/workflows/`.

## Releasing

Maintainer only. Releases are cut from `main` by tagging; the version lives in
`gradle.properties` and the changelog entry for that version must exist or the release is
refused. See `AGENTS.md` for the full process.
