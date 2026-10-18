# 002: Admin registration & session lifecycle

**What to build:** An organizer registers an account with email/password, logs in, refreshes the session, and logs out. Passwords are stored securely hashed (never plaintext). Successful login/refresh issues an `httpOnly` session cookie (`Secure`, `SameSite=Lax`) exchanged once after credential validation; logout invalidates the session. Failed logins return a 401 RFC 7807 `ProblemDetail` with a stable machine-readable error code. Demoable end-to-end with curl.

**Blocked by:** None (can start immediately).

**Status:** done (verified locally, full backend suite green; CI to confirm on push)

- [x] Register endpoint creates an admin account; duplicate email is rejected with a typed validation error (test)
- [x] Passwords are hashed with current security practice; plaintext never persisted or logged (test)
- [x] Login with valid credentials sets an httpOnly `Secure` `SameSite=Lax` session cookie; the raw token never appears in URLs, headers-as-storage, or response bodies beyond the cookie
- [x] Session refresh endpoint exchanges a valid session for a fresh one; invalid/expired sessions get 401 `ProblemDetail` (test)
- [x] Logout invalidates the session; the cookie no longer authenticates (test)
- [x] Failed login (wrong password / unknown email) returns 401 with a stable error code, without leaking whether the account exists
- [x] Wiring via `@Configuration` + `@Bean` only; hexagonal dependency direction intact (auth lives in application/infra, domain stays framework-free)

## Notes

- `IdGenerator` was generalised from `TaskId` to `String` so admin/session ids reuse it; this was the only prefactor.
- 401/403 distinction beyond credentials is deferred to ticket 003 (the API-wide guard).
- Known robustness gap (deferred): a concurrent duplicate registration races on the DB
  unique constraint and would surface as a 500 rather than a 409; the normal path is
  handled and tested.