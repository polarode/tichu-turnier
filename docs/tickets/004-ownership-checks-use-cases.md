# 004: Ownership checks through the use cases

**What to build:** The authenticated principal flows into the application layer: resources record their owning admin at creation, and every read/update/delete verifies the caller owns the resource before acting. A different admin's session (or any other session) touching someone else's resource gets 403/404 and never sees the data. This establishes the ownership rule that all tournament-scoped features (Phase 3+) inherit, using the Task domain as the guinea pig — same as Phase 1.

**Blocked by:** 003 (API-wide session guard & CORS lockdown).

**Status:** ready-for-agent

- [ ] Creating a resource records the authenticated principal as owner
- [ ] The owner can read/update/delete their own resources (test)
- [ ] User A cannot read, modify, or delete user B's resource: 403/404, never data (test for each verb)
- [ ] Foreign access is rejected even for listing: list endpoints return only the caller's own resources (test)
- [ ] Ownership check lives in the use case layer, exercised by unit tests plus endpoint integration tests
- [ ] All ownership-guarded mutations are covered so the rule is established before Phase 3 builds tournaments on it