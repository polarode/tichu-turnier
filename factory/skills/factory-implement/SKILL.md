---
name: factory-implement
description: Headless implementation of a fully-specified GitHub issue for the dark factory. No grilling, no confirmation gates.
---

You are running headless inside the dark factory container. Nobody can answer
questions. Implement the issue end-to-end on your own judgement. Never prompt
for confirmation; if a decision is needed, make the most reasonable choice
consistent with the issue and the repo conventions and note it in your summary.

## Input

The issue id is provided in your task prompt as `ISSUE: <id>`.

## Process

1. **Fetch**: `gh issue view <id> --comments` and read any issues it references
   (blocks / blocked-by). The issue is assumed fully specified — do not refine
   it, do not update it, do not comment on it.
2. **Orient**: read the repo `AGENTS.md`, `GLOSSARY.md`, and the code in the
   areas the issue touches. Follow the architecture and conventions documented
   there strictly.
3. **Implement**: write the code and tests described by the issue. Match the
   existing style, naming (`*.component.tsx`, `*.repository.ts`, …) and layering
   rules. Keep the change as small as the issue allows; no speculative
   generality.
4. **Verify**: run the checks yourself and fix what you broke:
   - backend: `cd backend && ./gradlew test`
   - frontend: `cd frontend && pnpm install && pnpm typecheck && pnpm lint && pnpm test`
5. **Commit**: stage everything and commit with Conventional Commits format,
   referencing the issue (e.g. `feat(backend): ... \n\n refs #<id>`). Several
   logical commits are fine. Do NOT push and do NOT create a PR — the wrapper
   does that.

## Blocked / underspecified

If — and only if — the issue is genuinely impossible to implement (missing
domain decisions, contradicting requirements, referenced blockers not done),
print exactly one line starting with `BLOCKED: ` followed by a short
explanation, make no commits, and stop. Anything else: implement and note
assumptions.

## Output

End with a short summary: what you changed, which checks you ran and their
result, and any assumptions made.
