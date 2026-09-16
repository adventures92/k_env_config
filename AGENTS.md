# AGENTS.md

Canonical, vendor-neutral contract for coding agents working in this repository.
`CLAUDE.md` imports this file; do not duplicate policy there.

## What this is

A Gradle plugin — `io.github.adventures92.kenv-config`, published to Maven Central as
`io.github.adventures92:kenv-config`. It generates type-safe Kotlin from a YAML schema plus
environment-specific value files, so a missing or mistyped value fails the build instead of
surfacing at runtime.

## The repository is two Gradle builds

This is the single most important thing to know here, and the cause of most surprises.

| | |
|---|---|
| `kenv-plugin/` | **The product.** A `kotlin("jvm")` Gradle plugin with its own `settings.gradle.kts`. The root pulls it in with `includeBuild("kenv-plugin")` in `pluginManagement`. |
| `composeApp/`, `iosApp/` | A demo that *consumes* the plugin — and the only end-to-end proof that generation works against a real project. |

Because `kenv-plugin` is a separate build, it does **not** inherit from the root:

- **the version catalog** — wired explicitly in `kenv-plugin/settings.gradle.kts` via
  `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`
- **`gradle.properties`** — so `VERSION_NAME` lives in `kenv-plugin/gradle.properties`

Both were established by probe, not assumption: a property set only at the root reads back `null`
inside the plugin build. Anything else that "should just be inherited" must be tested, not believed.

## Ground rules

- **Never stage, commit, push, create a PR, tag, or publish without explicit user consent.**
- **Generated code is the product.** A change to what `EnvConfig.kt` looks like is a behaviour
  change even when no Kotlin API moved, and can stop a consumer's code compiling.
- **Values are strings all the way through.** YAML's implicit resolver must never retype them:
  `FEATURE: on` means the string `on`. See "Parsing invariants" below.
- **Versions come from `gradle/libs.versions.toml`**, never inline in a build script.
- Verify claims against live source and configuration before editing.
- Report outcomes faithfully. If a gate fails, say so with the output.

## Commands

```bash
# Quality gates — run before considering any kenv-plugin change done
./gradlew kenv-plugin:spotlessCheck kenv-plugin:detekt

# The same for the root build (demo app and root build scripts)
./gradlew spotlessCheck

# Auto-fix formatting. Two builds, so two tasks
./gradlew spotlessApply kenv-plugin:spotlessApply

# Re-record the detekt baseline — only after deliberately accepting a finding
./gradlew kenv-plugin:detektBaseline

# The plugin's own suite
./gradlew kenv-plugin:test

# End to end: run the plugin against the demo's real schema and env files
./gradlew :composeApp:kenvGenerate
./gradlew :composeApp:assembleDebug

# What gets published, locally
cd kenv-plugin && ../gradlew publishToMavenLocal --no-configuration-cache
find ~/.m2/repository/io/github/adventures92/kenv-config -type f | sort

# The guide
mdbook build     # -> book/, gitignored
```

Requires JDK 17+ (the plugin targets a 17 toolchain) and the Android SDK for the demo app.

> **A test run that discovers nothing still exits 0.** Kotest 6 changed discovery. `BUILD
> SUCCESSFUL` is not proof a suite ran — check the `tests="N"` totals in
> `kenv-plugin/build/test-results/test/*.xml`. CI asserts this explicitly.

### Static analysis

spotless (ktlint) formats; detekt analyses. Because the repository is two Gradle builds, **each
build applies spotless itself** — the root build's `spotless` block excludes `kenv-plugin/**`, and
`kenv-plugin/build.gradle.kts` covers its own sources. There is no single `spotlessCheck` that
covers everything; run both, as CI does. detekt runs on `kenv-plugin/src/main/kotlin` only, which
is where the product lives.

