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
- **Multi-client support.** The realm JSON defines `finyo-fe` (public client, PKCE, standard flow) and `finyo-cli` (confidential client, service accounts) as first-class concepts without custom code.
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

**Amended (2026-07-14, ADR-007):** The `news` cache no longer exists — the RSS module was removed. The rationale above also over-reaches on one point: an in-process cache with a 15-minute TTL is the right tool for *responses*, but it is the wrong tool for *data*. Reference data and prices must survive a restart and an outage of the source, because the sources are unofficial and will be unavailable at some point. Those therefore live in Postgres (`security_reference`, and `instrument_price` from PR 2), with Caffeine only in front of them as a hot-path cache. The rule going forward: **Caffeine caches answers, Postgres stores data** — if losing an entry while the source is down breaks the feature, it does not belong in Caffeine.

---

## ADR-007: Ports and adapters for external data sources

**Decision:** External data sources live behind ports. The **port** (interface plus vendor-neutral DTOs) belongs to the consuming module (`ch.finyo.marketdata.spi`, later `ch.finyo.fx.spi` and `ch.finyo.tax.spi`); the **adapter** that speaks HTTP to a vendor belongs to `ch.finyo.integration.<vendor>`. Market data and FX rates get their own top-level modules (`ch.finyo.marketdata`, `ch.finyo.fx`) rather than living inside `investment`. An ArchUnit test enforces that nothing outside `ch.finyo.integration` depends on it.

**Context:** finyo needs security master data and prices (SIX, OpenFIGI, EODHD), exchange rates (Frankfurter, BAZG) and Swiss tax data (ESTV). The research in `docs/DATENQUELLEN.md` established that the three most valuable sources are all *unofficial*: SIX FQS and the ESTV calculator have no contract, no documentation and no stability promise, and SIX's terms of use permit personal use only — which stops being true the moment finyo has a second user.

**Rationale:**
- **The sources will change; the domain should not.** A vendor breaking its wire format, or becoming legally unusable, must be a configuration change, not a refactoring. That only holds if a SIX field name (`ProductLine`, `TradingBaseCurrency`) or an ESTV request quirk never escapes the adapter that produced it.
- **Market data is tenant-free; `Instrument` is not.** The closing price of `IE00B4L5Y983` is identical for every user, while `instrument` carries a `user_id` under ADR-001. Storing prices per user would be duplication with a consistency risk, and it would make a shared price cache impossible. That asymmetry is what justifies a separate module rather than a package inside `investment`.
- **Several modules need the same data.** FX is needed by `investment`, `transaction`, `wealth` and `tax`; ESTV by `tax` and `pillar3`. Homing those clients in any one feature module would point the dependency the wrong way.
- **A single fat `MarketDataProvider` interface was rejected.** The providers are asymmetric: SIX resolves master data *and* prices, OpenFIGI only master data. One interface would force OpenFIGI to answer `latestQuote()` with `Optional.empty()` — an interface-segregation violation that disguises itself as "the provider just returned nothing" and poisons debugging. Instead the ports are split by capability, and a `supports(SecurityId)` predicate expresses what each provider can resolve (OpenFIGI has no concept of a Swiss valor number).
- **The chain order lives in configuration, not in `@Order` annotations.** Which vendor answers first is an operational decision. `finyo.marketdata.reference-providers: [six, openfigi]` is the whole switch; the multi-user migration to a licensed provider is three YAML lines. Spring profiles were rejected for this: they describe environments, and provider choice is orthogonal — one may well want to test EODHD in dev. Abusing profiles as feature switches multiplies the test matrix.
- **ArchUnit rather than review discipline.** The boundary decays silently under normal maintenance — someone imports an FQS record into a service "just to read the ProductLine", and a year later SIX is wired through half the codebase. The rule costs seconds to run and is the reason the boundary still exists in six months.

**Consequences:** Three new packages and one `spi` sub-package per consuming module. In exchange, the day SIX has to be switched off is a config change. `SixLicenceCheck` makes the personal-use threshold an executable assertion instead of a footnote: it logs an error on every start if SIX is enabled while more than one user exists.

---

## ADR-008: Provenance — "unknown", "unavailable" and "verified" are different things

**Decision:** Every externally derived value carries where it came from, and the type system keeps the three possible states apart:

- A lookup returns `LookupResult.Found` / `NotFound` / `Unavailable` — never an `Optional`.
- `Instrument.currency` is **nullable**. `NULL` means unknown, and that is not the same as a verified `CHF`.
- `Instrument.source` distinguishes `SIX` / `OPENFIGI` (verified), `MANUAL` (the user), `HEURISTIC` (asked, nobody knew — the asset class was guessed from the name) and `UNRESOLVED` (could not ask; needs another attempt, and gets one on the next touch).

**Context:** This project already had the bug this ADR exists to prevent, twice. When the SIX API key was absent — its default state — `PortfolioService` silently fell back to the *purchase price*, so every position showed a gain/loss of exactly 0.00 and nothing said the number was not a market price. Separately, `Instrument` had no currency column at all, so a USD-quoted ETF was summed into the portfolio total as though it were francs. Both are the same failure: a guess presented as a fact.

