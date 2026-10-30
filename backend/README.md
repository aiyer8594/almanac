# Backend

The Almanac API: a Spring Boot 4 application on Java 25, using Spring MVC with
virtual threads, Spring Data JPA, and Flyway on PostgreSQL with pgvector.

**Status:** the `tasks` domain model and schema are in place (entity, status
transitions, migrations, unit tests). The repository, service, and REST endpoints
are in progress.

## Prerequisites

- JDK 25. The Gradle wrapper downloads Gradle itself.
- A running Docker daemon, for the tests and for `bootTestRun`.

## Running

Against a throwaway Postgres container started by Testcontainers (no setup needed):

```bash
./gradlew bootTestRun
```

Against the long-lived Postgres from `deploy/compose` (see the root README to start
it):

```bash
./gradlew bootRun
```

The API listens on `http://localhost:8080`. Health is at `/actuator/health`.

## Testing

```bash
./gradlew test     # unit tests, @WebMvcTest slices, and Testcontainers integration tests
./gradlew build    # compile and test
```

Tests that touch the database run against the same `pgvector/pgvector:pg17` image
used in local development, never an in-memory substitute.

## Configuration

The datasource is configured from environment variables, with local-development
defaults that match `deploy/compose`:

| Variable | Default |
| - | - |
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `almanac` |
| `POSTGRES_USER` | `almanac` |
| `POSTGRES_PASSWORD` | `almanac` |

The defaults are for local development only. `bootTestRun` and the tests ignore them
and connect to the Testcontainers database instead.

## Schema

Flyway owns the schema. Migrations live in `src/main/resources/db/migration` and run
at startup. Hibernate runs with `ddl-auto: validate`, so the application refuses to
start if an entity and the schema disagree. Applied migrations are never edited; a
change is a new version.

| Migration | Change |
| - | - |
| `V1__create_tasks.sql` | `tasks` table with a check constraint on `status` |
| `V2__add_tasks_version.sql` | `version` column for optimistic locking |

## Code layout

Packaged by feature under `io.github.aiyer8594.almanac`:

| Package | Contents |
| - | - |
| `tasks` | `Task` entity, `TaskStatus` and its transition rules, `InvalidStatusTransitionException` |
| `hello` | Smoke-test endpoint (`GET /hello`) |

A task moves `TODO` → `IN_PROGRESS` or `DONE`, `IN_PROGRESS` → `DONE`, and
`DONE` → `TODO`. Any other transition, including one to the current status, throws
`InvalidStatusTransitionException`. `Task` carries a `@Version` field, so concurrent
updates to the same task fail instead of overwriting each other.

Design decisions that affect the backend are recorded in [`docs/adr`](../docs/adr).
