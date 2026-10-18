# 004: Ownership checks through the use cases

**What to build:** The authenticated principal flows into the application layer: resources record their owning admin at creation, and every read/update/delete verifies the caller owns the resource before acting. A different admin's session (or any other session) touching someone else's resource gets 403/404 and never sees the data. This establishes the ownership rule that all tournament-scoped features (Phase 4+) inherit, using the Task domain as the guinea pig — same as Phase 1.

**Blocked by:** 003 (API-wide session guard & CORS lockdown).

**Status:** done (full backend suite green; ownership enforced in use cases and proven at the HTTP boundary)

- [x] Creating a resource records the authenticated principal as owner
- [x] The owner can read/update/delete their own resources (test)
- [x] User A cannot read, modify, or delete user B's resource: 403/404, never data (test for each verb)
- [x] Foreign access is rejected even for listing: list endpoints return only the caller's own resources (test)
- [x] Ownership check lives in the use case layer, exercised by unit tests plus endpoint integration tests
- [x] All ownership-guarded mutations are covered so the rule is established before Phase 4 builds tournaments on it

## Notes

- The owner is an explicit use-case argument (`owner: AdminUserId`) taken from the
  session principal by the controller — never from the request body — so a caller
  cannot claim another admin's data.
- Foreign access answers **404 NOT_FOUND** for every verb (not 403): an admin cannot
  even learn that another admin's id exists. 403 stays reserved for role/permission
  failures once those exist; ownership is "you can't see it", role is "you may not".
- `OwnedTaskLookup.findOwnedOrThrow` is the single ownership gate shared by get/update/
  delete; `TaskRepository.findAllByOwner` pushes the scope into the query for lists, so
  foreign rows are never loaded.
- `V3__add_task_owner.sql` makes `tasks.owner_id` `NOT NULL` (+ FK, index) and removes
  the template's pre-ownership guinea-pig task rows, which had no admin to attribute
  them to. Task is replaced by the tournament domain in Phase 4.