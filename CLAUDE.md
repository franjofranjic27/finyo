# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**finyo** is a personal finance planner. The planned stack is a **Spring Boot** backend (Java, Maven) with a **PostgreSQL** database.

## CI/CD

The workflows in `.github/workflows/` were copied from another project and still need to be adjusted for this repository:

- **ci.yml** — CI on PRs (typecheck, lint, test)
- **sonar.yml** — SonarQube analysis on push to `main` and PRs
- **claude.yml** — Claude Code responds to `@claude` mentions in issues/PRs (requires `CLAUDE_CODE_OAUTH_TOKEN` secret)
- **claude-code-review.yml** — automated Claude code review on all PRs (requires `CLAUDE_CODE_OAUTH_TOKEN` secret)

All workflows are currently commented out.

## Build & Run

### Prerequisites
- Java 25
- Maven (or use the Maven wrapper)
- Docker (for PostgreSQL via Docker Compose)

### Start infrastructure
```bash
docker compose up -d
```

### Run the API
```bash
cd finyo-api
./mvnw spring-boot:run
```

### Verify
```bash
curl http://localhost:8080/hello-world
# Expected: Hello, I'm finyo!
```

### Run tests
```bash
cd finyo-api
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
- `api`    — Spring Boot application (`finyo-api/`)
- `infra`  — Docker, docker-compose, deployment configs
- `config` — Environment, build, or project-level config
- `ci`     — GitHub Actions workflows, dependabot
- Omit scope for cross-cutting changes

**Rules:**
- Subject line: imperative mood, lowercase, no period, max 72 chars
- Body: wrap at 72 chars, explain *why* not *what* (the diff shows *what*)
- One logical change per commit — don't bundle unrelated changes
- Reference issues with `Closes #N` or `Refs #N` in the body when applicable

**Examples:**
```
feat(api): add transaction listing endpoint
```
```
fix(api): handle empty response in budget calculator
```
```
chore(infra): add postgres service to docker-compose stack
```

## Setup

After cloning, install the git hooks:
```bash
git config core.hooksPath .githooks
```