The first cut of the market-data module rebuilt that failure one level up, which is why this ADR exists. `Optional.empty()` was returned both when a provider did not know a security and when it could not be reached; and the missing currency from OpenFIGI (which publishes none) was defaulted to CHF while being stamped `source = OPENFIGI`. A USD ETF resolved through the fallback path — exactly the path taken when SIX does not know it or is down — would have been stored as a Swiss-franc instrument with authoritative-looking provenance.

**Rationale:**
- **`Optional` is the wrong type for a lookup against an unreliable source.** "The catalogue does not contain this security" is a durable fact about the world and licenses a fallback. "The network was down" is a fact about us and licenses nothing. Collapsing them means a provider outage during an import freezes guesses into the database permanently: the instrument then exists, is found by ISIN on every subsequent import, and is never re-resolved.
- **A default value is a lie with good posture.** `NOT NULL DEFAULT 'CHF'` makes an unknown currency indistinguishable from a verified one and hands the FX converter (PR 4) a guess it will treat as data. Nullable is uncomfortable and correct — it forces the consumer to decide what to do about not knowing.
- **Record provenance is not field provenance.** `source = OPENFIGI` says the master data came from OpenFIGI. It says nothing about the currency, which OpenFIGI does not publish. Conflating the two turns the provenance flag itself into a false assurance.
- The cost is real: three states to handle instead of two, and a nullable column that every consumer must think about. That is the price of not being confidently wrong.

**Consequences:** `ResilientCall` returns a typed `CallOutcome` instead of swallowing failures into an empty `Optional`, and re-throws `Error` and `InterruptedException` rather than reporting them as "the vendor had nothing" — a broken JVM must not quietly write master data. `PositionService` re-resolves `UNRESOLVED` instruments the next time it touches them, so an outage degrades the data rather than corrupting it.

---

## ADR-005: Spring Shell version alignment

**Decision:** Use `spring-shell-starter:4.0.0`. The previous version in the project (3.4.0) targets Spring Boot 3.x / Spring Framework 6.x and is incompatible with the project's Spring Boot 4.0.3 (Spring Framework 7.x) baseline.

**Context:** Spring Shell follows the same major-version cadence as Spring Boot. Version 4.0.0 was released alongside Spring Boot 4.0 GA and is the first version to declare a compile dependency on Spring Framework 7.

**Rationale:**
- Spring Shell 3.4.x depends on Spring Framework 6.x APIs. Running it under Spring Framework 7.x may appear to work initially but will break on any API that was removed or changed in the Framework 7 migration (e.g., `HttpInputMessage`, various `@Deprecated(forRemoval=true)` targets).
- Spring Shell 4.0.0 is the supported version for this Spring Boot generation and is available on Maven Central.
- The CLI module is currently a low-priority feature; if Spring Shell 4.0.x introduces any breaking changes to the shell DSL, the migration cost at this stage (no commands written yet) is zero.

---

## ADR-006: Cloud document ingestion — delta polling, folder-gated auto-apply

**Decision:** Tax documents are pulled from a SharePoint library via Microsoft Graph on a 15-minute delta poll (not via webhooks). Files are never copied into finyo — only metadata, a reference and the extraction result are stored. A value is written into a tax year unattended only when the folder path and the document agree on both type and year, and only into a field that is still empty. Access uses app-only client credentials scoped with `Sites.Selected`.

**Context:** Documents (salary certificates, insurance statements, assessments) are filed in OneDrive/SharePoint under a `STE-<year>/<type>/` convention. The existing `taxdocument` module could already classify and extract them, but was preview-only: it persisted nothing and had no way to be fed by anything but an upload.

**Rationale:**
- **Polling over webhooks.** Graph change notifications for `driveItem` carry no payload, so a delta query must follow regardless. Subscriptions expire after ~30 days and need renewal, and a missed notification means a document silently never arrives. A poll is self-healing: whatever one run misses, the next picks up. A webhook can be added later purely as a latency optimization, but must never carry correctness.
- **The folder, not the confidence, gates auto-apply.** `DocumentClassifier` normalizes its score per type against the sum of that type's keyword weights, and those maxima are unequal (salary 10, assessment 14). A score is therefore not comparable across types, and a global threshold would permanently lock some types out of automation. The folder convention is user-maintained and a far stronger signal. The year cross-check specifically prevents a 2024 document filed under `STE-2025/` from silently overwriting the 2025 figures.
- **Never overwrite unattended.** Auto-apply fills empty fields only; a stored value that differs is reported as a conflict and routed to the review inbox. `TaxYearService.upsert()` is a full replace and is deliberately not used on this path — a payload built from one document would null out every other field of the year.
- **`cTag`, not a content hash.** A unique constraint on a content hash would collide on legitimate duplicates and break on every delta full-resync. Graph's `cTag` changes only when content changes and needs no download to check. It also provides the repair path: re-uploading an OCR'd version of a failed scan changes the `cTag` and the document is reprocessed automatically.
- **`Sites.Selected` over `Files.Read.All`.** The latter grants read access to every OneDrive and site in the tenant to a self-hosted service. `Sites.Selected` grants nothing until an admin hands out access to one named site.
- **No file copies.** SharePoint is already the system of record and is backed up there. Storing a second copy of tax documents on the application server buys nothing and enlarges both the database and the blast radius.

**Consequence:** Scanned PDFs without an OCR text layer cannot be processed at all — the extraction stack reads text, not pixels. Enabling OCR in the scanner software is a precondition, not a nice-to-have.
