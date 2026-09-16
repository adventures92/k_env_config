#!/usr/bin/env bash
# Applies this repository's GitHub settings and branch policy.
#
# Idempotent — safe to re-run after changing the policy. Requires the GitHub CLI and jq:
#
#   gh auth login
#   ./scripts/setup-github-repo.sh --dry-run     # print every call, change nothing
#   ./scripts/setup-github-repo.sh               # apply
#
# What it configures:
#   1. Repository metadata — description, homepage, topics
#   2. Merge policy        — squash only, auto-delete merged branches
#   3. Issue labels        — .github/labels.json
#   4. Rulesets            — every file in .github/rulesets/*.json
#   5. Pages environment   — allows release tags (v*) to deploy documentation
#
# The ruleset JSON files are the single source of truth and can equally be imported by hand:
# Settings -> Rules -> Rulesets -> New ruleset -> Import a ruleset.
#
# It prints, but does not set, the things that need a secret value or a deliberate decision.

set -euo pipefail

REPO="${KENV_REPO:-adventures92/k_env_config}"
DEFAULT_BRANCH="main"

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CI_WORKFLOW="$ROOT/.github/workflows/ci.yml"
RULESET_DIR="$ROOT/.github/rulesets"
BRANCH_RULESET="$RULESET_DIR/protect-main.json"
LABELS_FILE="$ROOT/.github/labels.json"

