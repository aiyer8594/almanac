# CLAUDE.md

Project instructions for Claude Code. Keep this file accurate as the repo evolves.
Update the TODOs as decisions are made.

## What this repo is

A personal life-management app (TODO: name and one-line description), built as a
small platform: a Java backend, a TypeScript frontend, an AI document-search feature,
and a Go Kubernetes operator. It deploys with GitOps through ArgoCD.

## Tech stack

| Area | Choice |
| - | - |
| Backend | Java 25 (LTS), Spring Boot 4.x, Gradle wrapper (`./gradlew`). Pin exact plugin versions in `build.gradle`. |
| Database | PostgreSQL with pgvector (relational data and embeddings) |
| AI | Spring AI, Ollama running a small open-weight model locally, RAG over user documents |
| Frontend | TypeScript (strict), React + Vite |
| Operator | Go, kubebuilder / controller-runtime |
| Load tests | k6 (JavaScript) |
| Deploy | Docker images, Kustomize manifests, ArgoCD |
| Auth (planned) | Spring Security OAuth2 resource server, JWT validation. TODO: choose identity provider. |
| Observability (planned) | Micrometer + OpenTelemetry, Prometheus, Grafana |
| CI (planned) | GitHub Actions: build, test, Trivy image scan, Syft SBOM, cosign signing |

Rows marked "planned" are not built yet. Don't describe them as existing in docs.

## Repo layout

```
backend/          Java Spring Boot API (core of the project)
frontend/         TypeScript web app
operator/         Go Kubernetes operator (kubebuilder project)
deploy/
  compose/        docker-compose for local dependencies (Postgres, Ollama)
  k8s/            Kustomize base + overlays
  argocd/         ArgoCD Application manifests
load-tests/       k6 scripts
docs/
  adr/            Architecture decision records
.github/
  workflows/      CI pipelines (planned)
```

## Running locally

Local development does not need Kubernetes. Run the backend as a plain Java process
and the dependencies in Docker.

```bash
docker compose -f deploy/compose/docker-compose.yml up -d   # Postgres, Ollama
cd backend && ./gradlew bootRun                             # API on :8080
cd frontend && npm install && npm run dev                   # UI on :5173
```

Cluster mode (operator and ArgoCD) runs on a local kind cluster.
TODO: add a `make cluster-up` target once it exists.

## Common commands

```bash
# Backend
cd backend && ./gradlew build            # compile + unit + integration tests (Testcontainers)
cd backend && ./gradlew bootTestRun      # run the API against a Testcontainers Postgres

# Frontend
cd frontend && npm run lint && npm test && npm run build

# Operator
cd operator && make test                 # envtest-based controller tests
cd operator && make manifests generate   # regenerate CRDs and deepcopy code

# Load tests
k6 run load-tests/<script>.js
```

## Conventions

### Java
- Constructor injection only. No field injection, no Lombok.
- Records for DTOs and value objects. Sealed interfaces where a type hierarchy is closed.
- Package by feature (`com.<app>.documents`, `com.<app>.tasks`), not by layer.
- Don't expose JPA entities from controllers. Map them to records.
- Validate inputs with Bean Validation. Use problem-details (RFC 9457) for error responses.
- Tests: JUnit 5 and AssertJ. Use Testcontainers for anything touching Postgres. Don't mock the database.
- Database migrations with Flyway in `backend/src/main/resources/db/migration`.

### Go (operator)
- Follow kubebuilder conventions. Never hand-edit generated files (`zz_generated.*`, CRD YAML in `config/crd`).
- Reconcile must be idempotent. Return errors rather than logging and continuing.
- Wrap errors with context (`fmt.Errorf("...: %w", err)`).
- Run `gofmt` / `go vet` before committing.

### TypeScript
- `strict: true`. No `any` without a comment explaining why.
- API types come from the backend's OpenAPI spec. TODO: decide on a generation tool.

### Kubernetes manifests
- Every workload sets readiness and liveness probes; add a startup probe for anything
  slow to boot (the JVM backend, Ollama). The backend's probes use Spring Boot
  Actuator's liveness/readiness health groups.
- Every container sets CPU and memory requests and a memory limit.
- Multi-replica Deployments get a PodDisruptionBudget. The backend gets an HPA
  (requires metrics-server on the kind cluster).
- In cluster mode, Postgres runs as a single-replica StatefulSet with a headless
  Service and `volumeClaimTemplates`. Local development still uses compose.

### Security
- Never log tokens or document contents.
- Images are scanned in CI; a HIGH or CRITICAL finding with an available fix fails
  the build.

### General
- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `test:`).
- Never commit secrets. Configuration comes from environment variables. `.env.example` documents them.
- Record any significant design decision as an ADR in `docs/adr/` (short: context, decision, consequences).
- Run the relevant tests before calling a change done.

## Documentation

- Each top-level directory has its own README explaining what it is and how to run it.
- The root README is for readers evaluating the project: architecture diagram, how to
  run it, and the design decisions worth noticing. Keep it current.
- Public Java and Go APIs get doc comments. Explain the "why," not the obvious "what."
