# Architecture

## Overview

finyo is a personal finance planner. The system exposes a REST API that records and serves
financial data stored in a PostgreSQL database. The current scope covers the API foundation;
a frontend module is planned but not yet created.

## Module Structure

finyo is organised as a single-module Maven project today, with a second module planned:

```
finyo/
├── finyo-api/          # Spring Boot REST API (active)
└── (frontend TBD)      # Angular or React — not yet created
```

## Layered Architecture (finyo-api)

```
Controller → Service → Repository → PostgreSQL
```

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `ch.finyoapi.<feature>` | HTTP request handling, response mapping |
| Service | `ch.finyoapi.<feature>` | Business logic |
| Repository | `ch.finyoapi.<feature>` | Data access via Spring Data JPA |
| Database | PostgreSQL 17 | Persistence |

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Language |
| Spring Boot | 4.0.3 | Application framework |
| Spring MVC | (managed by Spring Boot) | REST layer |
| Spring Security | (managed by Spring Boot) | Authentication and authorisation |
| Spring Data JPA | (managed by Spring Boot) | ORM / repository abstraction |
| PostgreSQL | 17 | Primary database |
| Flyway | (managed by Spring Boot) | Database schema migrations |
| Lombok | (managed by Spring Boot) | Boilerplate reduction |
| SpringDoc / OpenAPI | 3.0.1 | API documentation (Swagger UI) |
| TestContainers | 1.20.5 | Real PostgreSQL container for integration tests |
| JaCoCo | 0.8.14 | Code coverage reporting |

## Database Schema

Schema is managed by Flyway migrations located in:

```
finyo-api/src/main/resources/db/migration/
```

Current migrations:

| Migration | Description |
|---|---|
| `V1__create_endpoint_log_table.sql` | Creates `endpoint_log` table |

**`endpoint_log` table**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | Primary key |
| `message` | `TEXT` | Log message |
| `called_at` | `TIMESTAMPTZ` | Timestamp of the request |
| `endpoint` | `VARCHAR(255)` | Endpoint path |

## Local Infrastructure

Docker Compose (`compose.yml` at the repo root) provides a PostgreSQL 17 instance for local development:

| Setting | Value |
|---|---|
| Image | `postgres:17` |
| Database | `finyo` |
| User | `finyo` |
| Password | `finyo` |
| Port | `5432` |

Start it with:

```bash
docker compose up -d
```

## API Documentation

When the application is running locally, interactive API documentation is available at:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec:** `http://localhost:8080/v3/api-docs`

## Security

Spring Security is enabled. The following endpoints are publicly accessible without authentication:

| Endpoint | Reason |
|---|---|
| `/hello-world` | Health/smoke-test endpoint |
| `/swagger-ui/**` | Swagger UI assets |
| `/swagger-ui.html` | Swagger UI entry point |
| `/v3/api-docs/**` | OpenAPI spec |

All other endpoints require authentication.

## Future / Roadmap

When the frontend module is decided (Angular or React), it will be added as a second module
under the repo root and documented here. The architecture section will be updated to describe
how the frontend communicates with `finyo-api`.