info() { printf '\033[36m→\033[0m %s\n' "$1"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '\033[33m!\033[0m %s\n' "$1"; }
fail() { printf '\033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

run() {
    if (( DRY_RUN )); then
        printf '  \033[90mwould run:\033[0m gh %s\n' "$*"
    else
        gh "$@" >/dev/null
    fi
}

# ---------------------------------------------------------------- preflight

command -v gh >/dev/null 2>&1 || fail "GitHub CLI not found — https://cli.github.com"
command -v jq >/dev/null 2>&1 || fail "jq not found — https://jqlang.github.io/jq/"

if (( DRY_RUN )); then
    # A dry run is most useful *before* the repo exists, so neither auth nor the repo is required.
    gh auth status >/dev/null 2>&1 || warn "Not authenticated — dry run continues anyway"
    gh repo view "$REPO" >/dev/null 2>&1 || warn "$REPO not visible yet — dry run continues anyway"
else
    gh auth status >/dev/null 2>&1 || fail "Not authenticated. Run: gh auth login"
    gh repo view "$REPO" >/dev/null 2>&1 \
        || fail "Cannot see $REPO. Create it first, or set KENV_REPO=<owner>/<name>."
fi

# A required check whose context is never reported makes the rule wait forever, so no pull request
# can ever merge. Two ways to get that wrong, both checked here:
#
#   1. the context names no job at all;
#   2. the context names a MATRIX job by its bare key. GitHub reports one check run per cell, named
#      "job (v1, v2)" — the bare name never appears. Requiring it blocks every PR permanently.
if [[ -f "$CI_WORKFLOW" && -f "$BRANCH_RULESET" ]]; then
    count=0
    while IFS= read -r check; do
        [[ -n "$check" ]] || continue
        # Strip any " (matrix, values)" suffix to recover the job key.
        job="${check%% (*}"
        grep -qE "^  ${job}:" "$CI_WORKFLOW" \
            || fail "Required check '$check' names no job in $(basename "$CI_WORKFLOW")"

        # Does that job declare a matrix?
        if yq -e ".jobs.\"${job}\".strategy.matrix" "$CI_WORKFLOW" >/dev/null 2>&1; then
            [[ "$check" == *"("* ]] || fail \
"Required check '$check' is a MATRIX job — GitHub reports it as '$job (<values>)', never as
     '$job' on its own, so requiring the bare name blocks every pull request forever.
     List each cell explicitly in $(basename "$BRANCH_RULESET")."
        fi
        count=$(( count + 1 ))
    done < <(jq -r '.rules[] | select(.type=="required_status_checks")
                    | .parameters.required_status_checks[].context' "$BRANCH_RULESET")
    ok "All $count required checks name reportable jobs in $(basename "$CI_WORKFLOW")"
else
    warn "Workflow or branch ruleset missing — skipping check-name validation"
fi

info "Target: $REPO (default branch: $DEFAULT_BRANCH)"
(( DRY_RUN )) && warn "Dry run — nothing will be changed"

# ------------------------------------------------------- 1. repo metadata

info "Repository metadata"
run repo edit "$REPO" \
    --description "Type-safe, schema-driven environment configuration for Kotlin Multiplatform and Android" \
    --homepage "https://adventures92.github.io/k_env_config/" \
    --add-topic kotlin \
    --add-topic gradle-plugin \
    --add-topic kotlin-multiplatform \
    --add-topic android \
    --add-topic environment-variables \
    --add-topic configuration \
    --add-topic code-generation \
    --add-topic dotenv
ok "Description, homepage and topics set"

# -------------------------------------------------------- 2. merge policy

info "Merge policy"
run repo edit "$REPO" \
    --default-branch "$DEFAULT_BRANCH" \
    --enable-squash-merge \
    --enable-merge-commit=false \
    --enable-rebase-merge=false \
    --delete-branch-on-merge \
    --enable-issues \
    --enable-wiki=false \
    --enable-projects=false
ok "Squash-only merges, branches deleted on merge, wiki and projects off"

# -------------------------------------------------------------- 3. labels

apply_labels() {
    [[ -f "$LABELS_FILE" ]] || { warn "No .github/labels.json — skipping labels"; return; }

    info "Issue labels (${LABELS_FILE#"$ROOT"/})"
    local created=0 updated=0 name color description
    while IFS=$'\t' read -r name color description; do
        [[ -n "$name" ]] || continue
        if (( DRY_RUN )); then
            printf '  \033[90mwould ensure:\033[0m %-24s #%s\n' "$name" "$color"
            continue
        fi
        if gh label create "$name" --repo "$REPO" --color "$color" \
                --description "$description" >/dev/null 2>&1; then
            created=$(( created + 1 ))
        elif gh label edit "$name" --repo "$REPO" --color "$color" \
                --description "$description" >/dev/null 2>&1; then
            updated=$(( updated + 1 ))
        else
            warn "Could not apply label '$name'"
        fi
    done < <(jq -r '.[] | [.name, .color, (.description // "")] | @tsv' "$LABELS_FILE")
    (( DRY_RUN )) || ok "Labels: $created created, $updated updated"
}

apply_labels

# ------------------------------------------------------------ 4. rulesets

apply_ruleset() {
    local file="$1" name existing
    name="$(jq -r '.name' "$file")"

    info "Ruleset '$name' (${file#"$ROOT"/})"
    if (( DRY_RUN )); then
        printf '  \033[90mwould PUT/POST:\033[0m\n'
        jq '.' "$file" | sed 's/^/    /'
        return
    fi

    existing="$(gh api "repos/$REPO/rulesets" --jq ".[] | select(.name==\"$name\") | .id" 2>/dev/null || true)"
    if [[ -n "$existing" ]]; then
        gh api --method PUT "repos/$REPO/rulesets/$existing" --input "$file" >/dev/null
        ok "Updated existing ruleset '$name' (id $existing)"
    else
        gh api --method POST "repos/$REPO/rulesets" --input "$file" >/dev/null
        ok "Created ruleset '$name'"
    fi
}

shopt -s nullglob
rulesets=("$RULESET_DIR"/*.json)
shopt -u nullglob

if (( ${#rulesets[@]} == 0 )); then
    warn "No ruleset files in .github/rulesets/"
else
    for ruleset in "${rulesets[@]}"; do
        apply_ruleset "$ruleset"
    done
fi

# --------------------------------------------- 5. pages deployment from tags

# The github-pages environment restricts which refs may deploy to it. Its default allows only the
# default branch, and a release runs on a TAG — so docs.yml builds fine and then the deploy job is
# rejected by the environment, with no steps and no log to explain it.
#
# That is exactly what happened on v0.2.0: docs/build succeeded, docs/deploy failed, and the whole
# release run reported failure despite having published to Maven Central. A rehearsal cannot catch
# it, because workflow_dispatch runs on a branch and only a real release runs on a tag.
#
# Additive and idempotent: the branch policy is left alone, and re-adding an existing tag policy is
# a no-op.

info "GitHub Pages deployments from release tags"
if (( DRY_RUN )); then
    printf '  \033[90mwould ensure:\033[0m tag policy "v*" on the github-pages environment\n'
elif ! gh api "repos/$REPO/environments/github-pages" >/dev/null 2>&1; then
    warn "github-pages environment does not exist yet — enable Pages (Settings > Pages > Source:
     GitHub Actions), then re-run. Until then a release cannot publish documentation."
elif gh api "repos/$REPO/environments/github-pages/deployment-branch-policies" \
        --jq '.branch_policies[] | select(.type=="tag" and .name=="v*") | .name' 2>/dev/null | grep -q 'v\*'; then
    ok "Tag policy 'v*' already allowed to deploy to github-pages"
elif gh api --method POST "repos/$REPO/environments/github-pages/deployment-branch-policies" \
        -f name='v*' -f type='tag' >/dev/null 2>&1; then
    ok "Allowed release tags (v*) to deploy to github-pages"
else
    warn "Could not add the tag policy. If the environment uses 'protected branches only', switch it
     to custom policies first, or docs will not publish on a release."
fi

# ------------------------------------------------------------- summary

echo
if (( DRY_RUN )); then
    warn "Dry run complete — re-run without --dry-run to apply"
else
    ok "Done. Review at https://github.com/$REPO/settings/rules"
fi

cat <<'NOTE'

Still manual — these need secret values or a UI toggle, and this script never handles secrets:

  1. Release secrets — Settings > Secrets and variables > Actions > New repository secret
       MAVEN_CENTRAL_USERNAME   user token username   (central.sonatype.com/usertoken)
       MAVEN_CENTRAL_PASSWORD   user token password
       GPG_KEY_CONTENTS         gpg --armor --export-secret-keys <KEY_ID>
       SIGNING_PASSWORD         the key's passphrase
       SIGNING_KEY_ID           LAST 8 characters of the key fingerprint

     The io.github.adventures92 namespace is already verified — kenv-config 0.1.0 is on Central —
     so no new namespace registration is needed. Reuse the existing key and token if you have them.

  2. GitHub Pages — Settings > Pages > Source: GitHub Actions   (required by docs.yml)

     Re-run this script afterwards: the github-pages environment only exists once Pages is
     enabled, and step 5 above needs it to allow deployments from release tags.

  3. Allow Actions to open pull requests — Settings > Actions > General > Workflow permissions:
     "Read and write permissions" AND "Allow GitHub Actions to create and approve pull requests"

     prepare-release.yml pushes the release branch and then opens the pull request. Without this
     the push succeeds and the `gh pr create` fails with "GitHub Actions is not permitted to create
     or approve pull requests", leaving a branch and no pull request.

     Deliberately not set by this script. The same toggle also lets workflows APPROVE pull
     requests, which weakens branch protection, so it should be a decision rather than a side
     effect of running setup.

  4. Enable Discussions — the issue-template chooser links to it.
NOTE