> **Do not add an `.editorconfig`.** ktlint reads one when it is present, and any Kotlin style key
> in it silently overrides the configuration spotless applies in `build.gradle.kts` — reformatting
> the entire codebase on the next `spotlessApply`. Formatter configuration belongs in the build
> scripts. This is a known trap; the sibling Sockit repository lost ~30 files to it.

`detekt.yml` at the repository root is the single config, shared by both builds, and runs with
`maxIssues: 0` — any finding fails the build. `kenv-plugin/detekt-baseline.xml` holds the **17
findings that already existed** when analysis was introduced: 8 `LongMethod`, 2 `NestedBlockDepth`,
2 `ReturnCount`, 2 `UnusedParameter`, and one each of `CyclomaticComplexMethod`, `ThrowsCount` and
`UnusedPrivateProperty`, concentrated in the parsers, `DefaultCodeGenerator` and `DefaultValidator`.
They are accepted debt, not permission: the rules are live, so anything new fails. Each entry names
one function — split the function, delete the line. Prefer that to `detektBaseline`, which
re-records whatever is there and is how a baseline quietly becomes a dumping ground.

Two narrow, deliberate exemptions exist, both documented where they are declared:
`io.kotest.property.arbitrary.**` may be imported on demand in tests (the generator DSL is designed
that way), and `composeApp`'s `MainViewController` keeps its PascalCase name because Swift calls it.

## Architecture

`kenv-plugin/src/main/kotlin/adven/kenv/config/`:

| Package | Role |
|---------|------|
| `plugin/` | `KEnvPlugin`, `KEnvGenerateTask` (`@CacheableTask`), the `kenvConfig` DSL, variant mapping, `GitignoreChecker` |
| `schema/` | Schema parsing and the scope/type model |
| `env/` | `DotEnvParser`, `YamlEnvParser`, `TomlEnvParser` and the file-naming convention |
| `model/` | `ParseError` and `ParseResult` — the located-error types every parser returns |
| `printer/` | Empty placeholder (`.gitkeep` only). Value quoting currently lives in `env/YamlEnvParser` |
| `validation/` | Cross-checks schema against the env files — this is what turns a missing value into a build failure |
| `yaml/` | `StringOnlyYaml` — the hardened loader **every** YAML parser here must use |
| `codegen/` | Emits `EnvConfig.kt` |

### Parsing invariants

- **No implicit retyping — in every parser.** SnakeYAML's YAML 1.1 resolver coerces unquoted
  scalars, so `on` became `"true"`, `no` became `"false"`, `1.10` became `"1.1"` and `0755` became
  `"493"`. `yaml/StringOnlyYaml` builds a loader whose resolver registers nothing, so every
  unquoted scalar falls back to `tag:yaml.org,2002:str`. **Use it for any new YAML loading.** It
  lives in its own package precisely because it did not: the hardening was once private to
  `YamlEnvParser`, and the identical bug survived in `YamlSchemaParser` — where it was worse,
  because map *keys* are affected too and a variable named `ON` became `true`. There are
  regression tests on both sides; do not weaken them.
- **`SafeConstructor` on every YAML load**, which blocks arbitrary type instantiation from explicit tags.
  Note SnakeYAML 2.x already refuses global tags by default — this is defence in depth.
- **Printing quotes the YAML 1.1 boolean aliases** (`yes`/`no`/`on`/`off`/`y`/`n`) so a round trip
  is stable.

## Branching and releases

**Trunk-based. `main` is the only long-lived branch.** Short-lived `feat/…`, `fix/…`, `docs/…`,
`chore/…` branches, squash-merged. Releases are annotated `vX.Y.Z` tags on `main`.

Unreleased work accumulates under `## [Unreleased]` in `CHANGELOG.md`. Cutting a release means
renaming that heading and bumping `VERSION_NAME`; the release workflow **refuses to publish a
version with no matching CHANGELOG section**, by design.

