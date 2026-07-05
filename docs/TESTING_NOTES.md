# Testing Notes — Devil's Advocate Analysis

This document captures security concerns, missing validations, and edge cases
identified while reviewing the finyo-be codebase for testability. It is
intended for contributors implementing new features, so they can write tests
that catch real bugs rather than only exercising happy paths.

---

## 1. Security Concerns Found

### 1.1 Multi-Tenant Isolation Is the Critical Risk

Every service method that queries the database must filter by `userId`. The
current implementation does this correctly in all service classes — but this
must be verified end-to-end via integration tests because:

- A future refactor might introduce a convenience method that calls
  `findById()` instead of `findByIdAndUserId()`.
- The compiler and unit tests would not catch this; only an integration test
  that creates data for User A and attempts access as User B will reveal it.

**Critical patterns to keep testing:**

| Operation | How the bug would manifest |
|---|---|
| `GET /api/v1/transactions/{id}` as User B | Returns 200 instead of 404 |
| `DELETE /api/v1/transactions/{id}` as User B | Deletes User A's data silently |
| `POST /api/v1/transactions` with User A's accountId | Allows cross-user booking |
| `POST /api/v1/transactions` with User A's categoryId | Allows cross-user category assignment |
| `GET /api/v1/accounts` | Leaks User B's accounts in User A's list |
| `GET /api/v1/categories` | Leaks User B's categories in User A's list |

### 1.2 UserProvisioningFilter In-Memory Cache Is a Risk

`UserProvisioningFilter` caches provisioned user IDs in a `ConcurrentHashMap`
stored in memory on the JVM heap. Consequences:

- After a restart or pod replacement (in Kubernetes), every user's next
  request will trigger `seedDefaultsIfEmpty()` again. The method is idempotent
  (`count > 0` guard), so this is safe — but it is an extra DB round-trip on
  every restart for every active user.
- If the application is ever horizontally scaled with sticky sessions disabled,
  different pods will independently run the provisioning check for the same
  user. Concurrent inserts into `category` for the same `userId` could produce
  duplicate rows if the `count > 0` check and the subsequent inserts are not in
  a serializable transaction. The `@Transactional` annotation on
  `seedDefaultsIfEmpty` uses the default isolation level (READ COMMITTED),
  which does not prevent a TOCTOU race with concurrent requests.

**Recommended test:** write a test that calls `/api/v1/categories` concurrently
from the same user via multiple threads and asserts that the category count
equals exactly 17 (the number of seeded defaults).

### 1.3 Admin Endpoint Authorisation Relies Solely on Keycloak Roles

`/api/v1/admin/**` requires `ROLE_admin`, which is extracted from the JWT's
`realm_access.roles` claim. There is no application-level role table. A
compromised or misconfigured Keycloak that issues `admin` role to wrong users
would immediately grant admin access in the application.

**Recommended mitigation:** add a database-backed role check as a secondary
guard for destructive admin operations.

### 1.4 CORS Is Locked to Two Origins — Must Not Widen on Accident

The allowed origins are `http://localhost:3000` and `http://localhost:5173`.
Any PR that adds a wildcard (`*`) or an overly broad regex would open the API
to cross-site requests from any origin. The `SecurityIT` suite currently
verifies only that the preflight succeeds for an allowed origin; it should also
verify that a preflight from a **disallowed** origin is rejected with no
`Access-Control-Allow-Origin` header.

---

## 2. Missing Validations Identified

### 2.1 Transaction Amount — Zero Is Accepted

`TransactionRequest.amount` is annotated with `@NotNull` only. A zero-value
amount (`0`, `0.00`, `0.0000`) passes validation and is persisted. A CHF 0.00
transaction has no financial meaning and is almost certainly a data-entry error
or an import bug.

**Recommended fix:** add a custom `@AssertTrue` or a Constraint Validator that
rejects amounts equal to zero. Whether the constraint should be
`amount != 0` or `|amount| >= 0.01` (minimum currency unit) is a product
decision that should be documented in `ARCHITECTURE.md`.

### 2.2 Transaction Description — No Maximum Length

`Transaction.description` is mapped to a PostgreSQL `TEXT` column with no
length limit. There is no `@Size` constraint on `TransactionRequest.description`.

A caller could POST a description of 10 MB, triggering:
- High memory pressure on the JVM while deserialising the JSON body
- A very large row in PostgreSQL that degrades index performance
- Slow full-table scans on future `LIKE`-based search queries

**Recommended fix:** add `@Size(max = 1000)` to `TransactionRequest.description`
and document the limit in the API contract.

### 2.3 Currency Code — Not Validated Against ISO 4217

Both `AccountRequest.currency` and `TransactionRequest.currency` accept any
string of up to 3 characters. There is no check that the value is a real ISO
4217 currency code. A client could store `"XXX"`, `"abc"`, or `"   "`.

**Recommended fix:** add a custom validator that checks against a set of known
ISO 4217 codes, or at a minimum add `@Pattern(regexp = "[A-Z]{3}")` to
enforce the format.

### 2.4 Partial Date Range Filter Is Silently Ignored

`TransactionController.getAll()` accepts `from` and `to` query parameters.
`TransactionService.getAll()` only activates the date range filter when
**both** are non-null. If a caller sends `?from=2025-01-01` without `to`, the
parameter is silently ignored and all transactions are returned. The caller
receives no error indication.

**Recommended fix:** validate that either both `from` and `to` are present or
neither is, and return 400 if exactly one is provided.

### 2.5 Account Color Format — Partial Validation

`AccountRequest.color` has `@Size(max = 7)` which accepts `"#RRGGBB"` (valid)
but also `"abc123"` (missing `#`) or `"#GGH"` (invalid hex). No regex
constraint enforces the hex colour format.

