#!/usr/bin/env bash
# Prepare a release: bump VERSION_NAME, promote the CHANGELOG's Unreleased section, rewrite the
# link refs, and point the install snippets at the new version.
#
# Makes no git-writing or network calls — prepare-release.yml commits the result and opens the pull
# request. Running it locally is the way to see the diff before dispatching the workflow.
#
#   ./scripts/prepare-release.sh patch|minor|major
#   ./scripts/prepare-release.sh --version 1.2.3            # explicit, skips the bump arithmetic
#   ./scripts/prepare-release.sh patch --dry-run            # rewrite a scratch copy, print the diff
#
# Refuses to do anything when the release would be wrong:
#   - no `## [Unreleased]` section
#   - the Unreleased section is empty -> a release with no changes, and empty GitHub Release notes
#   - the target tag already exists
#
# VERSION_NAME lives in kenv-plugin/gradle.properties, NOT the root one. kenv-plugin is a separate
# Gradle build and does not see the root properties file (AGENTS.md, "The repository is two Gradle
# builds"). tag-and-release.yml watches that exact path and reads the value back with
# `grep -E '^VERSION_NAME=' | head -1 | cut -d= -f2`, so the line must stay unindented, unspaced
# around the `=`, and first of its name. The verification block at the end asserts precisely that,
# using the same command — if this script and the workflow ever disagree, it fails here rather than
# merging a release pull request that silently tags nothing.

set -euo pipefail

SRC_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_URL="https://github.com/adventures92/k_env_config"
PLUGIN_ID="io.github.adventures92.kenv-config"

# Every file the script may rewrite, relative to the root. This doubles as the dry-run copy set, so
# the scratch copy and the real run can never drift apart.
#
# Deliberately excluded:
#   CHANGELOG.md          handled separately; it lists every version by definition
#   .agents/**            skill docs quote `version "X.Y.Z"` as an instruction template
#   AGENTS.md, CLAUDE.md  agent contracts; AGENTS.md quotes `version "..."` as prose
#   book/                 generated mdBook output, gitignored
TARGETS=(
    kenv-plugin/gradle.properties
    README.md
    kenv-plugin/README.md
    docs
)

info() { printf '\033[36m→\033[0m %s\n' "$1"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '\033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }
note() { printf '  \033[90m%s\033[0m\n' "$1"; }

# ------------------------------------------------------------------ arguments

BUMP=""
EXPLICIT=""
DRY_RUN=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        patch|minor|major) BUMP="$1"; shift ;;
        --version)         EXPLICIT="${2:-}"; [[ -n "$EXPLICIT" ]] || fail "--version needs a value"; shift 2 ;;
        --dry-run)         DRY_RUN=1; shift ;;
        *)                 fail "usage: $(basename "$0") patch|minor|major | --version X.Y.Z [--dry-run]" ;;
    esac
done
[[ -n "$BUMP" || -n "$EXPLICIT" ]] || fail "usage: $(basename "$0") patch|minor|major | --version X.Y.Z [--dry-run]"

# ------------------------------------------------------------------- scratch

# In dry-run everything below runs against a copy, so the worktree is never touched and the diff
# can be shown without needing `git checkout --` to undo a real rewrite.
ROOT="$SRC_ROOT"
if [[ "$DRY_RUN" == 1 ]]; then
    ROOT="$(mktemp -d)"
    trap 'rm -rf "$ROOT"' EXIT
    cp "$SRC_ROOT/CHANGELOG.md" "$ROOT/CHANGELOG.md"
    for t in "${TARGETS[@]}"; do
        mkdir -p "$ROOT/$(dirname "$t")"
        cp -R "$SRC_ROOT/$t" "$ROOT/$t"
    done
    info "dry run — rewriting a scratch copy, $SRC_ROOT is untouched"
fi

PROPS="$ROOT/kenv-plugin/gradle.properties"
CHANGELOG="$ROOT/CHANGELOG.md"

# -------------------------------------------------------------- current state

