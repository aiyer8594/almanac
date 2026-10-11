# Almanac

A personal life-management app built as a small platform: a Java backend, a
TypeScript frontend, AI-assisted search over your own documents, and a Kubernetes
operator, deployed with GitOps.

> **Status:** early development. The Spring Boot backend is scaffolded; the other
> components below are planned. Sections marked *planned* describe intended design,
> not working code.

## Architecture

Target architecture (most components planned):

```mermaid
flowchart LR
    user([Browser]) --> fe[Frontend<br/>React + Vite]
    fe -->|REST / JSON| be[Backend<br/>Spring Boot 4, Java 25]
    be --> pg[(PostgreSQL<br/>+ pgvector)]
    be -->|embeddings, chat| ollama[Ollama<br/>local model]

    subgraph cluster [Kubernetes]
        argocd[ArgoCD] -->|syncs| k8s[Kustomize manifests]
        op[Almanac operator<br/>Go] -->|reconciles| k8s
    end
```

| Component | Stack | Status |
| - | - | - |
| Backend API | Java 25, Spring Boot 4 (Spring MVC), Spring Data JPA, Flyway, Mustache | Scaffolded |
| Database | PostgreSQL 17 with pgvector | Used in tests via Testcontainers |
| Document search | Spring AI, Ollama, RAG over user documents | Planned |
| Frontend | TypeScript (strict), React, Vite | Planned |
| Operator | Go, kubebuilder / controller-runtime | Planned |
| Deployment | Docker, Kustomize, ArgoCD on a local kind cluster | Planned |
| Load tests | k6 | Planned |

## Repository layout

```
backend/          Spring Boot API
deploy/compose/   docker-compose for local dependencies (Postgres)
docs/adr/         Architecture decision records (planned)
```

Planned: `frontend/`, `operator/`, `deploy/k8s/` (Kustomize), `deploy/argocd/`,
`load-tests/`.

## Running locally

**Prerequisites:** JDK 25 and a running Docker daemon. The Gradle wrapper downloads
Gradle itself.

Start the backend with a throwaway Postgres (pgvector) container managed by
Testcontainers:

```bash
cd backend
./gradlew bootTestRun
```

The API listens on `http://localhost:8080`. Spring Boot Actuator's health endpoint
is at `/actuator/health`.

Run the tests (also requires Docker):

```bash
cd backend
./gradlew test
```

### Local Postgres (Docker Compose)

A long-lived Postgres (pgvector) container for local development, using the same
image as the tests. It binds to `127.0.0.1` only. No secrets are required for local
development; the values in `.env.example` are local defaults.

Run these from the repository root:

```bash
cp .env.example .env    # optional: every variable has a default in the compose file
docker compose --env-file .env -f deploy/compose/docker-compose.yml up -d
docker compose -f deploy/compose/docker-compose.yml ps    # wait for "(healthy)"
```

Open a `psql` shell:

```bash
docker compose -f deploy/compose/docker-compose.yml exec postgres psql -U almanac -d almanac
```

Stop it (`down -v` also deletes the data volume):

```bash
docker compose -f deploy/compose/docker-compose.yml down
```

`POSTGRES_*` values apply only when the data volume is first initialized. After
changing them in `.env`, recreate the volume with `down -v`.

## Design decisions

Recorded as ADRs in `docs/adr/` as they are made. Decisions that shape the current
code:

- **Spring MVC, not WebFlux.** The blocking servlet model keeps code and stack traces
  simple. With virtual threads (planned), blocking I/O no longer ties up a platform
  thread per request, which removes the main scaling argument for going reactive.
- **Postgres + pgvector for both relational data and embeddings.** One database to
  run, back up, and query transactionally, instead of a separate vector store.
- **Real Postgres in tests.** Integration tests run against a pgvector container via
  Testcontainers rather than an in-memory substitute, so migrations and queries are
  tested against the same engine used in production.
- **Schema managed by Flyway.** Versioned SQL migrations, applied at startup.

## License

[MIT](LICENSE)
