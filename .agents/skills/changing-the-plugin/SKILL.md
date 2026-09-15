---
name: changing-the-plugin
description: What to update alongside a change to kenv-plugin — tests, generated-output expectations, the guide, the changelog. Use when adding, renaming, removing or changing behaviour of anything in the plugin, before opening a pull request.
---

# Changing the plugin

A code change is rarely only a code change. This is what travels with it, and why.

## Start here

```bash
./gradlew kenv-plugin:test
./gradlew :composeApp:kenvGenerate :composeApp:assembleDebug
```

The demo app is not decoration — it is the only end-to-end proof that generation works against a
real consumer. A unit test can pass while the plugin produces something that does not compile.

## The thing most likely to catch you out

**`kenv-plugin` is a separate Gradle build.** The root includes it with
`includeBuild("kenv-plugin")`, which means it does **not** automatically see:

- the root version catalog — wired explicitly in `kenv-plugin/settings.gradle.kts`
- the root `gradle.properties` — `VERSION_NAME` therefore lives in `kenv-plugin/gradle.properties`

Both were verified by probe, not assumption: a property set only at the root reads back as `null`
inside the plugin build. If you add anything else that "should just be inherited", test it before
believing it.

## What each kind of change drags with it

### You changed generated output

**Generated code is the product.** A change to what `EnvConfig.kt` looks like is a behaviour change
even when no Kotlin API moved.

- A test covering the new shape.
- Ask whether an existing consumer's code stops compiling. Renaming a generated property is a
  breaking change for everyone using it.
- A `CHANGELOG.md` entry under `## [Unreleased]`.

### You touched a parser (`.env`, YAML, TOML)

- **Values are strings all the way through.** YAML's implicit resolver must never retype them —
  `FEATURE: on` means the string `on`, not `true`. `YamlEnvParser` disables implicit resolvers
  through a `StringOnlyResolver` and loads via `SafeConstructor` for exactly this reason. There are
  regression tests; do not weaken them.
- A round-trip test: parse, print, parse again, same values.
- Malformed input should produce a located, readable error, not a stack trace.

### You changed the schema format

- `docs/schema-reference.md` is the contract. It will not fail a build when it is wrong — it will
  just lie.
- Consider whether an existing schema still parses. Schema files live in users' repositories.

### You changed the `kenvConfig` DSL

- `docs/plugin-configuration.md`, and the README's quickstart if the change is visible there.
- Gradle property and provider wiring must stay lazy, or configuration cache support breaks.

### You added a dependency

- It goes in `gradle/libs.versions.toml` and is referenced as `libs.*`. Never inline a version in
  a build script.
- Remember it ships to every consumer of the plugin — a Gradle plugin's dependencies land on the
  build classpath of every project that applies it, where they can collide with other plugins.

### You changed CI or release plumbing

- A workflow triggered by something a workflow does will **not** fire: GitHub suppresses triggers
  for events raised by `GITHUB_TOKEN`. Use `workflow_call` from the producing workflow.
- A matrix job never reports its bare name — it reports `job (values)`. Requiring the bare name as
  a status check blocks every pull request forever.
- A required status check naming a job that does not exist waits forever. `scripts/setup-github-repo.sh`
  refuses to apply a ruleset with that mistake; run it with `--dry-run` after renaming any job.

## The docs are prose, and prose rots

`docs/` is hand-written and the compiler does not read it. When you change behaviour, search for
the concept, not just the identifier. The pages most likely to go quietly wrong are
`schema-reference.md` (the format), `plugin-configuration.md` (defaults) and
`variant-mapping.md` (Android behaviour).

## What not to put in the changelog

It is a product document, not a work log. CI fixes, refactors and build changes do not belong
there — git history holds them. Ask whether a user of the plugin could observe the change.