# The same read tag-and-release.yml performs.
CURRENT="$(grep -E '^VERSION_NAME=' "$PROPS" | head -1 | cut -d= -f2)"
[[ -n "$CURRENT" ]] || fail "no VERSION_NAME in kenv-plugin/gradle.properties"
[[ "$CURRENT" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "VERSION_NAME '$CURRENT' is not X.Y.Z"

if [[ -n "$EXPLICIT" ]]; then
    NEXT="$EXPLICIT"
    [[ "$NEXT" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "'$NEXT' is not a semantic version"
else
    IFS=. read -r MAJ MIN PAT <<< "$CURRENT"
    case "$BUMP" in
        major) NEXT="$((MAJ + 1)).0.0" ;;
        minor) NEXT="$MAJ.$((MIN + 1)).0" ;;
        patch) NEXT="$MAJ.$MIN.$((PAT + 1))" ;;
    esac
fi

info "$CURRENT → $NEXT"

# ------------------------------------------------------------------ guardrails

grep -q '^## \[Unreleased\]' "$CHANGELOG" || fail "CHANGELOG.md has no '## [Unreleased]' section"

# Everything between "## [Unreleased]" and the next "## [" heading.
UNRELEASED="$(awk '/^## \[Unreleased\]/{c=1;next} c&&/^## \[/{exit} c' "$CHANGELOG" | tr -d '[:space:]')"
[[ -n "$UNRELEASED" ]] || fail "the Unreleased section is empty — nothing to release
     Add entries under '## [Unreleased]' first. Releasing an empty section would publish a version
     with no changes and produce empty GitHub Release notes."

if git -C "$SRC_ROOT" rev-parse -q --verify "refs/tags/v$NEXT" >/dev/null 2>&1; then
    fail "tag v$NEXT already exists locally"
fi
grep -q "^## \[$NEXT\]" "$CHANGELOG" && fail "CHANGELOG.md already has a '## [$NEXT]' section"

ok "Unreleased has content, v$NEXT is free"

# -------------------------------------------------------------------- rewrite

TODAY="$(date -u +%Y-%m-%d)"

# Not `sed -i`: GNU and BSD disagree on whether it takes a suffix argument, and this runs on an
# Ubuntu runner as well as on a Mac. awk to a temp file behaves identically on both.
rewrite() {
    local file="$1"; shift
    awk "$@" "$file" > "$file.tmp" && mv "$file.tmp" "$file"
}

# 1. VERSION_NAME. Anchored so it cannot match a comment or another key, and emitted in exactly the
#    `VERSION_NAME=X.Y.Z` shape tag-and-release.yml greps for.
rewrite "$PROPS" -v v="$NEXT" '{ if ($0 ~ /^VERSION_NAME=/) print "VERSION_NAME=" v; else print }'
ok "VERSION_NAME=$NEXT"

# 2. Promote Unreleased, leaving a fresh empty one above it.
rewrite "$CHANGELOG" -v v="$NEXT" -v d="$TODAY" '
    /^## \[Unreleased\]/ && !done {
        print "## [Unreleased]"
        print ""
        print "## [" v "] - " d
        done = 1
        next
    }
    { print }
'
ok "promoted Unreleased → [$NEXT] - $TODAY"

# 3. Link refs: repoint Unreleased at the new tag and add a ref for it.
rewrite "$CHANGELOG" -v v="$NEXT" -v url="$REPO_URL" '
    /^\[Unreleased\]:/ {
        print "[Unreleased]: " url "/compare/v" v "...HEAD"
        print "[" v "]: " url "/releases/tag/v" v
        next
    }
    { print }
'
ok "link refs updated"

# 4. Install snippets in user-facing markdown. The plugin is installed two ways and both carry a
#    version, so both are rewritten:
#
#      [versions]
#      kenvConfig = "<latest>"                                     <- version catalog
#      id("io.github.adventures92.kenv-config") version "<latest>" <- direct application
#
#    The matched value is `<latest>` or a semantic version, never `[^"]*`. That is deliberate:
#    AGENTS.md writes `version "..."` and the release skill writes `version "X.Y.Z"`, both as prose
#    placeholders, and a loose pattern would happily "bump" those into nonsense if the scope above
#    were ever widened. Scope and pattern are independent guards.
snippet_files=()
while IFS= read -r f; do
    [[ -n "$f" ]] && snippet_files+=("$f")
done < <(
    grep -rlE "(^[[:space:]]*kenvConfig[[:space:]]*=[[:space:]]*\"(<latest>|[0-9]+\.[0-9]+\.[0-9]+)\")|(id\(\"$PLUGIN_ID\"\) version \"(<latest>|[0-9]+\.[0-9]+\.[0-9]+)\")" \
        --include='*.md' -- "${TARGETS[@]/#/$ROOT/}" 2>/dev/null || true
)

