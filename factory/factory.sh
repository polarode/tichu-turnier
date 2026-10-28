#!/usr/bin/env bash
# Dark factory container entrypoint.
#
# Phases: implement -> verify (wrapper) -> fix-up (if red) -> review -> push -> PR.
# Every phase is time-boxed by FACTORY_TIMEOUT_M. Any failure exits non-zero;
# the cron-side runner (run-factory.sh) turns that into a `factory-failed`
# relabel + issue comment.
set -uo pipefail

log() { echo "[factory] $(date -u +%H:%M:%SZ) $*"; }
die() { echo "[factory][FATAL] $*" >&2; exit 1; }

# --- required environment ----------------------------------------------------
: "${FACTORY_REPO_URL:?FACTORY_REPO_URL is required}"
: "${GH_TOKEN:?GH_TOKEN (the factory PAT) is required}"
: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
: "${ISSUE_ID:?ISSUE_ID is required}"

REPO_URL="${FACTORY_REPO_URL}"
MODEL="${FACTORY_MODEL:-z-ai/glm-5.3-flash}"
REVIEW_MODEL="${FACTORY_REVIEW_MODEL:-$MODEL}"
TIMEOUT_MIN="${FACTORY_TIMEOUT_M:-15}"
BRANCH="factory/issue-${ISSUE_ID}"
GIT_AUTHOR_NAME="${GIT_AUTHOR_NAME:-Tichu Factory Bot}"
GIT_AUTHOR_EMAIL="${GIT_AUTHOR_EMAIL:-$(printf '%s@%s' factory-bot users.noreply.github.com)}"
SKILL_IMPLEMENT="/opt/factory/skills/factory-implement/SKILL.md"
SKILL_REVIEW="/opt/factory/skills/code-review/SKILL.md"

# gh CLI picks up GH_TOKEN automatically; keep git quiet and identity set.
export GH_TOKEN
git config --global --replace-all url "https://x-access-token:${GH_TOKEN}@github.com/".insteadOf "https://github.com/"
git config --global user.name  "${GIT_AUTHOR_NAME}"
git config --global user.email "${GIT_AUTHOR_EMAIL}"
git config --global --add safe.directory /workspace

run_pi() { # run_pi <timeout-min> <model> <prompt>
  local minutes="$1" model="$2" prompt="$3"
  timeout --kill-after=60 "${minutes}m" \
    pi --print --no-session \
       --provider openrouter --model "$model" \
       "$prompt"
}

# --- clone & branch ----------------------------------------------------------
log "cloning ${REPO_URL}"
git clone --no-tags "${REPO_URL}" /workspace || die "clone failed"
cd /workspace || die "workspace missing"

log "creating branch ${BRANCH} from origin/main"
git fetch origin main
git checkout -B "$BRANCH" origin/main || die "branch creation failed"

# --- phase 1: implement ------------------------------------------------------
log "phase 1: implement (issue ${ISSUE_ID}, model ${MODEL}, max ${TIMEOUT_MIN}m)"
IMPL_PROMPT="ISSUE: ${ISSUE_ID}
Read and follow the skill file at ${SKILL_IMPLEMENT} exactly. Work in this repository."
IMPL_OUT="$(run_pi "$TIMEOUT_MIN" "$MODEL" "$IMPL_PROMPT")" \
  || die "implement phase failed or timed out (exit $?)"
echo "$IMPL_OUT"
case "$IMPL_OUT" in
  BLOCKED:*) die "agent reports the issue is underspecified: ${IMPL_OUT%%$'\n'*}" ;;
esac

# --- phase 2: verify (wrapper runs the checks itself; trust but verify) ------
verify() {
  log "verify: backend ./gradlew test"
  ( cd backend && ./gradlew test ) && log "verify: backend OK" || return 1
  log "verify: frontend typecheck/lint/test"
  ( cd frontend && pnpm install && pnpm typecheck && pnpm lint && pnpm test ) \
    && log "verify: frontend OK" || return 1
  return 0
}

