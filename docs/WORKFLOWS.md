# Workflows

GitHub Actions is used for CI/CD. The workflow files live in `.github/workflows/`.

## Workflows

### ci.yml — Continuous Integration

| Property | Value |
|---|---|
| Trigger | Pull requests (opened, synchronize, reopened) |
| Runner | `ubuntu-latest` |
| Java | 25 (Temurin) |
| Command | `./mvnw verify` |

Runs the full build including unit and integration tests. Integration tests use TestContainers,
which requires Docker — GitHub Actions runners include Docker out of the box.

Skipped for Dependabot PRs.

---

### sonar.yml — SonarCloud Analysis

| Property | Value |
|---|---|
| Trigger | Push to `main`, pull requests |
| Runner | `ubuntu-latest` |
| Java | 25 (Temurin) |
| Command | `mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar` |
| SonarCloud org | `franjofranjic27` |
| Project key | `franjofranjic27_finyo` |

Runs the full build and submits JaCoCo coverage reports to SonarCloud for quality gate analysis.

Skipped for Dependabot PRs.

---

## Required Secrets

| Secret | Used by | How to obtain |
|---|---|---|
| `SONAR_TOKEN` | `sonar.yml` | Generate at [sonarcloud.io](https://sonarcloud.io) → Account → Security |

Secrets are configured in the repository under **Settings → Secrets and variables → Actions**.

## Local Equivalents

Run the same checks locally before pushing to avoid waiting for CI:

```bash
# Equivalent to ci.yml
cd finyo-api
./mvnw verify

# Equivalent to sonar.yml (requires SONAR_TOKEN set in environment)
cd finyo-api
mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.projectKey=franjofranjic27_finyo
```
