# finyo

[![CI](https://img.shields.io/github/actions/workflow/status/franjofranjic27/finyo/ci.yml?branch=main&style=for-the-badge&label=CI)](https://github.com/franjofranjic27/finyo/actions/workflows/ci.yml)
[![Backend Quality Gate](https://img.shields.io/sonar/quality_gate/franjofranjic27_finyo?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&label=backend%20quality%20gate)](https://sonarcloud.io/summary/overall?id=franjofranjic27_finyo)
[![Frontend Quality Gate](https://img.shields.io/sonar/quality_gate/franjofranjic27_finyo-fe?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&label=frontend%20quality%20gate)](https://sonarcloud.io/summary/overall?id=franjofranjic27_finyo-fe)
[![Backend coverage](https://img.shields.io/sonar/coverage/franjofranjic27_finyo?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&label=backend%20coverage)](https://sonarcloud.io/summary/overall?id=franjofranjic27_finyo)
[![Frontend coverage](https://img.shields.io/sonar/coverage/franjofranjic27_finyo-fe?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&label=frontend%20coverage)](https://sonarcloud.io/summary/overall?id=franjofranjic27_finyo-fe)
[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)](#tech-stack)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?style=for-the-badge)](#tech-stack)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

finyo is a personal finance planner for Swiss residents. It tracks accounts,
transactions, budgets and investments, and adds Swiss-specific tooling such as a
pillar 3a tax calculator. A Spring Boot REST API and a React single-page app are
deployed as a self-hosted stack behind Keycloak authentication.

## Project Status

Live at [finyo.frama-maschinenhandel.ch](https://finyo.frama-maschinenhandel.ch)
(access by invitation — new accounts need a role assignment). Releases are tagged
and auto-deployed, see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Quick Start

**Prerequisites:** Docker (the full stack runs in Docker Compose; Java 25 only for local API development)

```bash
# 1. Start the full stack (Postgres, Keycloak, API, frontend)
docker compose up -d

# 2. Verify
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3001 |
| API | http://localhost:8082 (Swagger UI: http://localhost:8082/swagger-ui.html) |
| Keycloak | http://localhost:8081 (realm `finyo`) |
| PostgreSQL | localhost:5433 |

To run the API outside Docker: `cd finyo-be && ./mvnw spring-boot:run`.
Tests: `cd finyo-be && ./mvnw test` — see [docs/TESTING.md](docs/TESTING.md).

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Backend language |
| Spring Boot | 4.1 | Application framework |
| PostgreSQL | 17 | Primary database (Flyway migrations) |
| Keycloak | 26 | Authentication (OIDC, Google login) |
| React | 19 | Frontend (TypeScript, Vite) |
| Tailwind CSS | 4 | Styling (with Shadcn/UI) |
| Caddy | 2 | Reverse proxy + TLS (production) |
| Testcontainers / Vitest | — | Backend / frontend testing |

## Documentation

| Document | Description |
|---|---|
| [Docs site](https://franjofranjic27.github.io/finyo/) | Rendered documentation (GitHub Pages) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, module structure, tech stack, DB schema |
| [docs/COMMIT_CONVENTION.md](docs/COMMIT_CONVENTION.md) | Commit message format and rules |
| [docs/TESTING.md](docs/TESTING.md) | How to run and write tests |
| [docs/WORKFLOWS.md](docs/WORKFLOWS.md) | GitHub Actions CI/CD workflows |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production deployment (VPS, Caddy, Keycloak) |
| [docs/PILLAR3_PRODUCTS.md](docs/PILLAR3_PRODUCTS.md) | Pillar 3a product catalog administration and bulk import |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute to this project |

Repo-wide conventions (README/badge standard, PR and issue templates) live in
[franjofranjic27/.github](https://github.com/franjofranjic27/.github).

## License

[MIT](LICENSE)