`scripts/prepare-release.sh` performs that edit — the bump in `kenv-plugin/gradle.properties`, the
changelog promotion and link refs, and the install snippets in `README.md` and `docs/`. It makes no
git-writing or network calls, so `./scripts/prepare-release.sh patch --dry-run` shows the exact diff
without touching the worktree. `prepare-release.yml` runs it and opens the release pull request.

Branch policy lives in `.github/rulesets/*.json` and is applied by `scripts/setup-github-repo.sh`,
which refuses to apply a ruleset whose required checks name jobs that do not exist.

## CI / CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | PR and push to `main` | `plugin` and `demo` jobs on hosted runners |
| `prepare-release.yml` | manual | Step 1 of a release. Runs `scripts/prepare-release.sh` and opens the release pull request |
| `release.yml` | tag `v*`; `workflow_call`; manual with `dry_run` | The only path to a published release |
| `tag-and-release.yml` | push to `main` touching `kenv-plugin/gradle.properties` | Detects a `VERSION_NAME` change, tags, calls `release.yml` |
| `docs.yml` | docs push to `main`; `workflow_call`; manual | mdBook guide to GitHub Pages |

> **A tag pushed with `GITHUB_TOKEN` does not trigger workflows.** GitHub suppresses triggers for
> events raised by that token, to prevent recursion. `release.yml` and `docs.yml` are therefore
> `uses:`-called explicitly rather than left to `push: tags` or `release:` triggers. Reverting that
> makes releases stop happening *silently*.

`gradle/actions` is pinned at **v5** deliberately: v6 moves caching into `gradle-actions-caching`,
a proprietary component whose use requires accepting Gradle's commercial terms of use. That is a
licensing decision, not a version bump. v5 is already on Node 24.

mdBook is installed as a pinned binary rather than via `peaceiris/actions-mdbook`, whose released
`@v2` still declares `using: node20` with no newer tag to move to.

### Release secrets

| Secret | Gradle property |
|--------|-----------------|
| `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` |
| `GPG_KEY_CONTENTS` | `signingInMemoryKey` |
| `SIGNING_PASSWORD` | `signingInMemoryKeyPassword` |
| `SIGNING_KEY_ID` | `signingInMemoryKeyId` (last 8 characters of the fingerprint) |

## What gets published

Two artifacts, and both matter:

| | |
|---|---|
| `io.github.adventures92:kenv-config` | the plugin jar, POM, sources and javadoc |
| `io.github.adventures92.kenv-config.gradle.plugin` | the **plugin marker** — a tiny POM pointing at the above. This is what `plugins { id(...) }` resolves. |

`java-gradle-plugin` generates the marker automatically. The Gradle Plugin Portal's Maven repo
**proxies Maven Central**, so publishing to Central is sufficient for
`plugins { id("io.github.adventures92.kenv-config") version "..." }` to resolve with no extra
repository configuration — verified from an empty Gradle cache. A Portal *listing* would add
searchability only; it is not a prerequisite.

This project cannot be listed on klibs.io: that indexes Kotlin Multiplatform libraries, and this is
a `kotlin("jvm")` Gradle plugin with no `kotlin-tooling-metadata.json`.

## Documentation

`docs/` is an mdBook (`book.toml`, output `book/`, gitignored), published to GitHub Pages by
`docs.yml`. `docs/SUMMARY.md` is the table of contents — a page not listed there is not built.

It is hand-written prose and the compiler does not read it. When you change behaviour, search for
the concept, not just the identifier.

## Agent tooling

`.agents/` is the vendor-neutral home for skills. `.claude/skills` is a **committed symlink** to
`.agents/skills` — git stores it as mode `120000`, so it resolves on clone with no bootstrap step.

| Skill | Purpose |
|-------|---------|
| [`changing-the-plugin`](.agents/skills/changing-the-plugin/SKILL.md) | What travels with a code change |
| [`release`](.agents/skills/release/SKILL.md) | Cut a release and verify it actually resolved |

Skills are an **authoring aid, never a pipeline dependency**. No workflow may require an agent to
run: a release must be reproducible by anyone holding the secrets.
