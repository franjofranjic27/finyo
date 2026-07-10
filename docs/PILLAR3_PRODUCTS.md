# Pillar 3a Product Catalog

## Overview

The pillar 3a product catalog is **admin-managed reference data**: a list of 3a
funds (provider, name, ISIN, equity allocation, TER) that users pick from when
comparing products. It is deliberately **not seeded via Flyway** — unverified
ISINs and TER values baked into a migration are worse than an empty catalog,
because wrong fund data silently distorts every comparison. Instead, verified
data is loaded through the bulk import endpoint described below.

Users only ever see **active** products via the read-only endpoints
`GET /api/v1/pillar3/products` and `POST /api/v1/pillar3/products/compare`.
Everything under `/api/v1/admin/pillar3/products` requires the Keycloak realm
role `admin`.

## Endpoints

Base path: `/api/v1/admin/pillar3/products` — all endpoints require a JWT with
realm role `admin` (enforced via `@PreAuthorize("hasRole('admin')")`).

| Method | Path | Description | Responses |
|---|---|---|---|
| `GET` | `/` | List all products, **including inactive** ones | `200` |
| `POST` | `/` | Create a product | `201`, `400` validation, `409` ISIN exists |
| `PUT` | `/{id}` | Update a product; set `active=false` to deactivate | `200`, `400`, `404`, `409` ISIN taken by another product |
| `DELETE` | `/{id}` | Delete permanently — prefer deactivation via `PUT` | `204`, `404` |
| `POST` | `/import` | Bulk import (upsert by ISIN), see below | `200` with per-row result, `400` empty/malformed payload |

## Import Semantics

`POST /api/v1/admin/pillar3/products/import` takes
`{ "products": [Pillar3ProductRequest, ...] }` and upserts each row:

- **Upsert by normalized ISIN.** ISINs are trimmed and uppercased before
  matching; a row whose normalized ISIN already exists updates that product,
  otherwise a new product is created.
- **Per-row validation and error isolation.** Each row is validated and
  persisted independently — one bad row is reported in `errors` and does
  **not** abort the batch. The endpoint returns `200` even if every row failed;
  `400` only means the payload itself was empty or malformed.
- **Last-wins for duplicate ISINs** within the same payload: the first
  occurrence is created, later occurrences update it.
- **Idempotent.** Re-posting an identical payload yields
  `created=0, updated=n`.
- **Partial overwrite rules.** `active` and `sortOrder` are optional: on
  create they default to `true`/`0`; on update they are only overwritten when
  provided in the row.

### Result Shape

```json
{
  "totalRows": 3,
  "created": 2,
  "updated": 1,
  "failed": 0,
  "errors": []
}
```

| Field | Meaning |
|---|---|
| `totalRows` | Number of rows in the payload |
| `created` | Rows that created a new product |
| `updated` | Rows that updated an existing product (matched by ISIN) |
| `failed` | Rows rejected by validation or persistence errors |
| `errors` | One entry per failed row: `row <n> (ISIN <isin>): <detail>` (1-based row index) |

## Field Reference

Request body for create, update and import rows (`Pillar3ProductRequest`):

| Field | Type | Required | Validation | Notes |
|---|---|---|---|---|
| `provider` | string | yes | not blank, max 100 chars | e.g. `"VIAC"` |
| `name` | string | yes | not blank, max 200 chars | Fund name as on the factsheet |
| `isin` | string | yes | `^[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]$` | Normalized to trimmed uppercase; unique across the catalog |
| `valor` | string | no | `^\d{1,20}$` (numeric, max 20 digits) | Swiss valor number |
| `equityPct` | decimal | yes | `0` – `100` | Equity allocation in percent; drives the modelled return |
| `terPct` | decimal | yes | `0` – `10` | Total expense ratio in percent, up to 3 decimal places |
| `active` | boolean | no | — | Default `true` on create; on update/import only overwritten when provided |
| `sortOrder` | integer | no | — | Default `0` on create; on update/import only overwritten when provided; drives listing order |

## Example: Import via curl (local dev)

Admin endpoints need a token for a user with realm role `admin`. The local
Keycloak realm (`keycloak/finyo-realm.json`) ships the confidential client
`finyo-cli` with the password grant enabled and a seeded admin user
(`admin` / `admin123`), so a token can be fetched directly. These credentials
and the client secret exist only in the local dev realm — the production realm
(`keycloak/finyo-realm.prod.json`) contains neither the `finyo-cli` client nor
any seeded users:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/finyo/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=finyo-cli \
  -d client_secret=finyo-cli-secret \
  -d username=admin \
  -d password=admin123 | jq -r .access_token)
```

Then import products:

```bash
curl -X POST http://localhost:8082/api/v1/admin/pillar3/products/import \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "products": [
      {
        "provider": "Example Bank",
        "name": "Example 3a Fund 45 (EXAMPLE DATA)",
        "isin": "CH0000000001",
        "valor": "1000001",
        "equityPct": 45,
        "terPct": 0.45,
        "sortOrder": 10
      },
      {
        "provider": "Example Bank",
        "name": "Example 3a Fund 75 (EXAMPLE DATA)",
        "isin": "CH0000000002",
        "valor": "1000002",
        "equityPct": 75,
        "terPct": 0.48,
        "sortOrder": 20
      },
      {
        "provider": "Example Insurer",
        "name": "Example 3a Equity 100 (EXAMPLE DATA)",
        "isin": "CH0000000003",
        "equityPct": 100,
        "terPct": 0.55,
        "active": true,
        "sortOrder": 30
      }
    ]
  }'
```

> **Example data only.** The ISINs, valors and TERs above are placeholders that
> merely satisfy the format validation. For a real catalog, take ISIN, equity
> allocation and TER from the provider's current factsheet — never guess them.

Re-running the same command returns `{"totalRows":3,"created":0,"updated":3,...}`.

## Return Model

`equityPct` drives the modelled return used by
`POST /api/v1/pillar3/products/compare`: gross return = 1% base + 5% × equity
share (e.g. 45% equity → 3.25% p.a.). `terPct` is deducted from the gross
return each year of the projection, so the comparison ranks products by net
final capital. Every product response also exposes the derived
`grossReturnPct` and `netReturnPct`, so clients never re-implement the formula
(single source: `Pillar3ReturnModel`).
