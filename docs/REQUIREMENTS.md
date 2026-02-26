# finyo — Product Requirements

> Status: **Draft** — last updated 2026-02-25
> This document is the authoritative reference for what finyo is and how it should be built.
> It drives the implementation and is kept in sync as decisions evolve.

---

## Table of Contents

1. [Vision & Goals](#1-vision--goals)
2. [User Roles & Multi-Tenancy](#2-user-roles--multi-tenancy)
3. [Functional Requirements](#3-functional-requirements)
   - 3.1 [Transaction Management](#31-transaction-management)
   - 3.2 [Spending Analytics](#32-spending-analytics)
   - 3.3 [Budget Planning](#33-budget-planning)
   - 3.4 [Savings Goals](#34-savings-goals)
   - 3.5 [Investment Portfolio](#35-investment-portfolio)
   - 3.6 [Swiss Tax Engine](#36-swiss-tax-engine)
   - 3.7 [Third Pillar Calculator (3. Säule)](#37-third-pillar-calculator-3-säule)
   - 3.8 [Insurance Overview](#38-insurance-overview)
   - 3.9 [News Widget](#39-news-widget)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [Technical Architecture](#5-technical-architecture)
6. [API Design](#6-api-design)
7. [Security & Authentication](#7-security--authentication)
8. [Frontend Design](#8-frontend-design)
9. [CLI — Spring Shell](#9-cli--spring-shell)
10. [Testing Strategy](#10-testing-strategy)
11. [CI/CD](#11-cicd)
12. [Deployment](#12-deployment)
13. [MVP Scope](#13-mvp-scope)
14. [Implementation Phases](#14-implementation-phases)

---

## 1. Vision & Goals

**finyo** is a self-hosted personal finance web application targeting Swiss residents.
It gives a clear, real-time overview of where money goes, how much is saved,
what investments look like, and how financial decisions affect taxes.

**Core goals:**

- Provide an intuitive spending and budget overview (weekly, monthly, by category)
- Track savings goals and investment positions
- Calculate Swiss income tax including the impact of the third pillar (3a)
- Surface useful financial content (news, insurance considerations)
- Expose a clean OpenAPI and a Spring Shell CLI so that future agents can interact
  with finyo programmatically
- Be production-ready for self-hosting via a single `docker compose up`

---

## 2. User Roles & Multi-Tenancy

### Multi-Tenancy Strategy

finyo uses **row-level multi-tenancy**: a single PostgreSQL schema shared by all users,
with every user-owned entity carrying a `user_id` column that maps to the Keycloak
`sub` (subject) claim in the JWT. The API layer automatically filters all queries to
the authenticated user's ID. No data from one user is ever visible to another.

This is the standard approach for personal SaaS tools. It is simple, requires no schema
management overhead, and scales well for hundreds of users.

### Roles

| Role | Description |
|---|---|
| `ROLE_USER` | Regular user — full access to their own data only |
| `ROLE_ADMIN` | Administrator — can query across all users; manages system configuration |

Roles are defined in the Keycloak realm and propagated via the JWT `realm_access.roles` claim.

### Realm

A single Keycloak realm named `finyo` with one client `finyo-api`.
OIDC authorization code flow with PKCE for the React frontend.
Client-credentials flow for the CLI (Spring Shell).

---

## 3. Functional Requirements

### 3.1 Transaction Management

#### Overview
Transactions are the foundation of all analytics and budgeting.
A transaction represents a single financial event (debit or credit) linked to
an account and one or more user-defined categories.

#### Data Model

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | VARCHAR | Keycloak subject — row-level isolation |
| `amount` | DECIMAL(19,4) | Positive = income, negative = expense |
| `currency` | VARCHAR(3) | ISO 4217, default CHF |
| `date` | DATE | Transaction date (not booking date) |
| `description` | TEXT | Free-text description |
| `category_id` | UUID FK | User-defined category |
| `account_id` | UUID FK | The account this transaction belongs to |
| `source` | ENUM | `MANUAL`, `CSV_IMPORT` |
| `created_at` | TIMESTAMPTZ | Record creation time |
| `updated_at` | TIMESTAMPTZ | Record update time |

#### Features

- **Manual entry**: Form in the UI to enter a single transaction
- **CSV import**: Upload a CSV file and map columns to transaction fields
  - The mapping UI lets users assign which column is date, amount, description, etc.
  - Preview the mapped rows before confirming import
  - Duplicate detection: warn if a transaction with the same date + amount + description
    already exists (user can override and import anyway)
  - Support common Swiss bank CSV formats as presets (UBS, Raiffeisen, PostFinance,
    ZKB, BEKB) — users can also define a custom mapping
- **Excel import**: Same as CSV but supports `.xlsx` / `.xls`
- **CRUD**: Create, read, update, delete individual transactions
- **Filtering**: By date range, account, category, amount range, source
- **Pagination**: Server-side; default 50 rows per page

#### Accounts

Each transaction belongs to an account. An account is a user-defined wallet
(e.g. "Raiffeisen Privatkonto", "PostFinance Sparkonto", "Crypto Wallet").

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | VARCHAR | Row-level isolation |
| `name` | VARCHAR(100) | Display name |
| `type` | ENUM | `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `INVESTMENT`, `CASH`, `OTHER` |
| `currency` | VARCHAR(3) | Default CHF |
| `initial_balance` | DECIMAL(19,4) | Starting balance at account creation |
| `color` | VARCHAR(7) | Hex color for UI |

#### Categories

Categories are fully user-defined and hierarchical (one level of nesting):
a parent category (e.g. "Living") can have sub-categories ("Groceries", "Rent").

| Field | Notes |
|---|---|
| `id`, `user_id` | Standard |
| `name` | e.g. "Groceries" |
| `parent_id` | NULL for top-level |
| `icon` | Emoji or icon identifier |
| `color` | Hex color |
| `type` | `EXPENSE` or `INCOME` |

A set of sensible default categories is created for every new user:

**Expenses:** Housing, Groceries, Transport, Health, Insurance, Subscriptions,
Dining & Restaurants, Entertainment, Clothing, Education, Travel, Savings Transfers

**Income:** Salary, Bonus, Freelance, Investment Income, Other Income

---

### 3.2 Spending Analytics

The analytics module aggregates transactions and presents them in charts and summary cards.

#### Time-Based Summaries

All summaries are filterable by account and/or category.

| View | Description |
|---|---|
| Last 7 days | Total spent in the rolling last 7 days |
| Last 30 days | Rolling 30 days |
| This month | Calendar month (1st to today) |
| Last month | Previous full calendar month |
| Last 3 months | Quarter view |
| Last 6 months | Half-year view |
| Last 12 months | Full-year rolling |
| Custom range | User-defined start and end date |

#### Charts

| Chart | Type | Description |
|---|---|---|
| Spending by category | Donut / pie | Top-level categories, last 30 days |
| Spending over time | Bar chart | Monthly bars, rolling 12 months |
| Income vs Expense | Grouped bar or area chart | Net per month |
| Daily spending | Line chart | Spend per day in the selected range |
| Top merchants/descriptions | Horizontal bar | Most frequent expense descriptions |

#### Summary Cards (Dashboard)

- Net this month (income − expenses)
- Total spent this month
- Biggest spending category this month
- Compared to last month (% change)
- Average daily spend (current month)

---

### 3.3 Budget Planning

#### Overview
Users define monthly budgets per category. The system tracks actual spending against
the budget in real time.

#### Data Model

| Field | Notes |
|---|---|
| `id`, `user_id` | Standard |
| `category_id` | Category the budget applies to |
| `amount` | Budget limit in CHF |
| `period` | `MONTHLY` (MVP), `WEEKLY` (future) |
| `valid_from` | First month the budget is active |
| `valid_until` | NULL = indefinite |

#### Features

- Create / edit / delete budget entries per category per month
- Dashboard widget: progress bars showing actual vs budget per category
- Alert indicator when a category exceeds 80% or 100% of its budget
- Total budget summary: sum of all category budgets vs total actual spending
- Roll-forward: copy last month's budget to the current month with one click

---

### 3.4 Savings Goals

#### Overview
Users define savings goals. finyo tracks progress toward each goal.

#### Data Model

| Field | Notes |
|---|---|
| `id`, `user_id` | Standard |
| `name` | e.g. "Emergency fund", "New car", "Holiday Japan" |
| `target_amount` | Target in CHF |
| `current_amount` | Current saved amount (manually updated) |
| `target_date` | Desired completion date (optional) |
| `account_id` | Optional: link to a specific savings account |
| `icon` | Emoji or icon |
| `color` | Hex color |

#### Features

- Create, edit, archive, delete goals
- Progress bar: current vs target amount
- Monthly savings required to hit the target date (if set)
- Dashboard widget: overview of all active goals sorted by % completion

---

### 3.5 Investment Portfolio

#### Overview
Users can register financial instruments by VALOR number, ISIN, ticker symbol, or
common name. finyo fetches live market data from the SIX Swiss Exchange API and
displays structured instrument cards.

#### Instrument Card

Each registered instrument shows a card with the following data (sourced from SIX):

| Field | Notes |
|---|---|
| Name | Full instrument name |
| VALOR / ISIN | Identifier(s) |
| Exchange | Primary listing exchange |
| Currency | Trading currency |
| Last price | Latest available price |
| Price change | Today %, 1W %, 1M %, YTD %, 1Y % |
| 52-week high / low | Range indicator |
| Market cap | If available |
| Dividend yield | If applicable |
| Instrument type | Stock, ETF, Bond, Fund, … |
| Sector | If applicable (stocks) |
| TER | Total Expense Ratio (ETFs/Funds) |

Cards are displayed in a responsive grid (2–4 columns depending on screen width).
Users can reorder cards via drag-and-drop.

#### Portfolio Positions (non-MVP)

In a later phase, users can log positions (number of units bought at a price and date).
finyo then calculates:

- Total invested (cost basis)
- Current market value
- Unrealised gain/loss (CHF and %)
- Portfolio allocation by instrument and sector
- Historical portfolio value chart

#### Data Model

| Entity | Key Fields |
|---|---|
| `instrument` | `id`, `user_id`, `valor`, `isin`, `ticker`, `name`, `type`, `sort_order` |
| `position` *(non-MVP)* | `id`, `instrument_id`, `quantity`, `purchase_price`, `purchase_date` |

#### SIX Integration

- Use the SIX Swiss Exchange REST/JSON API (or SIX Financial Information services)
  for instrument lookups and price data
- Cache responses in Redis or in-memory (Caffeine) for 15 minutes to avoid rate limits
- Store the last known price in the database so cards still display data when the
  external API is unavailable

---

### 3.6 Swiss Tax Engine

#### Scope
Calculate approximate Swiss income tax (federal + cantonal + communal) for a given
tax year and personal situation. Initial focus on **Canton St. Gallen**; the model
is designed to accommodate all 26 cantons over time via a pluggable canton-rate table.

#### Input Parameters (Manual Entry)

| Parameter | Notes |
|---|---|
| Tax year | 4-digit year |
| Canton | Dropdown, all 26 cantons |
| Municipality | Lookup within selected canton |
| Civil status | Single, Married, Single parent |
| Number of children | Integer |
| Gross employment income | CHF |
| Self-employment income | CHF |
| Investment income (dividends) | CHF |
| Rental income | CHF |
| Deductions — professional expenses | CHF (or % of income up to legal cap) |
| Deductions — insurance premiums | CHF |
| Deductions — charitable donations | CHF |
| Deductions — debt interest | CHF |
| Wealth (Vermögen) | Total net assets in CHF |
| 3a contributions | CHF (auto-populated from 3rd pillar module) |

The input form uses sensible defaults and inline help text explaining each field
(e.g. what counts as professional expense deduction in CH law).

#### Output

| Output | Notes |
|---|---|
| Taxable income (after deductions) | CHF |
| Federal tax (DBSt) | CHF |
| Cantonal tax | CHF |
| Communal tax | CHF |
| Church tax | CHF (optional, opt-in) |
| Total tax burden | CHF |
| Effective tax rate | % |
| Marginal tax rate | % |
| Wealth tax | CHF |
| Total (income + wealth tax) | CHF |

Results shown as a breakdown table plus a stacked bar chart (federal / cantonal /
communal / church).

#### Tax Rate Data

Tax rates are stored as database tables (managed via Flyway) and can be updated
per tax year without code changes:

- `tax_canton_rate` — cantonal income tax rates (tariff tables by income bracket)
- `tax_commune_multiplier` — communal multiplier per municipality per year
- `tax_federal_rate` — federal tariff tables (same for all cantons)

#### Withholding Tax (Verrechnungssteuer)

Display a calculated estimate of the 35% withholding tax on dividends and fund
distributions, and the amount reclaimable via the tax return (for Swiss residents
with correct declaration).

---

### 3.7 Third Pillar Calculator (3. Säule 3a)

#### Overview
The Swiss tied pension pillar 3a allows contributions to be deducted from taxable
income up to a legally defined annual cap. finyo models the tax savings and projected
future value of 3a contributions.

#### Features

**1. Annual contribution input**

- Current 3a contribution for the year (CHF)
- Maximum deductible amount shown (updated per year: currently CHF 7'258 for employees,
  CHF 36'288 for self-employed, subject to annual index adjustment)
- "Fill to maximum" button

**2. Tax saving calculation**

- Shows how much income tax is saved by the current 3a contribution
- "What if max?" — recalculates tax with max 3a to show potential additional saving
- Feeds directly into the tax engine (Section 3.6) as a deduction

**3. Future value projection**

| Input | Notes |
|---|---|
| Current 3a balance | CHF |
| Annual contribution | CHF |
| Assumed annual return | % (user-adjustable, default 3%) |
| Years to retirement | Derived from user profile or manual |

Output: projected balance at retirement, year-by-year chart (compound growth curve)

**4. Payout tax estimate**

Swiss law taxes 3a payouts at a preferential flat rate (separate from income tax,
typically 2–5% depending on canton and amount). Display:

- Estimated payout tax at retirement based on projected balance
- Net value after payout tax
- Comparison: net 3a value vs equivalent taxable savings (same amount, taxed annually)

**5. Institution comparison (non-MVP)**

Compare projected returns across common 3a providers (VIAC, Finpension, PostFinance,
bank accounts) — rates stored as reference data, updated manually.

---

### 3.8 Insurance Overview

#### Overview
A guided, dynamic overview of insurance types relevant to Swiss residents.
In the MVP this is a structured mock/static content display that demonstrates the
intended UI. A later phase adds personalised recommendations based on the user profile.

#### Insurance Categories

| Category | Mandatory? | Notes |
|---|---|---|
| Krankenkasse (KVG) | Yes | Basic health insurance; links to Comparis / Priminfo |
| Zusatzversicherung (VVG) | No | Supplemental health |
| Haftpflichtversicherung | Recommended | Personal liability |
| Hausratversicherung | Recommended | Contents insurance |
| Lebensversicherung / Todesfallrisiko | Depends | Life insurance |
| Erwerbsunfähigkeitsversicherung | Recommended | Disability income |
| Rechtsschutzversicherung | Optional | Legal protection |
| Reiseversicherung | Optional | Travel |
| Auto / Motorfahrzeug | If applicable | Mandatory for vehicle owners |
| BVG (Pensionskasse) | Yes (employed) | Occupational pension — info card |

#### Card Structure

Each insurance type shows a card with:

- Name + short description (EN / DE)
- Mandatory / recommended / optional badge
- Why it matters for Swiss residents
- Typical annual cost range (CHF)
- Link to an external comparison service
- "I have this" toggle (user marks what they already have)

#### Dynamic Recommendations (post-MVP)

Based on user profile fields (age, marital status, children, home owner, car owner,
employment status), finyo highlights which insurances are most relevant and flags
any gaps.

---

### 3.9 News Widget

#### Overview
An embeddable widget displaying a live feed of financial news from configurable RSS sources.
MVP is read-only; sources are pre-configured.

#### Default RSS Sources

| Source | URL | Language |
|---|---|---|
| cash.ch | `https://www.cash.ch/rss/news` | DE |
| Handelszeitung | `https://www.handelszeitung.ch/rss.xml` | DE |
| NZZ Wirtschaft | `https://www.nzz.ch/wirtschaft.rss` | DE |
| Reuters Business (EN) | `https://feeds.reuters.com/reuters/businessNews` | EN |

#### Features

- Fetch and cache RSS feed items in the backend (cache TTL: 15 minutes)
- Display items as cards: headline, source name, publication date, excerpt, link
- "Open in new tab" on click (no in-app reader in MVP)
- Filter by language (DE / EN toggle on the widget)
- Responsive grid layout (1–3 columns)

#### Data Flow

```
RSS Source → finyo-api (RSS parser + cache) → REST endpoint → React widget
```

The backend parses RSS/Atom feeds server-side. The frontend never calls external
sources directly — this avoids CORS issues and allows caching.

---

## 4. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Languages** | UI available in English (EN) and German (DE); language switch in header; stored in user preference |
| **Dark / Light mode** | Theme toggle in header; preference stored in localStorage; default follows OS setting |
| **Accessibility** | WCAG 2.1 AA minimum; keyboard navigation; ARIA labels on interactive elements |
| **Performance** | Dashboard initial load < 2s on local network; API responses < 500ms for read endpoints under normal load |
| **Security** | All endpoints (except auth callback and health) require valid Keycloak JWT; HTTPS in production; no sensitive data in logs |
| **Auditability** | All write operations (create/update/delete) are logged with user ID, timestamp, and action |
| **Data isolation** | Strict row-level multi-tenancy; no user can access another user's data via any API endpoint |
| **Offline resilience** | Cached investment data and news are served when external APIs are unavailable |
| **Code quality** | SonarCloud quality gate must pass on every PR; no critical/blocker issues unresolved |
| **Test coverage** | Minimum 80% line coverage on the API module (enforced by SonarCloud gate) |
| **i18n** | All user-visible strings externalised; no hardcoded text in frontend components |

---

## 5. Technical Architecture

### 5.1 Module Structure

```
finyo/
├── finyo-api/          # Spring Boot REST API (Java 25, Maven)
├── finyo-ui/           # React frontend (Vite, TypeScript)
├── compose.yml         # Full-stack local development + production compose
├── docs/               # Architecture, requirements, testing, workflows
└── .github/workflows/  # CI/CD pipelines
```

### 5.2 Backend — finyo-api

**Package structure:**

```
ch.finyoapi/
├── transaction/        # Transactions, accounts, categories
├── analytics/          # Spending summaries and chart data
├── budget/             # Budget planning
├── savings/            # Savings goals
├── investment/         # Instrument registry + SIX integration
├── tax/                # Swiss tax engine
│   ├── federal/
│   ├── cantonal/
│   └── pillar3/        # 3a calculator
├── insurance/          # Insurance overview
├── news/               # RSS feed aggregation
├── auth/               # Security config, JWT extraction
├── cli/                # Spring Shell commands
├── common/             # Shared DTOs, exceptions, audit
└── FinyoApiApplication.java
```

**Key libraries to add to existing stack:**

| Library | Purpose |
|---|---|
| `spring-boot-starter-oauth2-resource-server` | JWT validation via Keycloak JWKS |
| `spring-shell-starter` | CLI interface |
| `rome` (RSS parser) | RSS/Atom feed parsing |
| `caffeine` | In-memory caching for SIX data and news |
| `spring-boot-starter-cache` | Cache abstraction |
| `apache-poi` | Excel (.xlsx) import |
| `opencsv` or `univocity-parsers` | CSV parsing |
| `springdoc-openapi` | Already present — OpenAPI docs |
| `micrometer` | Metrics (for future monitoring) |

### 5.3 Frontend — finyo-ui

**Stack:**

| Technology | Purpose |
|---|---|
| React 19 | UI framework |
| TypeScript | Type safety |
| Vite | Build tool |
| Tailwind CSS v4 | Styling |
| Shadcn/UI | Component library (built on Radix UI) |
| Recharts | Charts and data visualisation |
| React Router v7 | Client-side routing |
| TanStack Query | Server-state management + caching |
| Oidc-client-ts | Keycloak OIDC integration (PKCE flow) |
| React i18next | Internationalisation (EN/DE) |
| Playwright | End-to-end testing |
| Vitest | Unit / component tests |

**Page structure:**

```
/                       → Dashboard (overview cards + charts)
/transactions           → Transaction list + import
/transactions/new       → Manual entry form
/budget                 → Budget overview + editing
/savings                → Savings goals
/investments            → Investment portfolio / instrument cards
/tax                    → Tax calculator
/tax/pillar3            → 3rd pillar calculator
/insurance              → Insurance overview
/settings               → User profile, accounts, categories, language, theme
/admin/*                → Admin views (ROLE_ADMIN only)
```

### 5.4 Infrastructure

**Updated `compose.yml` services:**

| Service | Image | Port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:17` | 5432 | Primary database |
| `keycloak` | `quay.io/keycloak/keycloak:26` | 8081 | Auth server |
| `finyo-api` | `finyo-api:latest` (built locally) | 8080 | Spring Boot API |
| `finyo-ui` | `finyo-ui:latest` (built locally) | 3000 | React frontend |

Keycloak is bootstrapped with an import of a pre-configured `finyo-realm.json`
(realm, client, roles) so that `docker compose up` results in a fully working stack
with zero manual Keycloak configuration.

---

## 6. API Design

### Conventions

- Base path: `/api/v1`
- All endpoints require `Authorization: Bearer <JWT>` except `/actuator/health`
- Response format: JSON
- Error format: RFC 9457 Problem Details (`application/problem+json`)
- Pagination: `?page=0&size=50&sort=date,desc` (Spring Data Pageable)
- All timestamps: ISO 8601 UTC
- All amounts: `DECIMAL` as JSON string (to avoid floating point issues)

### Endpoint Groups

| Resource | Base Path |
|---|---|
| Accounts | `/api/v1/accounts` |
| Categories | `/api/v1/categories` |
| Transactions | `/api/v1/transactions` |
| Import | `/api/v1/transactions/import` |
| Analytics | `/api/v1/analytics` |
| Budgets | `/api/v1/budgets` |
| Savings goals | `/api/v1/savings` |
| Instruments | `/api/v1/instruments` |
| Positions | `/api/v1/instruments/{id}/positions` |
| Tax calculation | `/api/v1/tax/calculate` |
| Third pillar | `/api/v1/tax/pillar3/calculate` |
| Insurance | `/api/v1/insurance` |
| News | `/api/v1/news` |
| User profile | `/api/v1/profile` |
| Admin — users | `/api/v1/admin/users` |

### Key Endpoints (non-exhaustive)

```
GET  /api/v1/analytics/summary?range=LAST_30_DAYS&accountId=...
GET  /api/v1/analytics/by-category?range=LAST_30_DAYS
GET  /api/v1/analytics/over-time?range=LAST_12_MONTHS&groupBy=MONTH

POST /api/v1/transactions/import
     Body: multipart/form-data (file + mapping config)

GET  /api/v1/instruments/{valor}/market-data
     Returns live card data from SIX (cached 15 min)

POST /api/v1/tax/calculate
     Body: TaxInputDTO
     Returns: TaxResultDTO

POST /api/v1/tax/pillar3/calculate
     Body: Pillar3InputDTO
     Returns: Pillar3ResultDTO

GET  /api/v1/news?lang=de&limit=20
```

### OpenAPI

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

All endpoints are documented with `@Operation`, `@ApiResponse`, and schema annotations.
The spec is generated automatically by SpringDoc.

---

## 7. Security & Authentication

### Keycloak Setup

| Setting | Value |
|---|---|
| Realm | `finyo` |
| Client | `finyo-api` (confidential, resource server) |
| Client | `finyo-ui` (public, PKCE) |
| Roles (realm-level) | `ROLE_USER`, `ROLE_ADMIN` |
| Token lifespan | Access: 5 min, Refresh: 30 min |
| HTTPS | Required in production; HTTP allowed for local dev |

### Spring Security Configuration

- `spring-boot-starter-oauth2-resource-server` validates JWTs against Keycloak's JWKS endpoint
- A custom `JwtAuthenticationConverter` maps `realm_access.roles` to Spring `GrantedAuthority`
- A `UserContextHolder` (request-scoped bean) extracts `sub`, `preferred_username`, and roles
  from the JWT and makes them available to service layer without coupling to Spring Security directly
- All repository methods in service classes apply a `user_id = currentUserId()` filter
  automatically via a base entity or specification pattern

### Endpoint Security

| Pattern | Access |
|---|---|
| `/actuator/health` | Public |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public (dev) / protected (prod) |
| `/api/v1/admin/**` | `ROLE_ADMIN` only |
| `/api/v1/**` | `ROLE_USER` or `ROLE_ADMIN` |

### Frontend Auth

- `oidc-client-ts` handles the OIDC authorization code + PKCE flow
- Tokens stored in memory (not localStorage) to reduce XSS risk
- Silent token renewal via refresh token
- Unauthenticated routes redirect to Keycloak login page

---

## 8. Frontend Design

### Design Principles

- **Dark / Light mode**: Tailwind `dark:` variant; default from `prefers-color-scheme`;
  toggle in header; stored in `localStorage`
- **Responsive**: Mobile-first layout; optimised for 1280px+ desktop; usable on tablet
- **Modern aesthetic**: Clean cards, generous whitespace, subtle borders; no gradients overload
- **Swiss-specific**: CHF as default currency; Swiss German locale for number formatting
  (e.g. `1'234.50`); date format `DD.MM.YYYY` in DE locale

### Component Library

Shadcn/UI components used as the base. Custom finyo components built on top:

- `<TransactionCard>` — compact transaction row
- `<BudgetProgressBar>` — category budget with % fill
- `<InstrumentCard>` — investment instrument with market data
- `<SavingsGoalCard>` — goal with progress ring
- `<InsuranceCard>` — insurance type card with have/need toggle
- `<NewsCard>` — RSS news item
- `<TaxBreakdownChart>` — stacked bar showing tax components
- `<SpendingDonut>` — category spending donut chart (Recharts)

### Internationalisation

- Framework: `react-i18next`
- Language files: `src/i18n/en.json`, `src/i18n/de.json`
- Language switch in header (flag icons EN / DE)
- Stored in user profile (backend) and `localStorage` (for unauthenticated landing)
- All user-visible strings must be in translation files — no hardcoded text

### Playwright Tests

Tests live in `finyo-ui/e2e/` and cover critical user flows:

| Test | Flow |
|---|---|
| `auth.spec.ts` | Login and logout via Keycloak |
| `transactions.spec.ts` | Create, edit, delete a transaction |
| `import.spec.ts` | Upload CSV and confirm import |
| `budget.spec.ts` | Create a budget and verify progress bar |
| `savings.spec.ts` | Create a savings goal |
| `investments.spec.ts` | Add an instrument and verify card |
| `tax.spec.ts` | Fill tax form and verify result |
| `theme.spec.ts` | Toggle dark/light mode |
| `language.spec.ts` | Switch EN/DE and verify labels |

---

## 9. CLI — Spring Shell

The CLI allows querying and managing finyo data from the terminal. It is the interface
future AI agents will use to interact with the system programmatically.

### Authentication

The CLI authenticates via Keycloak client-credentials flow (machine-to-machine).
Credentials are configured in `application.yaml` or environment variables.

### Command Groups

```
transactions
  transactions list [--from <date>] [--to <date>] [--category <name>]
  transactions add --amount <n> --date <d> --description <s> --category <name>
  transactions delete --id <uuid>
  transactions import --file <path> [--preset ubs|raiffeisen|postfinance|custom]

analytics
  analytics summary [--range last7d|last30d|thisMonth|lastMonth|last6m|last12m]
  analytics by-category [--range ...]
  analytics net-worth

budget
  budget list
  budget set --category <name> --amount <n>
  budget status [--month <YYYY-MM>]

savings
  savings list
  savings add --name <s> --target <n> [--date <YYYY-MM-DD>]
  savings update --id <uuid> --current <n>

investments
  investments list
  investments add --valor <n>
  investments remove --id <uuid>
  investments prices

tax
  tax calculate --income <n> --canton sg [--year 2025] [...]
  tax pillar3 --balance <n> --contribution <n> --years <n>

news
  news list [--lang de|en] [--limit 10]

profile
  profile show
  profile set-language en|de

admin
  admin users list
  admin users show --id <uuid>
```

### Output Formats

- Default: human-readable table (Spring Shell's built-in `TableModel`)
- `--format json`: JSON output (for agent/script consumption)
- `--format csv`: CSV output (for piping to other tools)

---

## 10. Testing Strategy

### Backend (finyo-api)

Follows the conventions established in `docs/TESTING.md`:

| Type | Suffix | Scope |
|---|---|---|
| Unit test | `*Test` | Single class, no Spring context, fast |
| Integration test | `*IT` | Full Spring Boot context, TestContainers PostgreSQL |

**Coverage target:** 80% line coverage (enforced by SonarCloud quality gate)

**Test scope per module:**

- Transaction service: unit tests for import mapping logic, duplicate detection
- Tax engine: unit tests for each canton's rate table calculation (property-based testing encouraged)
- Analytics: unit tests for aggregation logic; integration tests for query correctness
- CLI: integration tests using Spring Shell's test support
- Security: integration tests verifying that endpoints reject missing/invalid tokens

### Frontend (finyo-ui)

| Type | Tool | Scope |
|---|---|---|
| Unit / component | Vitest + React Testing Library | Individual components and hooks |
| End-to-end | Playwright | Full user flows (see Section 8) |

Playwright tests run against a Docker Compose stack with Keycloak configured in
test mode (no email verification, test user pre-created).

### CI Coverage

All tests run on every PR via `ci.yml`. Coverage reports are submitted to SonarCloud
via `sonar.yml`.

---

## 11. CI/CD

Existing workflows remain unchanged. New additions:

| Workflow | Trigger | Action |
|---|---|---|
| `ci.yml` | PR | `./mvnw verify` — backend unit + integration tests |
| `sonar.yml` | Push to `main`, PR | SonarCloud analysis + quality gate |
| `ci-ui.yml` *(new)* | PR | `npm run build && npm run test && npm run e2e` |
| `claude.yml` *(planned)* | PR comment `@claude` | Claude Code agent (requires secret) |
| `claude-code-review.yml` *(planned)* | PR opened | Automated code review (requires secret) |

**New scopes for commit convention:**

| Scope | Path | When to use |
|---|---|---|
| `ui` | `finyo-ui/` | React frontend code |
| `auth` | Keycloak config, realm JSON, security config | Auth-related changes |
| `tax` | Tax engine, rate tables | Tax calculation changes |
| `cli` | Spring Shell commands | CLI changes |

---

## 12. Deployment

### Local Development (existing)

```bash
docker compose up -d      # Start Postgres + Keycloak
cd finyo-api && ./mvnw spring-boot:run
cd finyo-ui && npm run dev
```

### Full Stack via Docker Compose

Target: `docker compose up` starts everything with zero manual steps.

```yaml
# compose.yml services:
postgres:    # PostgreSQL 17
keycloak:    # Keycloak 26, realm imported from finyo-realm.json
finyo-api:   # Spring Boot jar (built by ./mvnw package)
finyo-ui:    # Nginx serving Vite production build
```

**Keycloak bootstrap**: Realm is imported on first start via
`--import-realm` and a `finyo-realm.json` committed to the repo.
The JSON defines the realm, both clients, roles, and a seeded `admin` user.
Credentials for the admin user are set via environment variables.

### Self-Hosted Production

For production, the same `compose.yml` is used with:

- HTTPS termination via a reverse proxy (Caddy or Traefik — `compose.prod.yml` override)
- Secrets via environment variables or Docker secrets
- Persistent volumes for PostgreSQL and Keycloak data
- Health checks on all services

---

## 13. MVP Scope

The MVP is the minimal set of features that delivers a complete, usable personal finance
overview. Everything else is labelled "post-MVP" in this document.

**MVP includes:**

- [x] Authentication via Keycloak (login, logout, token refresh)
- [x] Account management (CRUD)
- [x] Category management (CRUD, user-defined, defaults on signup)
- [x] Transaction management (manual entry + CSV import)
- [x] Spending analytics (summary cards + donut + monthly bar chart)
- [x] Budget planning (create budgets, progress bars)
- [x] Savings goals
- [x] Investment instrument cards (VALOR/ISIN lookup via SIX, no portfolio tracking)
- [x] Swiss tax calculator (income tax, St. Gallen canton, manual input)
- [x] Third pillar 3a calculator (tax saving + future value projection)
- [x] Insurance overview (static/mock cards)
- [x] News widget (RSS, read-only, pre-configured sources)
- [x] Dark/light mode
- [x] English and German localisation
- [x] OpenAPI documentation (Swagger UI)
- [x] Spring Shell CLI (all command groups)
- [x] Docker Compose full-stack setup
- [x] Playwright e2e test suite skeleton
- [x] SonarCloud quality gate passing

**Post-MVP (future phases):**

- Portfolio position tracking (cost basis, unrealised P&L)
- All 26 cantons for tax calculation
- Dynamic insurance recommendations
- 3a institution comparison
- Auto-classification of transactions
- Excel import presets for Swiss banks
- News source configuration by user
- Wealth tax calculation
- Withholding tax (Verrechnungssteuer) reclaimation estimate

---

## 14. Implementation Phases

### Phase 1 — Foundation (Backend)

1. Add Keycloak to `compose.yml`; create `finyo-realm.json`
2. Add OAuth2 resource server dependency; configure JWT validation
3. Implement `UserContextHolder` (JWT extraction)
4. Create Account, Category, Transaction entities + Flyway migrations
5. Transaction CRUD API + unit/integration tests
6. CSV import endpoint + column mapping logic
7. Category default-seeding on user first login

### Phase 2 — Analytics & Budget

1. Analytics service + endpoints (summary, by-category, over-time)
2. Budget entity + CRUD API
3. Budget vs actual calculation endpoint
4. Savings goal entity + CRUD API

### Phase 3 — Investments

1. Instrument entity + CRUD API
2. SIX Swiss Exchange integration (client + caching)
3. Market data endpoint per instrument

### Phase 4 — Swiss Tax Engine

1. Tax rate data model + Flyway seed migrations (St. Gallen + Federal)
2. Tax calculation service (federal + cantonal + communal)
3. Tax API endpoint
4. Third pillar 3a calculation service + API endpoint

### Phase 5 — News & Insurance

1. RSS feed parser + caching
2. News API endpoint
3. Insurance reference data + API endpoint

### Phase 6 — Spring Shell CLI

1. Add Spring Shell dependency
2. Implement all command groups (delegates to existing service layer)

### Phase 7 — Frontend

1. Bootstrap `finyo-ui` with Vite + React + TypeScript + Tailwind + Shadcn/UI
2. OIDC auth flow (oidc-client-ts)
3. API client layer (TanStack Query)
4. i18n setup (react-i18next, EN + DE)
5. Dashboard page
6. Transactions page + import flow
7. Budget page
8. Savings goals page
9. Investments page (instrument cards)
10. Tax calculator page + Pillar 3a page
11. Insurance overview page
12. News widget
13. Settings page (accounts, categories, profile)
14. Playwright test suite

### Phase 8 — Production Hardening

1. `compose.prod.yml` overlay (HTTPS, secrets, health checks)
2. CI workflow for frontend (`ci-ui.yml`)
3. SonarCloud quality gate enforcement at 80% coverage
4. Performance and security review
