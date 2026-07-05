# Contributing to finyo

Thanks for your interest in contributing! This document covers the essentials for getting
set up and submitting changes.

## Setup

```bash
# 1. Clone the repository
git clone https://github.com/franjofranjic27/finyo.git
cd finyo

# 2. Install git hooks (enforces commit message format)
git config core.hooksPath .githooks

# 3. Start infrastructure
docker compose up -d

# 4. Verify your setup
cd finyo-be
./mvnw verify
```

## Development Workflow

**Branch naming**

Use a prefix that matches the type of change:

| Prefix | When to use |
|---|---|
| `feat/` | New feature or capability |
| `fix/` | Bug fix |
| `chore/` | Maintenance, deps, tooling |
| `docs/` | Documentation only |
| `refactor/` | Code restructure without behaviour change |
| `test/` | Test additions or corrections |

Examples: `feat/transaction-listing`, `fix/budget-calculator-empty-response`

**Making changes**

1. Create a branch from `main`
2. Make your changes in `finyo-be/`
3. Run `./mvnw verify` from `finyo-be/` — this runs unit and integration tests
4. Push and open a pull request against `main`

## Commits

All commits must follow the project's [Commit Convention](docs/COMMIT_CONVENTION.md).
The format is enforced automatically by the `.githooks/commit-msg` hook.

## Testing

See [docs/TESTING.md](docs/TESTING.md) for test types, how to run them, and how to write new tests.

## Pull Requests

- Fill out the PR template when opening a pull request — it outlines what reviewers will check.
- CI runs automatically on every PR (`./mvnw verify` including integration tests).
- See [docs/WORKFLOWS.md](docs/WORKFLOWS.md) for details on what CI does and how to reproduce it locally.
