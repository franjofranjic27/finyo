# finyo

A personal finance planner.

[![CI status](https://img.shields.io/github/actions/workflow/status/franjofranjic27/finyo/sonar.yml?branch=main&style=for-the-badge)](https://github.com/frnajofranjic27/finyo/actions/workflows/sonar.yml?branch=main)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

## What is finyo?

finyo is a personal finance planner designed to help individuals track income, expenses, and budgets.
It exposes a REST API backed by a PostgreSQL database, with a frontend planned for a future phase.
The project is in early development — the API foundation is in place and growing.

## Quick Start

**Prerequisites:** Java 25, Docker (Maven wrapper is included)

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start the API
cd finyo-api
./mvnw spring-boot:run

# 3. Verify
curl http://localhost:8080/hello-world
# Expected: Hello, I'm finyo!
```

API docs are available at `http://localhost:8080/swagger-ui.html` once the app is running.

## Documentation

| Document | Description |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, module structure, tech stack, DB schema |
| [docs/COMMIT_CONVENTION.md](docs/COMMIT_CONVENTION.md) | Commit message format and rules |
| [docs/TESTING.md](docs/TESTING.md) | How to run and write tests |
| [docs/WORKFLOWS.md](docs/WORKFLOWS.md) | GitHub Actions CI/CD workflows |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute to this project |

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Language |
| Spring Boot | 4.0.3 | Application framework |
| PostgreSQL | 17 | Primary database |
| Flyway | (managed by Spring Boot) | Database migrations |
| Lombok | (managed by Spring Boot) | Boilerplate reduction |
| SpringDoc / OpenAPI | 3.0.1 | API documentation |
| TestContainers | 1.20.5 | Integration test infrastructure |
| JaCoCo | 0.8.14 | Code coverage |
