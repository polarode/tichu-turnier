# Spec: Dark Factory (autonomous issue implementation)

## Problem Statement

Marcel wants to close `ready-for-agent` issues without personally driving every
implementation session. Each issue still requires: opening a session, reading the
issue, implementing, running checks, reviewing the result, pushing a branch, and
opening a PR. That is hours of pipeline work per issue that a machine could run
end-to-end while he does the parts only a human can do: specifying issues and
reviewing the resulting PRs.

## Solution

A "dark factory": a Docker image plus a cron-driven runner on the VPS (hosted in
Coolify) that watches for issues labeled `ready-for-factory-implementation`
(authored by Marcel only). When one is found, the runner claims it, spawns a
container from the factory image, and the container autonomously:

1. clones the repo, creates `factory/issue-<id>` from `origin/main`,
2. runs the pi coding agent headlessly with a purpose-built
   `factory-implement` skill to implement the issue,
3. independently re-runs the verification suite (backend tests, frontend
   typecheck/lint/tests), with one agent fix-up pass if red,
4. runs a second, fresh-context pi invocation using the code-review skill
   (Standards + Spec axes) against `origin/main`; the same invocation applies
   fixes and commits,
5. pushes the branch and opens a PR referencing the issue.

Marcel reviews and merges the PR by hand. Failures of any kind (agent error,
red checks after fix-up, review-blocked, timeout, underspecified issue) relabel
the issue `factory-failed` with a comment summarizing what happened and where
the full log lives. No automatic retries.

## User Stories

1. As the operator, I want the factory to pick up issues labeled `ready-for-factory-implementation` that I authored, so that specified work gets implemented without my session time.
2. As the operator, I want the factory to do nothing when no matching issue exists, so that idle ticks cost nothing.
3. As the operator, I want an issue to be relabeled `factory-in-progress` the moment work starts, so that I can see what the factory is doing and no tick double-claims it.
4. As the operator, I want a lock so only one factory container runs at a time, so that ticks cannot overlap or spawn parallel runs.
5. As the operator, I want `FACTORY_CONCURRENCY` as a documented knob for future parallelism, so that scaling up later does not require redesign (v1 enforces 1).
6. As the operator, I want the implementation done by a fresh agent run on a dedicated branch, so that main is never touched and every change is PR-reviewable.
7. As the operator, I want the verification suite run by the wrapper *outside* the agent, so that agent-claimed success is verified, not trusted.
8. As the operator, I want one automatic fix-up pass when checks are red, so that trivial breakage doesn't fail the whole run.
9. As the operator, I want an independent review run with fresh context after implementation, so that author-blindness doesn't hide defects.
10. As the operator, I want the review run to apply its own fixes and commit them, so that review findings don't need another orchestration round-trip.
11. As the operator, I want the branch and PR created automatically (`Closes #<id>`), so that the only remaining human step is review and merge.
12. As the operator, I want a PR body with What / How verified / Notes sections, so that I can review efficiently.
13. As the operator, I want failed runs to relabel the issue `factory-failed` with an explanatory comment, so that I can triage manually without digging through container logs.
14. As the operator, I want full run logs persisted to a volume on the VPS, so that I can read what the agent actually did.
15. As the operator, I want a per-phase wall-clock timeout (default 15 min, configurable), so that a stuck run cannot burn money indefinitely.
16. As the operator, I want the model configurable via env with a default, so that I can swap models without rebuilding the image.
17. As the operator, I want all credentials injected at container runtime, so that nothing sensitive is baked into the image.
18. As the operator, I want the image published to GHCR automatically when factory code changes, so that the VPS always runs the reviewed factory.
19. As the operator, I want the image pulled only when an issue was actually claimed, so that idle ticks don't download gigabytes.
20. As the operator, I want the image public on GHCR, so that the VPS needs no registry credentials (the image bakes in no secrets).
21. As the operator, I want the cron-side script deployed manually, so that only code I explicitly reviewed and deployed executes on my VPS.
22. As the operator, I want a dry-run mode, so that I can validate configuration and issue detection without starting an implementation.
23. As the operator, I want the PAT scoped minimally (single repo: contents:write, pull requests:write, issues:write) and expiring, so that a leak is cheap to contain.
24. As the operator, I want only issues authored by me processed, so that no third party can drive the agent via a crafted issue.
25. As the operator, I want setup documented (env contract, Coolify cron command, PAT scopes, one-time VPS bootstrap), so that I can rebuild the setup from the README alone.

## Implementation Decisions

- **Location & components.** Everything lives in `/factory` in this repo:
  `Dockerfile`, `factory.sh` (in-container entrypoint: implement → verify →
  review → push → PR), `run-factory.sh` (cron-side: pick, claim, pull image,
  run container, report failure), `skills/factory-implement/` (new lean skill,
  derived from the repo's implement workflow but without any grilling or
  confirmation gates), a `justfile` (`build-image`, `deploy-factory`, dry-run
  test), a `.dockerignore`, and a `README.md` with the full setup section.
- **Image.** Single-stage on `eclipse-temurin:21-jdk-jammy` (backend needs
  JDK 21). Node.js 22 LTS via NodeSource (pi requires ≥ 22.19), `corepack
  enable` so pnpm resolves per `packageManager` (11.28.6), `gh` from its
  official apt repo, pi installed via `npm install -g
  @earendil-works/pi-coding-agent`. Skills copied into the image as
  belt-and-braces. Non-root `factory` user. No browser tooling (no
  Playwright/Cypress in the test suite). No secrets baked in.
- **Headless pi.** `pi --print` for one-shot phases (`--mode rpc` is a
  long-lived control protocol and is not used). Provider keys via env vars
  (`OPENROUTER_API_KEY`), which pi natively supports for CI use.