if ! verify; then
  log "verify failed; running one fix-up pass"
  FIXUP_PROMPT="The repository at $(pwd) failed its checks after implementing issue ${ISSUE_ID}.
Run the checks to see the failures, fix them (backend: ./gradlew test; frontend: pnpm typecheck, pnpm lint, pnpm test),
amend or add commits (Conventional Commits, refs #${ISSUE_ID}), do not push.
Only touch what is needed to make the checks pass."
  run_pi "$TIMEOUT_MIN" "$MODEL" "$FIXUP_PROMPT" >/dev/null \
    || die "fix-up phase failed or timed out"
  verify || die "checks still red after fix-up pass"
fi

# --- phase 3: commit leftovers ------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
  git add -A
  git commit -m "feat: implement #${ISSUE_ID}" -m "Automated implementation by the dark factory.

 refs #${ISSUE_ID}"
fi
if [ "$(git rev-list --count origin/main..HEAD)" -eq 0 ]; then
  die "no commits produced on ${BRANCH}; nothing to review or push"
fi

# --- phase 4: fresh-context review --------------------------------------------
log "phase 4: code review (model ${REVIEW_MODEL}, max ${TIMEOUT_MIN}m)"
ISSUE_TEXT="$(gh issue view "${ISSUE_ID}" --json title,body -q '.title + "\n\n" + .body' 2>/dev/null || echo "(issue fetch failed)")"
REVIEW_PROMPT="You are reviewing an automated implementation in this repository.
Read and follow the skill file at ${SKILL_REVIEW} exactly.
Fixed point: origin/main (use 'git diff origin/main...HEAD').
Spec source: GitHub issue ${ISSUE_ID}, contents below between the markers.

--- ISSUE ${ISSUE_ID} START ---
${ISSUE_TEXT}
--- ISSUE ${ISSUE_ID} END ---

After aggregating findings: fix every legitimate finding yourself (follow the
repo AGENTS.md conventions), re-run the relevant checks (backend: ./gradlew test;
frontend: pnpm typecheck, pnpm lint, pnpm test), and commit the fixes
(Conventional Commits, refs #${ISSUE_ID}). Do NOT push and do NOT create a PR.
If a finding is a false positive or pure nit, skip it and say so.
End your output with the aggregated review report (Standards / Spec), what you fixed,
and then a short PR body with the headings '## What', '## How verified', '## Notes'."
REVIEW_OUT="$(run_pi "$TIMEOUT_MIN" "$REVIEW_MODEL" "$REVIEW_PROMPT")" \
  || die "review phase failed or timed out (exit $?)"
echo "$REVIEW_OUT"

if [ -n "$(git status --porcelain)" ]; then
  git add -A
  git commit -m "fix: apply review findings for #${ISSUE_ID}" -m " refs #${ISSUE_ID}"
fi

# --- phase 5: push & PR --------------------------------------------------------
log "phase 5: push ${BRANCH} and open PR"
git push --force origin "$BRANCH" || die "push failed"

PR_BODY=""
if printf '%s' "$REVIEW_OUT" | grep -q '^## What'; then
  PR_BODY="Closes #${ISSUE_ID}

${REVIEW_OUT}"
else
  PR_BODY="Closes #${ISSUE_ID}

## What
See the commit list.

## How verified
Wrapper ran backend tests and frontend typecheck/lint/tests.

## Notes
${REVIEW_OUT}"
fi

REPO_SLUG="$(git remote get-url origin | sed -E 's#.*github.com[:/]##; s/\.git$//')"
if ! gh pr create \
     --repo "$REPO_SLUG" \
     --head "$BRANCH" --base main \
     --title "$(gh issue view "${ISSUE_ID}" --json title -q .title)" \
     --body "$PR_BODY"; then
  die "PR creation failed (branch was pushed)"
fi

log "done: PR open for issue ${ISSUE_ID}; awaiting human review"
