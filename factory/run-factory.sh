#!/usr/bin/env bash
# Dark factory cron-side runner. Deployed (manually) to the VPS; invoked by the
# Coolify scheduled task under flock:
#
#   flock -n /tmp/tichu-factory.lock /opt/tichu-factory/factory/run-factory.sh
#
# Per tick: find the oldest open issue labeled ready-for-factory-implementation
# authored by the configured user; if any, claim it (relabel), pull the factory
# image, run the container, and on failure relabel to factory-failed with a
# comment. Idle ticks exit 0 and do nothing. Requires: bash, curl, jq, docker.
#
# Flags: --dry-run  validate config and show what would run, without Docker.
set -uo pipefail

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

log() { echo "[factory-run] $(date -u '+%Y-%m-%dT%H:%M:%SZ') $*"; }
die() { echo "[factory-run][FATAL] $*" >&2; exit 1; }

# --- configuration ------------------------------------------------------------
: "${FACTORY_REPO_URL:?FACTORY_REPO_URL is required}"
: "${FACTORY_GH_TOKEN:?FACTORY_GH_TOKEN is required}"
: "${FACTORY_AUTHOR:?FACTORY_AUTHOR (GitHub username whose issues are processed) is required}"

IMAGE="${FACTORY_IMAGE:-ghcr.io/polarode/tichu-turnier/factory:latest}"
LABEL_READY="${FACTORY_LABEL_READY:-ready-for-factory-implementation}"
LABEL_IN_PROGRESS="${FACTORY_LABEL_IN_PROGRESS:-factory-in-progress}"
LABEL_FAILED="${FACTORY_LABEL_FAILED:-factory-failed}"
LOG_DIR="${FACTORY_LOG_DIR:-/opt/tichu-factory/factory-logs}"
API="https://api.github.com"

REPO_SLUG="$(printf '%s' "$FACTORY_REPO_URL" | sed -E 's#.*github.com[:/]##; s/\.git$//')"
[ "$REPO_SLUG" != "$FACTORY_REPO_URL" ] || die "FACTORY_REPO_URL does not look like a GitHub URL"

command -v curl >/dev/null || die "curl is required"
command -v jq >/dev/null || die "jq is required"

