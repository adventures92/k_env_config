# Security policy

## Supported versions

Only the latest released version receives fixes. KEnv Config is pre-1.0; expect breaking changes
between minor versions.

## Reporting a vulnerability

Report privately through GitHub's
[security advisory form](https://github.com/adventures92/k_env_config/security/advisories/new)
rather than a public issue. Expect an acknowledgement within a week.

Useful detail: affected version, Gradle version, the env file format involved, and a minimal
reproduction. **Redact real secrets** — the shape of a value is enough to reproduce a parsing bug.

## Known sharp edges

**Generated config is baked into your binary.** `EnvConfig.kt` is ordinary Kotlin source compiled
into your application. Anything you put in an env file and generate from is recoverable by anyone
who has the artifact — decompiling an APK or JAR is trivial. This plugin is for *configuration*
(endpoints, feature flags, build-variant differences), not for secrets that must stay secret.
A key that must not leak belongs on a server, or in a platform keystore fetched at runtime.

**Env files must not be committed.** The plugin checks `.gitignore` and warns, but a warning is
not a guarantee. Verify with `git check-ignore` before your first commit, and treat any secret
that has ever been committed as compromised.

**Schema and env files are build inputs that become compiled code.** Treat them with the same
trust as a build script, not as user data. SnakeYAML 2.x refuses global tags by default, so
arbitrary type instantiation from an explicit `!!` tag is not reachable.
