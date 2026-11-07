---
name: implement
description: "Implement a piece of work based on a spec or set of tickets."
---

Implement the work described by the user in the spec or tickets. You are the **orchestrator**: you plan, brief and check; the `scout`, `implementer` and `checker` subagents do the legwork.

If the user passes a ticket reference, fetch it from the issue tracker and state its title before starting. If the reference is ambiguous, ask.

1. **Scout**: dispatch `scout` subagents in parallel, one per area the work touches (backend, frontend). Done when you can name every file the change creates or edits and the existing pattern each one copies.
2. **Seams**: call the Skill tool with "tdd" and agree the seams with the user.
3. **Slice**: cut the work into vertical slices, one seam and one test each, small enough that a brief can name every file involved.
4. **Delegate** each slice to an `implementer` subagent, one at a time (they share the working tree). The brief is all it knows, so it carries: the agreed seam and the behaviour its test asserts, the files to create or edit, the pattern files to copy, the `AGENTS.md` section that applies, and the single test command.
5. **Check** each slice as it reports: read its `git diff` and judge it against the brief and `AGENTS.md`. Send corrections to the same implementer with SendMessage; when a second round still misses, finish the slice yourself.
6. **Verify**: dispatch a `checker` subagent for every app touched. Fix what it reports and re-run it until it reports green.
7. Call the Skill tool with "code-review" to review the work.
8. Commit your work to the current branch, with a changelog entry when the change is organizer-facing (`AGENTS.md` → *Changelog*).