**Recommended fix:** add `@Pattern(regexp = "^#[0-9A-Fa-f]{6}$")` to both
`Account.color` and `Category.color` fields.

### 2.6 Account Name Uniqueness — Not Enforced per User

Multiple accounts with the same name can be created for the same user. While
not necessarily a security issue, it degrades UX and makes it harder to
identify the correct account during transaction assignment.

**Recommended fix:** add a unique constraint at the database level:
`UNIQUE (user_id, name)` on the `account` table, and handle the resulting
`DataIntegrityViolationException` in `GlobalExceptionHandler` with a 409
Conflict response.

---

## 3. Edge Cases to Implement as Modules Are Built

### 3.1 Analytics Module (`/api/v1/analytics/summary`)

- A user with **no transactions** must receive an empty summary (zero totals),
  not a 500 or a null pointer.
- A summary period where the user has **only income** and no expenses must
  produce correct non-null expense totals (zero, not null).
- The monthly data aggregation query uses `EXTRACT(YEAR/MONTH FROM date)`.
  Verify that a transaction on `2025-12-31` falls in December 2025 and not
  January 2026 (timezone edge case if `date` is ever stored as `TIMESTAMP WITH
  TIME ZONE`).
- Category breakdown: if a category is deleted after transactions were assigned
  to it, the `sumExpensesByCategoryForPeriod` query joins on `t.category IS NOT
  NULL` which filters out orphaned transactions. Verify this is intentional and
  document it.

### 3.2 Budget Module

- Budget with `limitAmount = 0` — is this intentional (tracking category, no
  limit) or an error?
- Overlapping budget periods for the same category and user — is this allowed?
- When a budget period ends, is the budget automatically archived or does it
  persist indefinitely in the active state?

### 3.3 CSV/Excel Import

- A CSV file with a `BOM` (Byte Order Mark) header — the parser may or may not
  strip it; the first column header could be misread.
- A file with Windows CRLF line endings must be handled correctly.
- A row where the amount column contains text instead of a number — must
  produce a partial success response with the failed row listed, not a 500.
- A file with 100 000 rows — verify that the import does not cause an OOM or a
  request timeout. Consider streaming the parse rather than loading all rows
  into memory.
- The duplicate detection uses `existsByUserIdAndDateAndAmountAndDescription`,
  which requires an exact match on all four fields. A transaction with the same
  date and amount but a slightly different description (e.g. trailing whitespace
  added by the bank) will be imported as a duplicate. Consider normalising
  descriptions before the duplicate check.

### 3.4 Savings Goals

- A savings goal with `targetDate` in the past — still valid for tracking
  historical goals, or should it be rejected?
- `currentAmount > targetAmount` — progress over 100%; must display correctly
  without overflow.

### 3.5 Category Parent Cycles

- `CategoryService.resolveParent` accepts any category owned by the user as a
  parent. It is possible to create a cycle: A → B → C → A. This would cause
  infinite recursion in any tree-rendering client. Consider adding a
  depth-limit check or a cycle-detection query before setting a parent.

---

## 4. Recommended Test Data Strategy

### 4.1 Approach: Direct Repository Setup, Not Factory POST Calls

Integration tests that need database state should set it up via Spring Data
repositories in `@BeforeEach`, not by calling the REST API via MockMvc. This:

- Decouples test setup from the correctness of the creation endpoints
- Makes the test faster (no HTTP round-trip for setup)
- Gives the test full control over entity state (e.g. setting `userId` directly)

`@Autowired` repositories are available because `BaseIntegrationTest` loads a
full `@SpringBootTest` context.

### 4.2 Cleanup: deleteAll() in @BeforeEach, Not @AfterEach

Clean up at the **start** of each test rather than the end. This ensures that
if a test fails mid-way and cannot reach `@AfterEach`, the next test still
starts with a clean slate. Foreign key order matters: delete child tables first
(`transaction`, then `category`, then `account`).

```java
@BeforeEach
void clean() {
    transactionRepository.deleteAll();
    categoryRepository.deleteAll();
    accountRepository.deleteAll();
}
```

### 4.3 Do Not Share Mutable Test Data Between Tests

Every test that needs an account or a category should create its own instance
in `@BeforeEach` or in the test method itself. Sharing a static entity
reference breaks test isolation when tests run in parallel or when one test
modifies shared state.

### 4.4 Use @Sql for Read-Only Baseline Data (Tax Rates, Insurance Types)

Tables like `tax_canton_rate`, `insurance_type`, and `tax_federal_rate` contain
reference data that never changes during a test run. Use an `@Sql` script on
the test class to populate these once, and restore them with
`@Sql(scripts = "cleanup.sql", executionPhase = AFTER_TEST_METHOD)` if any
test modifies them.

### 4.5 Testcontainers Strategy

The project uses `jdbc:tc:postgresql:17:///finyo` via the Testcontainers JDBC
URL, which starts a PostgreSQL 17 container automatically without explicit
container management. This is sufficient for the current scope.

When the test suite grows beyond ~500 tests, consider switching to a shared
container instance (using `@Container` with a static field on `BaseIntegrationTest`)
to avoid starting/stopping PostgreSQL for every test class. The Testcontainers
`@Testcontainers` + static `@Container` pattern handles this automatically with
Ryuk cleanup.

### 4.6 Seed Data Must Be User-Scoped

The `UserProvisioningService` seeds 17 default categories for each user. In
integration tests that call authenticated endpoints, the provisioning filter
runs and may insert these categories unless the user was already provisioned in
a previous test (the in-memory cache). Tests that count categories must account
for these defaults or clear the category table in `@BeforeEach`.
