## What changed

<!-- One or two sentences. What does this do that the previous behaviour did not? -->

## Why

<!-- The problem this solves. Link the issue if there is one: Fixes #123 -->

## Checklist

- [ ] `./gradlew kenv-plugin:test` passes
- [ ] The demo app still builds — `./gradlew :composeApp:assembleDebug`
- [ ] Entry added under `## [Unreleased]` in `CHANGELOG.md`

### If this changes generated output

- [ ] A test covers the new shape — generated code is the product, so a change to it is a
      behaviour change even when no API moved
- [ ] I have considered whether existing consumers' `EnvConfig` would stop compiling

### If this touches a parser (`.env`, YAML, TOML)

- [ ] A round-trip test covers it. Values are consumed as `String` throughout, so implicit
      typing must never rewrite them — see the YAML resolver note in `docs/`
- [ ] Malformed input produces a located error, not a stack trace

### If this touches the version catalog or a build script

- [ ] Versions come from `gradle/libs.versions.toml`, not inline. `kenv-plugin` is a separate
      build and reads the same catalog through its `settings.gradle.kts`

## Notes for the reviewer

<!-- Anything non-obvious: a trade-off you made, something you deliberately left out, a follow-up. -->
