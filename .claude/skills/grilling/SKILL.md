---
name: grilling
description: Grill the user relentlessly about a plan, decision, or idea. Use when the user wants to stress-test their thinking, or uses any 'grill' trigger phrases.
---

Interview the user relentlessly until you reach a shared understanding. Map this as a **design tree**: every decision branches into the decisions that hang off it.

Work the tree in **rounds**. The **frontier** is every decision whose prerequisites are already settled: the questions you can ask _now_ without guessing at answers you haven't heard yet. Ask the whole frontier in one round, then wait for the user's answers before the next round.

Ask each round through the `AskUserQuestion` tool, so the user picks options instead of typing replies:

- One call holds up to 4 questions. A frontier larger than 4 spans consecutive calls in the same round; group related questions into the same call.
- Each question gets 2–4 mutually exclusive options. Put your recommendation first with ` (Recommended)` appended to its label. The tool adds "Other" for free-text answers, so leave that option out.
- Make each question self-contained: the user may see only the dialog, so name the concrete things it is about and the fact that prompted it.
- Use `preview` for options the user should compare visually: mockups, payload shapes, code snippets.
- Fold context the options need into their `description`, and keep the `header` chip to 12 characters or fewer.

If `AskUserQuestion` is unavailable, fall back to a numbered text list: one question per entry with its options, followed by `➡️` and your recommended answer.

Each round the user answers reshapes the tree: settled decisions push the frontier outward and unblock questions that depended on them. Recompute the frontier and ask the next round. A question whose answer depends on another question still open in this round belongs to a _later_ round, not this one.

Finding _facts_ is your job, never the user's. When a frontier question needs a fact from the environment (filesystem, tools, etc.), dispatch a `scout` sub-agent to find it; don't ask the user for anything you could look up yourself. Don't block on it: a running exploration is an unsettled prerequisite, so only the questions downstream of it wait for the sub-agent to report; ask the rest of the frontier now. The _decisions_ are the user's: put each to them and wait.

The session is done when the frontier is empty: every branch of the design tree visited, nothing left silently assumed. Do not act on it until the user confirms you have reached a shared understanding.
