# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**finyo** is a personal finance planner. The planned stack is a **Spring Boot** backend (Java, Maven) with a **PostgreSQL** database.

## Documentation

| File | Content |
|---|---|
| docs/ARCHITECTURE.md | Module structure, tech stack, DB schema, security, API docs |
| docs/COMMIT_CONVENTION.md | Full commit format spec with examples |
| docs/TESTING.md | Test types, how to run, how to write tests |
| docs/WORKFLOWS.md | GitHub Actions workflows and local equivalents |
| docs/DEPLOYMENT.md | Production deployment (compose.prod.yml, Caddy, Keycloak persistence) |
| CONTRIBUTING.md | Contributor setup and workflow |

## CI/CD

Workflows in `.github/workflows/`:

- **ci.yml** — active; backend (`./mvnw verify`) + frontend (lint, vitest, build) on PRs and pushes to `main`
- **sonar.yml** — active; SonarCloud analysis for backend (`franjofranjic27_finyo`, JaCoCo) and frontend (`franjofranjic27_finyo-fe`, LCOV) on push to `main`, PRs and manually (`workflow_dispatch`); requires `SONAR_TOKEN`
- **release.yml** — active; on tag push builds Docker images, publishes to Docker Hub, deploys to the VPS via SSH (`deploy/deploy.sh`) and creates a GitHub release. Tags: `v1.2.3` (both), `be-v1.2.3` (backend only), `fe-v1.2.3` (frontend only); requires `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`

## Build & Run

### Prerequisites
- Java 25
- Maven (or use the Maven wrapper)
- Docker (for PostgreSQL via Docker Compose)

### Install git hooks
```bash
git config core.hooksPath .githooks
```

### Start the full stack
```bash
docker compose up -d
```

Host ports (5432/8080/3000 are occupied by another local project):
- Frontend: http://localhost:3001
- API: http://localhost:8082 (Swagger UI: http://localhost:8082/swagger-ui.html)
- Keycloak: http://localhost:8081 (realm `finyo`)
- Postgres: localhost:5433

### Run the API locally (outside Docker)
```bash
cd finyo-be
./mvnw spring-boot:run
```

### Verify
```bash
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}
```

### Run tests
```bash
cd finyo-be
./mvnw test
```

## Commit Convention

All commits in this repository MUST follow this format:

```
<type>(<scope>): <short summary>

<optional body — what and why, not how>
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `ci`, `build`

**Scopes** (use the most specific one that applies):
- `api`    — Spring Boot application (`finyo-be/`)
- `infra`  — Docker, docker-compose, deployment configs
- `config` — Environment, build, or project-level config
- `ci`     — GitHub Actions workflows, dependabot
- Omit scope for cross-cutting changes

**Rules:**
- Subject line: imperative mood, lowercase, no period, max 72 chars
- Body: wrap at 72 chars, explain *why* not *what* (the diff shows *what*)
- One logical change per commit — don't bundle unrelated changes
- Reference issues with `Closes #N` or `Refs #N` in the body when applicable

See `docs/COMMIT_CONVENTION.md` for the full spec with examples.
