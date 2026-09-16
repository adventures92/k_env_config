@AGENTS.md

# CLAUDE.md

`AGENTS.md` is the canonical, vendor-neutral contract for this repository and is imported above.
**Follow it. Do not duplicate any of it here** — architecture, invariants, commands, branching,
release process and CI all live there, and a second copy only drifts.

If the `@AGENTS.md` import is not supported by the Claude surface you are running on, open
`AGENTS.md` directly before doing anything else.

Skills live in [`.agents/skills/`](.agents/skills/), reachable as `.claude/skills` through a
committed symlink.

## Claude Code harness specifics

Only things that are true of *this harness* belong here. Everything else goes in `AGENTS.md`.

- **`gh` resolves the wrong account in a non-interactive shell.** This machine picks a GitHub
  account per repository via a `gh` wrapper in `~/.config/zsh/gh-account.zsh`, which a
  non-interactive shell does not load — it falls through to the default account, which has no
  write access here. Export the config directory explicitly:
  `export GH_CONFIG_DIR="$HOME/.config/gh-personal"`.
- **Commits must not use the global git identity.** The global config is a work address; this
  repository has a local override to
  `adventures92 <48243629+adventures92@users.noreply.github.com>`. Check
  `git config --local user.email` before the first commit in a fresh clone.
- **`python3` is the Xcode Command Line Tools stub** and is blocked by an unaccepted Xcode licence.
  Use `jq`, `yq`, `awk` or `node`.
- **`ANDROID_HOME` is unset here.** Prefix demo-app tasks with
  `ANDROID_HOME="$HOME/Library/Android/sdk"`, or write `sdk.dir` into a local `local.properties`.
- **Run long Gradle invocations in the background.** A cold `:composeApp:assembleDebug` takes
  around half a minute; the plugin suite is faster.
