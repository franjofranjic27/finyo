# Commit Convention

All commits in this repository must follow this format. The format is automatically enforced
by the `.githooks/commit-msg` hook.

## Format

```
<type>(<scope>): <short summary>

<optional body>
```

## Types

| Type | Description |
|---|---|
| `feat` | A new feature or user-facing capability |
| `fix` | A bug fix |
| `refactor` | Code restructure that neither fixes a bug nor adds a feature |
| `docs` | Documentation changes only |
| `chore` | Maintenance tasks: dependencies, tooling, housekeeping |
| `test` | Adding or correcting tests, no production code change |
| `ci` | Changes to GitHub Actions workflows or CI configuration |
| `build` | Changes to the build system (Maven config, Docker, etc.) |

## Scopes

Use the most specific scope that applies. Omit the scope for cross-cutting changes.

| Scope | Path | When to use |
|---|---|---|
| `api` | `finyo-api/` | Spring Boot application code |
| `infra` | `compose.yml`, Dockerfile, deployment configs | Docker, docker-compose, infrastructure |
| `config` | Root-level config files | Environment, build, or project-level config |
| `ci` | `.github/workflows/` | GitHub Actions workflows, Dependabot |

## Rules

- **Subject line:** imperative mood, lowercase, no trailing period, max 72 characters
- **Body:** optional; wrap at 72 characters; explain *why* not *what* (the diff shows *what*)
- **One logical change per commit** — don't bundle unrelated changes
- **Issue references:** use `Closes #N` or `Refs #N` in the body when applicable

## Examples

**Good**

```
feat(api): add transaction listing endpoint
```

```
fix(api): handle empty response in budget calculator

The service returned null when no transactions existed in the given period,
causing a NullPointerException in the controller. Return an empty list instead.

Closes #42
```

```
chore(infra): add postgres service to docker-compose stack
```

```
refactor(api): extract budget calculation into dedicated service
```

```
ci: add sonarcloud analysis workflow
```

```
docs: add architecture and testing documentation
```

**Bad**

```
# Missing type
updated the budget calculator
```

```
# Period at end, past tense, too vague
Fixed stuff.
```

```
# Multiple unrelated changes in one commit
feat(api): add transaction endpoint and fix login bug and update deps
```

## Enforcement

The `.githooks/commit-msg` hook validates every commit message against the expected pattern.

To activate the hook after cloning:

```bash
git config core.hooksPath .githooks
```

If a commit message does not match the pattern, the commit is rejected with an error message
showing the expected format and examples.
