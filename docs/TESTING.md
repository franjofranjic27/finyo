# Testing

## Test Types

### Unit Tests (`*Test.java`)

- Framework: JUnit 5
- Runner: Maven Surefire plugin
- No external dependencies — no database, no containers required
- Package mirrors `src/main/java`

### Integration Tests (`*IT.java`)

- Framework: JUnit 5
- Runner: Maven Failsafe plugin
- Use TestContainers to spin up a real PostgreSQL 17 container automatically
- No manual database setup required — the container starts and stops per test class
- Annotated with `@SpringBootTest` to load the full application context

## Running Tests

```bash
# All tests (unit + integration) — recommended before pushing
./mvnw verify

# Unit tests only
./mvnw test

# Integration tests only
./mvnw failsafe:integration-test
```

All commands must be run from the `finyo-api/` directory (or use `-f finyo-api/pom.xml` from the root).

## TestContainers Setup

Integration tests do not require a locally running PostgreSQL instance. TestContainers
pulls and manages the `postgres:17` Docker image automatically.

The test profile is activated via `finyo-api/src/test/resources/application-test.yaml`.
It configures the datasource to connect to the TestContainers-managed instance.

**Requirement:** Docker must be running when executing integration tests.

## Code Coverage

JaCoCo generates HTML and XML coverage reports after `./mvnw verify`:

| Report | Location |
|---|---|
| Unit test coverage | `target/site/jacoco/` |
| Integration test coverage | `target/site/jacoco-it/` |

The XML reports are picked up by SonarCloud in CI for quality gate analysis.
See [WORKFLOWS.md](WORKFLOWS.md) for CI details.

## Writing Tests

**Naming conventions**

| Type | Suffix | Example |
|---|---|---|
| Unit test | `*Test` | `HelloWorldServiceTest` |
| Integration test | `*IT` | `FinyoApiApplicationIT` |

**Package structure**

Test classes mirror the main source tree:

```
src/main/java/ch/finyoapi/helloworld/HelloWorldService.java
src/test/java/ch/finyoapi/helloworld/HelloWorldServiceTest.java
```

**Integration tests**

Integration tests should be annotated with `@SpringBootTest` and use the `test` Spring profile.
TestContainers wiring is handled by the test application config — no additional setup is needed
in individual test classes beyond using the standard JUnit 5 `@Test` annotation.