# --- github api helpers ---------------------------------------------------------
api() { # api <method> <path> [json-body]
  local method="$1" path="$2" body="${3:-}"
  local args=(curl -fsS -X "$method" "$API$path"
    -H "Authorization: Bearer $FACTORY_GH_TOKEN"
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28")
  [ -n "$body" ] && args+=(-d "$body")
  "${args[@]}"
}

ensure_labels() {
  local label color
  for spec in "$LABEL_READY:0e8a16" "$LABEL_IN_PROGRESS:fbca04" "$LABEL_FAILED:b60205"; do
    label="${spec%%:*}" color="${spec##*:}"
    BODY="$(jq -n --arg n "$label" --arg c "$color" '{name:$n,color:$c}')"
    api POST "/repos/$REPO_SLUG/labels" "$BODY" >/dev/null 2>&1 || true
  done
}

# --- pick & claim ----------------------------------------------------------------
log "looking for open issues labeled '$LABEL_READY' by '$FACTORY_AUTHOR' in $REPO_SLUG"

ISSUES="$(api GET "/repos/$REPO_SLUG/issues?labels=$(printf '%s' "$LABEL_READY" | jq -sRr @uri)&state=open&sort=created&direction=asc&per_page=50")" \
  || die "issue listing failed"
ISSUE_ID="$(printf '%s' "$ISSUES" | jq -r "[.[] | select(.pull_request == null and .user.login == \"$FACTORY_AUTHOR\")][0].number // empty")"

if [ -z "$ISSUE_ID" ]; then
  log "no claimable issue found; nothing to do"
  exit 0
fi

log "claimed issue #$ISSUE_ID"

# --- dry-run ------------------------------------------------------------------------
if [ "$DRY_RUN" = 1 ]; then
  log "DRY RUN - would now: relabel to '$LABEL_IN_PROGRESS', docker pull $IMAGE, then:"
  echo "docker run --rm --name tichu-factory-$ISSUE_ID \\"
  echo "  -e ISSUE_ID=$ISSUE_ID -e FACTORY_REPO_URL=... -e GH_TOKEN=... -e OPENROUTER_API_KEY=... \\"
  echo "  -v $LOG_DIR:/factory-logs $IMAGE"
  log "dry run complete (no state was changed)"
  exit 0
fi

ensure_labels

# claim: ready -> in progress (remove ready first; a crash between the two
# calls leaves the issue with neither label, which keeps it out of both pools)
api DELETE "/repos/$REPO_SLUG/issues/$ISSUE_ID/labels/$(printf '%s' "$LABEL_READY" | jq -sRr @uri)" >/dev/null \
  || die "could not remove label $LABEL_READY from #$ISSUE_ID"
api POST "/repos/$REPO_SLUG/issues/$ISSUE_ID/labels" "{\"labels\":[\"$LABEL_IN_PROGRESS\"]}" >/dev/null \
  || die "could not add label $LABEL_IN_PROGRESS to #$ISSUE_ID"

# --- run -------------------------------------------------------------------------------
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/issue-${ISSUE_ID}-$(date -u +%Y%m%dT%H%M%SZ).log"
log "running container; log: $LOG_FILE"

RC=0
docker pull "$IMAGE" >>"$LOG_FILE" 2>&1 || { RC=125; echo "[factory-run][FATAL] image pull failed" >>"$LOG_FILE"; }
if [ "$RC" -eq 0 ]; then
  docker run --rm --name "tichu-factory-$ISSUE_ID" \
    -e ISSUE_ID="$ISSUE_ID" \
    -e FACTORY_REPO_URL="$FACTORY_REPO_URL" \
    -e GH_TOKEN="$FACTORY_GH_TOKEN" \
    -e OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-}" \
    -e FACTORY_MODEL="${FACTORY_MODEL:-}" \
    -e FACTORY_REVIEW_MODEL="${FACTORY_REVIEW_MODEL:-}" \
    -e FACTORY_TIMEOUT_M="${FACTORY_TIMEOUT_M:-}" \
    -e GIT_AUTHOR_NAME="${GIT_AUTHOR_NAME:-}" \
    -e GIT_AUTHOR_EMAIL="${GIT_AUTHOR_EMAIL:-}" \
    -v "$LOG_DIR:/factory-logs" \
    "$IMAGE" >>"$LOG_FILE" 2>&1
  RC=$?
fi

if [ "${RC:-125}" -eq 0 ]; then
  log "run succeeded; PR should be open for #$ISSUE_ID"
  exit 0
fi

# --- failure reporting --------------------------------------------------------------------
# Redact any credential that might appear in git error output before it can
# reach the failure comment (the same log stays on the VPS volume for humans).
TAIL="$(sed -E 's/x-access-token:[^@[:space:]]*@/x-access-token:***@/g; s/gh[pousr]_[A-Za-z0-9_]+/**redacted**/g' <<<"$(tail -n 50 "$LOG_FILE")")"
FAILURE_BODY="The dark factory failed on this issue (exit code ${RC}).
Full log: \`${LOG_FILE}\` on the VPS.

<details><summary>Last 50 log lines</summary>

\`\`\`
${TAIL}
\`\`\`

</details>"

api DELETE "/repos/$REPO_SLUG/issues/$ISSUE_ID/labels/$LABEL_IN_PROGRESS" >/dev/null || true
api POST "/repos/$REPO_SLUG/issues/$ISSUE_ID/labels" "{\"labels\":[\"$LABEL_FAILED\"]}" >/dev/null || true
api POST "/repos/$REPO_SLUG/issues/$ISSUE_ID/comments" "$(jq -n --arg b "$FAILURE_BODY" '{body:$b}')" >/dev/null \
  || log "warning: could not post failure comment on #$ISSUE_ID"

die "factory run for #$ISSUE_ID failed (exit ${RC}); issue relabeled to $LABEL_FAILED"
