# Dark factory

Autonomous implementation of `ready-for-factory-implementation` issues:
a cron job on the VPS (Coolify) picks up the oldest matching open issue
(authored by the operator only), runs a container from the factory image,
and the container implements → verifies → reviews → pushes `factory/issue-<id>`
and opens a PR. Human review and human merge are mandatory. Failures relabel
the issue `factory-failed` with a comment; no automatic retries.

Spec: `docs/spec/dark-factory.md`.

## Components

| File | Runs where | Role |
|---|---|---|
| `Dockerfile` | CI (GH Actions) | Image: JDK 21, Node 22, corepack/pnpm, gh, ripgrep, pi |
| `factory.sh` | inside container | Entrypoint: implement → verify → fix-up → review → push → PR |
| `run-factory.sh` | VPS (cron) | Pick + claim issue, pull image, run container, report failures |
| `skills/factory-implement/` | container | Lean headless implementation skill (no grilling, no gates) |
| `skills/code-review/` | container | Two-axis review skill (Standards/Spec), vendored from operator skills |
| `justfile` | VPS / local | `check`, `build-image`, `deploy-factory`, `dry-run` |

## One-time VPS bootstrap

1. Clone this repo: `git clone https://github.com/polarode/tichu-turnier /opt/tichu-factory`
2. Install on the VPS (if missing): `docker`, `curl`, `jq`, `flock` (util-linux), `just` (optional).
3. `cd /opt/tichu-factory && just deploy-factory` (installs `run-factory.sh` to `/opt/tichu-factory/run-factory.sh`; re-run after changing it — the script is **not** self-updating by design).
4. Create the fine-grained PAT (see below) and store the Coolify env vars (below).
5. In Coolify, create a **Scheduled Task** (cron) with command:

   ```
   flock -n /tmp/tichu-factory.lock /opt/tichu-factory/run-factory.sh
   ```

   Suggested interval: every 15 min. The lock makes overlapping ticks exit silently.

## PAT (minimal scope)

Fine-grained personal access token, **Repository access: only `polarode/tichu-turnier`**,
**Permissions:**
- Contents: **Read and write** (push the branch)
- Pull requests: **Read and write** (create the PR)
- Issues: **Read and write** (relabel + comment)

Nothing else. Expiry ≤ 90 days. Dedicated token — revoke independently of your personal one.
Store it in Coolify as `FACTORY_GH_TOKEN`; the container receives it as `GH_TOKEN`.

## Environment contract (Coolify task / server env)

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `FACTORY_REPO_URL` | yes | — | `https://github.com/polarode/tichu-turnier` |
| `FACTORY_GH_TOKEN` | yes | — | the scoped PAT above |
| `FACTORY_AUTHOR` | yes | — | your GitHub username (issue-author filter) |
| `OPENROUTER_API_KEY` | yes | — | OpenRouter key; keep balance manually capped |
| `FACTORY_MODEL` | no | `z-ai/glm-5.3-flash` | OpenRouter model slug for implement/fix-up |
| `FACTORY_REVIEW_MODEL` | no | `FACTORY_MODEL` | model for the review phase |
| `FACTORY_TIMEOUT_M` | no | `15` | wall-clock minutes per agent phase |
| `FACTORY_LABEL_READY` | no | `ready-for-factory-implementation` | trigger label |
| `FACTORY_LABEL_IN_PROGRESS` | no | `factory-in-progress` | claimed |
| `FACTORY_LABEL_FAILED` | no | `factory-failed` | failure terminal state |
| `FACTORY_LOG_DIR` | no | `/opt/tichu-factory/factory-logs` | host log volume |
| `FACTORY_IMAGE` | no | `ghcr.io/polarode/tichu-turnier/factory:latest` | image to run |
| `GIT_AUTHOR_NAME` / `GIT_AUTHOR_EMAIL` | no | `Tichu Factory Bot` / `factory-bot@users.noreply.github.com` | commit identity |

`FACTORY_CONCURRENCY` is a documented future knob; v1 is strictly single-lane (flock).

## Image delivery (GHCR)

`.github/workflows/factory-image.yml` builds and pushes
`ghcr.io/polarode/tichu-turnier/factory:{latest,sha-<commit>}` whenever
`/factory/**` changes on main (public package, no secrets inside). The cron
script pulls the image **only** when an issue was actually claimed.

## Testing / validation

- `just check` — bash syntax + shellcheck
- `FACTORY_REPO_URL=... FACTORY_GH_TOKEN=... FACTORY_AUTHOR=... just dry-run` —
  validates env, lists the claimable issue, prints the docker command; changes nothing
- Full end-to-end (real LLM spend, real PR) is accepted manually against a
  throwaway issue on first deployment.
