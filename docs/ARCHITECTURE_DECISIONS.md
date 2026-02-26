# Architecture Decisions

This document records key architectural decisions made for the finyo project, including the context and rationale behind each choice.

---

## ADR-001: Row-level multi-tenancy

**Decision:** Each database table that holds user data includes a `user_id` column (the Keycloak subject UUID). All queries are filtered by this column at the repository layer. There is no separate schema or database per user.

**Context:** finyo is a personal finance planner. Every user's data (accounts, transactions, budgets) is private and must be isolated from other users.

**Rationale:**
- Schema-per-tenant and database-per-tenant approaches are operationally expensive at the scale of a personal-finance SaaS: thousands of schemas in a single PostgreSQL instance create catalog bloat and make Flyway migrations complex.
- Row-level tenancy is simple, well-understood, and performs well at this scale. A composite index on `(user_id, <relevant_column>)` keeps queries fast.
- PostgreSQL Row-Level Security (RLS) can be added later as a defense-in-depth layer without schema changes.
- The `UserContextProvider` component extracts `user_id` from the JWT subject claim so it is always derived from the trusted token, not from a request parameter.

---

## ADR-002: Keycloak as the authorization server (vs. Spring Authorization Server)

**Decision:** Use Keycloak 26 (self-hosted via Docker Compose) as the OIDC/OAuth2 authorization server. The Spring Boot API is a pure resource server that validates JWTs.

**Context:** The project needs user authentication, role management, and eventually a React SPA client plus a CLI client. Both need PKCE and/or client-credentials flows.

**Rationale:**
- **Keycloak is production-ready out of the box.** It ships with a UI for user and role management, brute-force protection, password reset, and session management — features that would each require bespoke development with Spring Authorization Server.
- **Multi-client support.** The realm JSON defines `finyo-ui` (public client, PKCE, standard flow) and `finyo-cli` (confidential client, service accounts) as first-class concepts without custom code.
- **PKCE enforcement per client.** Keycloak 26 supports `pkce.code.challenge.method: S256` as a client attribute, enforcing PKCE at the authorization-server level rather than relying on the SPA to opt in.
- **Spring Authorization Server** was considered but rejected: it requires significant custom code to implement a user store, consent screens, and role management, which is not the core product value.

---

## ADR-003: JWT validation approach in tests

**Decision:** Tests use a `@TestConfiguration` class (`TestSecurityConfig`) that provides a `@Primary JwtDecoder` bean returning a statically-constructed `Jwt` for any token value. The `application-test.yaml` profile does not include any `spring.security.oauth2.resourceserver` configuration.

**Context:** Spring Boot's OAuth2 resource server auto-configuration performs an HTTP call to the `issuer-uri` JWKS endpoint during `ApplicationContext` startup. In CI there is no Keycloak instance, so this call fails immediately and every `@SpringBootTest` aborts before running any test.

**Rationale:**
- Removing the issuer-uri from the test profile prevents the JWKS fetch. Without a `JwtDecoder` bean in scope at all, the auto-configuration would still try to create one and fail.
- Providing a `@Primary JwtDecoder` mock satisfies the resource-server auto-configuration without hitting the network.
- Tests that need to assert on authenticated behavior use `SecurityMockMvcRequestPostProcessors.jwt()` (from `spring-security-test`) to inject a `JwtAuthenticationToken` directly into the `MockMvc` request — no actual token string is ever decoded.
- `@WithMockUser` is suitable for simple role checks but does not populate the `Jwt` principal required by `UserContextProvider`; use `.jwt()` post-processor for those cases.

---

## ADR-004: In-process cache with Caffeine

**Decision:** Use Caffeine as the cache provider (`spring-boot-starter-cache` + `caffeine`), configured with a single shared spec of 500 entries / 15-minute TTL. Named caches (`marketData`, `news`) are declared in `CacheConfig`.

**Context:** The API fetches market data from the SIX Group API and parses multiple RSS feeds. Both are external HTTP calls that should not be repeated on every request.

**Rationale:**
- An in-process cache avoids introducing a Redis dependency at this stage. Redis adds operational complexity (another container, connection pool, serialization) that is not justified until the API is horizontally scaled.
- Caffeine is the default and highest-performing in-process cache library for the JVM; it is already on the Spring Boot dependency management BOM.
- 15-minute TTL is appropriate for market data and news: data is stale on that timescale regardless, and it aligns with typical rate-limit windows on free-tier financial APIs.
- When horizontal scaling becomes necessary, the cache layer can be swapped to Redis by changing `spring.cache.type` and adding the Redis starter — no application code changes required because all cache interactions go through the Spring `@Cacheable` abstraction.

---

## ADR-005: Spring Shell version alignment

**Decision:** Use `spring-shell-starter:4.0.0`. The previous version in the project (3.4.0) targets Spring Boot 3.x / Spring Framework 6.x and is incompatible with the project's Spring Boot 4.0.3 (Spring Framework 7.x) baseline.

**Context:** Spring Shell follows the same major-version cadence as Spring Boot. Version 4.0.0 was released alongside Spring Boot 4.0 GA and is the first version to declare a compile dependency on Spring Framework 7.

**Rationale:**
- Spring Shell 3.4.x depends on Spring Framework 6.x APIs. Running it under Spring Framework 7.x may appear to work initially but will break on any API that was removed or changed in the Framework 7 migration (e.g., `HttpInputMessage`, various `@Deprecated(forRemoval=true)` targets).
- Spring Shell 4.0.0 is the supported version for this Spring Boot generation and is available on Maven Central.
- The CLI module is currently a low-priority feature; if Spring Shell 4.0.x introduces any breaking changes to the shell DSL, the migration cost at this stage (no commands written yet) is zero.
