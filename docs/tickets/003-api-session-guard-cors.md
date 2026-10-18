# 003: API-wide session guard & CORS lockdown

**What to build:** Every `/api/**` endpoint except register/login requires a valid admin session cookie. Unauthenticated requests get a 401 RFC 7807 `ProblemDetail` with a stable error code and English message — never data. CORS is restricted to the configured frontend origin only. The guard is provable against the existing real endpoints (the Task API), not a stub, so the whole API becomes protected the moment this lands.

**Blocked by:** 002 (Admin registration & session lifecycle).

**Status:** ready-for-agent

- [ ] Unauthenticated request to any protected endpoint returns 401 `ProblemDetail` with a stable error code (test)
- [ ] Register/login endpoints stay reachable without a session; everything else under `/api/**` is guarded
- [ ] A valid session cookie grants access to previously public endpoints (test)
- [ ] CORS allows only the configured frontend origin; other origins are rejected (test)
- [ ] Error responses distinguish unauthenticated (401) from other failures; no data leaks in error bodies
- [ ] Guard enforced server-side regardless of client behavior (security filter chain, not controller-level checks)