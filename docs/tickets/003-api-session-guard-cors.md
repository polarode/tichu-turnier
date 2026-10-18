# 003: API-wide session guard & CORS lockdown

**What to build:** Every `/api/**` endpoint except register/login requires a valid admin session cookie. Unauthenticated requests get a 401 RFC 7807 `ProblemDetail` with a stable error code and English message — never data. CORS is restricted to the configured frontend origin only. The guard is provable against the existing real endpoints (the Task API), not a stub, so the whole API becomes protected the moment this lands.

**Blocked by:** 002 (Admin registration & session lifecycle).

**Status:** done (full backend suite green; CORS preflight + guard verified against the real Task API)

- [x] Unauthenticated request to any protected endpoint returns 401 `ProblemDetail` with a stable error code (test)
- [x] Register/login endpoints stay reachable without a session; everything else under `/api/**` is guarded
- [x] A valid session cookie grants access to previously public endpoints (test)
- [x] CORS allows only the configured frontend origin; other origins are rejected (test)
- [x] Error responses distinguish unauthenticated (401) from other failures; no data leaks in error bodies
- [x] Guard enforced server-side regardless of client behavior (security filter chain, not controller-level checks)

## Notes

- The guard is a Spring Security `SecurityFilterChain` (stateless, CSRF disabled per the
  plan's cookie + restricted-CORS + JSON-only reasoning). `AdminSessionAuthenticationFilter`
  resolves the session cookie through the read-only `AuthenticateSession` port and must run
  before `AnonymousAuthenticationFilter`.
- `/api/auth/refresh` and `/api/auth/logout` are guarded too (they validate their own
  cookie); only `register` and `login` are `permitAll`.
- CORS is configured through Spring Security's `CorsConfigurationSource` (`allowedHeaders =
  Content-Type` for JSON preflight); the MVC `WebMvcConfigurer` CORS bean was removed.
- `ProblemDetailAccessDeniedHandler` answers 403 in the same RFC 7807 shape once role/
  ownership checks exist; the 401 entry point is the one exercised today.