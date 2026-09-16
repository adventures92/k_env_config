---
name: release
description: Cut a KEnv Config release — pre-flight checks, tag, and verify the plugin is actually resolvable afterwards. Use when asked to release, publish, cut a version, or ship to Maven Central.
---

# Releasing KEnv Config

## Ground rules

- **Never tag or publish without explicit user consent.**
- A published version is permanent. Maven Central does not allow re-uploading a version or
  correcting a POM in place — `0.1.0` shipped with `scm` URLs pointing at a repository that does
  not exist, and that cannot be fixed, only superseded.
- Releases ship from `main`, never from a branch.

## 1. Pre-flight

```bash
./gradlew kenv-plugin:test
./gradlew :composeApp:kenvGenerate :composeApp:assembleDebug
```

Then check the things CI cannot:

- `CHANGELOG.md` has a `## [X.Y.Z] - YYYY-MM-DD` section, and it describes what a *user* would
  notice. Build and CI changes do not belong there.
- `kenv-plugin/gradle.properties` `VERSION_NAME` is the version you intend.
- The README's install snippet quotes that version.

## 2. Release

The automated path is two workflows. Dispatch `prepare-release.yml`, which runs
`scripts/prepare-release.sh` to bump `VERSION_NAME`, promote the `## [Unreleased]` changelog
heading, rewrite the link refs and the install snippets, and open a `Release X.Y.Z` pull request.
When that merges, `tag-and-release.yml` sees `VERSION_NAME` change, creates `vX.Y.Z`, and calls
`release.yml`.

```bash
gh workflow run prepare-release.yml -f bump=patch
```

See the diff first, without touching the worktree — the script makes no git-writing or network
calls, so this is the same rewrite the workflow would commit:

```bash
./scripts/prepare-release.sh patch --dry-run
```

To rehearse the publish itself without publishing:

```bash
gh workflow run release.yml -f version=X.Y.Z -f dry_run=true
```

## 3. Verify it actually landed

A green workflow is not proof. Central's own validation runs after the upload.

```bash
V=X.Y.Z
# the artifact
curl -sI "https://repo1.maven.org/maven2/io/github/adventures92/kenv-config/$V/kenv-config-$V.jar" \
  | head -1
# the plugin MARKER — this is what `plugins { id(...) }` resolves, and it is a separate artifact
curl -sI "https://repo1.maven.org/maven2/io/github/adventures92/kenv-config/io.github.adventures92.kenv-config.gradle.plugin/$V/io.github.adventures92.kenv-config.gradle.plugin-$V.pom" \
  | head -1
```

Then the only test that matters — resolve it as a stranger would, from an **empty** Gradle home so
no local cache can make a broken publish look fine:

```bash
mkdir -p /tmp/kenv-check && cd /tmp/kenv-check
cat > settings.gradle.kts <<'EOF'
rootProject.name = "check"
EOF
cat > build.gradle.kts <<'EOF'
plugins { id("io.github.adventures92.kenv-config") version "X.Y.Z" }
EOF
GRADLE_USER_HOME=/tmp/kenv-check/home gradle tasks --refresh-dependencies
```

Reaching the plugin's own error (`generatedPackageName is required`) means resolution worked.

## Traps that have actually bitten this repo

- **A tag pushed with `GITHUB_TOKEN` does not trigger workflows.** GitHub suppresses triggers for
  events raised by that token. `release.yml` and `docs.yml` are therefore `uses:`-called
  explicitly. If you ever "simplify" that back to a `push: tags` or `release:` trigger, releases
  stop happening silently.
- **`publishToMavenCentral` is not `publishAndReleaseToMavenCentral`.** The first uploads and waits
  for a manual Publish click in the portal; the run goes green and nothing is downloadable.
- **`kenv-plugin` is a separate build.** It does not see the root `gradle.properties` or the root
  version catalog. Both are wired explicitly. A change that assumes one build will fail confusingly.
- **The Plugin Portal proxies Maven Central.** Publishing to Central is sufficient for
  `plugins { id(...) version ... }` to resolve — verified from an empty cache. A Portal *listing*
  is discoverability only, not a prerequisite.

## After the release

- Confirm the GitHub release notes are the changelog section, not a generated commit list.
- Confirm Pages published — `docs.yml` is called by the release, but a Pages failure does not fail
  the publish, by design.
- Open the next `## [Unreleased]` heading in `CHANGELOG.md`.