if [[ ${#snippet_files[@]} -gt 0 ]]; then
    for f in "${snippet_files[@]}"; do
        rewrite "$f" -v v="$NEXT" -v id="$PLUGIN_ID" '
            {
                # Direct application: id("<plugin id>") version "X.Y.Z"
                gsub("id\\(\"" id "\"\\) version \"(<latest>|[0-9]+[.][0-9]+[.][0-9]+)\"",
                     "id(\"" id "\") version \"" v "\"")
                # Version catalog: a [versions] entry, which is a bare quoted string. The [plugins]
                # entry of the same name is `kenvConfig = { id = ... }` and cannot match.
                if ($0 ~ /^[ \t]*kenvConfig[ \t]*=[ \t]*"(<latest>|[0-9]+[.][0-9]+[.][0-9]+)"[ \t]*$/) {
                    sub(/"(<latest>|[0-9]+[.][0-9]+[.][0-9]+)"/, "\"" v "\"")
                }
                print
            }
        '
        printf '    %s\n' "${f#"$ROOT"/}"
    done
    ok "install snippets rewritten to $NEXT"
else
    note "no install snippets found to rewrite"
fi

# -------------------------------------------------------------------- verify

# Sockit runs scripts/check-docs.sh here. This repository has no such checker, so the invariants
# that actually matter for the handover are asserted inline instead. Each one has failed somewhere
# before: a version the next workflow cannot see, a changelog section the release gate rejects, and
# a README advertising the previous release for a whole cycle.

# The exact command tag-and-release.yml runs. If this does not return $NEXT, the merge tags nothing
# and the release silently never happens.
DETECTED="$(grep -E '^VERSION_NAME=' "$PROPS" | head -1 | cut -d= -f2)"
[[ "$DETECTED" == "$NEXT" ]] \
    || fail "tag-and-release.yml would read VERSION_NAME as '$DETECTED', not '$NEXT'"

# The backstop in tag-and-release.yml, and the gate in release.yml.
grep -q "^## \[$NEXT\]" "$CHANGELOG" || fail "CHANGELOG.md has no '## [$NEXT]' section after the rewrite"
grep -q '^## \[Unreleased\]' "$CHANGELOG" || fail "the '## [Unreleased]' heading was consumed, not promoted"

# release.yml extracts the notes with the same awk; empty notes fail the release there.
NOTES="$(awk -v v="$NEXT" '$0 ~ "^## \\[" v "\\]" {c=1;next} c&&/^## \[/{exit} c' "$CHANGELOG" | tr -d '[:space:]')"
[[ -n "$NOTES" ]] || fail "the new [$NEXT] section is empty — release.yml would refuse it"

grep -q "^\[Unreleased\]: $REPO_URL/compare/v$NEXT\.\.\.HEAD$" "$CHANGELOG" \
    || fail "the [Unreleased] link ref does not point at v$NEXT"
grep -q "^\[$NEXT\]: $REPO_URL/releases/tag/v$NEXT$" "$CHANGELOG" \
    || fail "no link ref was added for [$NEXT]"

# Nothing user-facing may still advertise the version we just moved off, or the placeholder.
stale="$(grep -rnE "(^[[:space:]]*kenvConfig[[:space:]]*=[[:space:]]*\"(<latest>|$CURRENT)\")|(id\(\"$PLUGIN_ID\"\) version \"(<latest>|$CURRENT)\")" \
    --include='*.md' -- "${TARGETS[@]/#/$ROOT/}" 2>/dev/null || true)"
[[ -z "$stale" ]] || fail "install snippets still quote the old version:
$stale"

ok "release invariants hold for $NEXT"

# ------------------------------------------------------------------ handover

if [[ "$DRY_RUN" == 1 ]]; then
    echo
    info "diff that a real run would produce"
    echo
    # Per file rather than per target, so a directory target still labels each file by its own
    # repository-relative path instead of the scratch directory's name.
    while IFS= read -r rel; do
        diff -u --label "a/$rel" --label "b/$rel" "$SRC_ROOT/$rel" "$ROOT/$rel" || true
    done < <(
        printf '%s\n' CHANGELOG.md
        for t in "${TARGETS[@]}"; do
            if [[ -d "$ROOT/$t" ]]; then
                find "$ROOT/$t" -type f | sed "s|^$ROOT/||" | sort
            else
                printf '%s\n' "$t"
            fi
        done
    )
    echo
    ok "dry run complete — nothing was written to $SRC_ROOT"
    exit 0
fi

# prepare-release.yml reads these to title the branch, commit and pull request.
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
        echo "current=$CURRENT"
        echo "version=$NEXT"
        echo "date=$TODAY"
    } >> "$GITHUB_OUTPUT"
fi

echo
ok "prepared $NEXT — review the diff, then commit"