- **Orchestration.** The Coolify cron command is
  `flock -n /tmp/tichu-factory.lock /opt/tichu-factory/run-factory.sh`:
  lockfile prevents overlap; the script itself does issue selection, claiming,
  and container lifecycle in plain bash. `FACTORY_CONCURRENCY` is documented
  but v1 is single-lane.
- **Claiming.** Primary mechanism is relabeling:
  `ready-for-factory-implementation` → `factory-in-progress` before the
  container starts → `factory-failed` on any failure; on success the issue
  stays `factory-in-progress` until the PR merges (the `Closes #<id>` closes
  it). Labels are auto-created by a bootstrap step on first use.
- **Issue selection.** `gh issue list --label ready-for-factory-implementation
  --author polarode` (author filter is a hard requirement); one issue per tick,
  oldest first.
- **Branch & PR.** Branch `factory/issue-<id>`, force-refreshed from
  `origin/main` on every attempt (factory output is disposable; the issue is
  the source of truth). PR not draft, title from the issue, body = `Closes
  #<id>` + `## What` + `## How verified` + `## Notes`. Human review and human
  merge are mandatory gates.
- **Push policy.** Repo AGENTS.md changes to "Never push to main." — pushing
  feature branches is the factory's job. Git identity via
  `GIT_AUTHOR_NAME`/`GIT_AUTHOR_EMAIL` env (factory bot); auth via HTTPS with
  the PAT, no SSH keys.
- **Phases & timeout.** Per-phase `timeout` enforced by the container
  entrypoint (implement, review), `FACTORY_TIMEOUT_M` env, default 15. Wrapper
  verification runs outside the timeout budget of the agent phases.
- **Failure handling.** No retries. Any failure → relabel `factory-failed` +
  issue comment with a tail of the log; logs also written to
  `/factory-logs/<issue>-<timestamp>.log` on a mounted volume.
- **Env contract (Coolify-injected).** `FACTORY_REPO_URL` (required),
  `FACTORY_GH_TOKEN` (required, fine-grained PAT), `OPENROUTER_API_KEY`
  (required), `FACTORY_MODEL` (default `z-ai/glm-5.3-flash`),
  `FACTORY_REVIEW_MODEL` (defaults to `FACTORY_MODEL`), `FACTORY_TIMEOUT_M`
  (default 15), `FACTORY_LABEL_READY` / `_IN_PROGRESS` / `_FAILED` (defaults
  as above), `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `FACTORY_LOG_DIR`.
  Required vars fail loudly with a clear message.
- **Image delivery.** GitHub Actions workflow triggers on pushes to main
  touching `/factory/**`, builds and pushes
  `ghcr.io/polarode/tichu-turnier/factory:{latest,sha-<commit>}` using the
  built-in `GITHUB_TOKEN` (`packages: write`). Package is public.
- **Cron script delivery.** No self-update: deployment of `run-factory.sh` to
  `/opt/tichu-factory/` on the VPS is a manual `just deploy-factory`. One-time
  bootstrap: clone the repo to `/opt/tichu-factory` on the VPS. Image pulled
  via `docker pull` only after an issue is claimed (Docker layer cache makes
  updates cheap).
- **Security model.** Author filter gates the agent's *instructions*; the
  *code* executed in the container comes from main, gated by branch protection
  (only Marcel merges) — same trust model as the Actions workflow. Blast
  radius limits: minimal PAT (single repo, three permissions, 90-day expiry,
  dedicated token), OpenRouter key on a manually-capped credit balance, non-root
  container, no docker socket mounted.

## Testing Decisions

The factory is plumbing (bash + Docker + CI), so tests target the seams where
behavior is observable without an LLM:

- **Dry-run seam (primary).** `run-factory.sh --dry-run` exercises: required-env
  validation, issue selection query, claim/relabel decision logic, and the
  exact `docker run` command it *would* execute — without touching Docker,
  credentials, or the issue. This is the highest seam and the one exercised in
  CI-ish local checks via the justfile.
- **Script-level checks.** `bash -n` syntax checks and shellcheck on both
  scripts, run by the justfile; the Docker build itself is the integration
  test (`just build-image`).
- **Manual acceptance.** The true end-to-end path (cron → container → PR) is
  accepted manually against a throwaway issue on first deployment; it involves
  real LLM spend and real GitHub state, so it is deliberately not automated.

Prior art: this repo has no bash test harness; the dry-run mode replaces one
rather than introducing a test framework.

## Out of Scope

- Automatic retries of failed runs (revisit after real-world failure data).
- Parallel factory lanes (`FACTORY_CONCURRENCY > 1` documented, not implemented).
- Automatic merging, auto-relabel on PR merge, or any CI-triggered issue
  bookkeeping beyond `Closes #<id>`.
- Extracting the factory into a separate repo (likely later; design keeps
  `/factory` self-contained to make extraction trivial).
- Supporting repositories other than tichu-turnier (single hard-coded default;
  `FACTORY_REPO_URL` already points the way).
- Cost/token accounting dashboards; OpenRouter's own billing is the control.
- Grilling/refinement inside the factory: issues are assumed sufficiently
  specified; an underspecified issue is a normal `factory-failed`.

## Further Notes

- The `ready-for-factory-implementation` label is distinct from the existing
  `ready-for-agent` label: factory-ready is a stricter bar (fully specified, no
  open questions) and is applied by Marcel after his normal triage.
- On success the flow ends with the PR open; issue closure happens at merge
  time via `Closes #<id>`.
- If pi rejects the configured model slug, the run fails into
  `factory-failed` — acceptable and visible.
