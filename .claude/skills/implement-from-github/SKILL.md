---
name: implement-from-github
description: Implement a GitHub issue end-to-end. Input is the issue id. Fetches the issue, explores the codebase, refines it via grill-with-docs, updates the issue, then implements after explicit confirmation.
---

Workflow — stop and wait for the user at every marked gate. Never skip a gate.

1. **Fetch**: `gh issue view <id> --repo polarode/tichu-turnier --comments` and read any linked issues it blocks on / is blocked by.
2. **Explore**: dispatch `scout` subagents in parallel, one per codebase area the issue touches, each briefed with the issue's requirements. Their reports (relevant AGENTS.md sections, existing code, patterns) are your context for grilling.
3. **Grill**: call the Skill tool for "grill-with-docs" to refine the issue and resolve open questions. Ask at most 4 questions per round (one `AskUserQuestion` call).
4. **Gate — confirm issue update**: ask the user for confirmation before updating the GitHub issue. If confirmed, update it with the decisions made during grilling (`gh issue edit`). If not, wait for direction.
5. **Gate — start implementation**: wait for the user's explicit signal before writing any code. Only then call the Skill tool with "implement" to carry out the implementation phase.
