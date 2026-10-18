# 001: Real persistence — Postgres + Flyway behind the repository port, docker-compose for local dev

**What to build:** A developer who freshly clones the repo runs `docker compose up` and gets the full stack running against a real PostgreSQL database. The Task domain (kept temporarily as the template's guinea pig) survives a backend restart, proving the documented persistence swap is real: a Flyway-managed schema, a Spring Data JPA adapter implementing the existing domain repository port, and no in-memory fallback in the default profile. Integration tests run against Testcontainers Postgres in CI, so every push proves the persistence layer actually works.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Flyway migration applies cleanly on an empty database; Task data survives a backend restart (no in-memory repository in the default profile)
- [ ] Repository integration test runs against Testcontainers Postgres (locally and in CI)
- [ ] `docker compose up` starts Postgres + backend + frontend dev server from a fresh clone, documented in the README
- [ ] Existing CI keeps running build + full test suite green, now including the Testcontainers test
- [ ] Template conventions hold: wiring via `@Configuration` + `@Bean` only, no `@Service`/`@Autowired` in production code, hexagonal dependency direction intact (domain unchanged, adapter in infra/persistence)
