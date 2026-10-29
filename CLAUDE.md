# CLAUDE.md

Project instructions for Claude Code. Keep this file accurate as the repo evolves.
Update the TODOs as decisions are made.

## What this repo is

Almanac is a self-hosted task manager with natural-language search over your own
documents, answered by a locally run LLM so the documents never leave your
infrastructure. It is built as a small platform: a Java backend, a Go Kubernetes
operator that creates per-pull-request preview environments, GitOps delivery through
ArgoCD, a scanned and signed supply chain, and Terraform for AWS EKS.

## Tech stack

| Area | Choice |
| - | - |
| Backend | Java 25 (LTS), Spring Boot 4.x, Gradle wrapper (`./gradlew`). Pin exact plugin versions in `build.gradle`. |
| Database | PostgreSQL with pgvector (relational data and embeddings) |
| AI (planned) | Spring AI, Ollama running a small open-weight model locally, RAG over user documents |
| Operator (planned) | Go, kubebuilder / controller-runtime. Manages `PreviewEnvironment` resources (see below). |
| Deploy (planned) | Docker images, Kustomize manifests, ArgoCD |
| Auth (planned) | Spring Security OAuth2 resource server, JWT validation. TODO: choose identity provider. |
| Observability (planned) | Micrometer + OpenTelemetry, Prometheus, Grafana |
| CI/CD (planned) | GitHub Actions for CI and promotion; ArgoCD for delivery. Trivy, Syft SBOM, cosign keyless signing, GHCR. See ADR 0004. |
| Infrastructure (planned) | Terraform modules for an AWS EKS target, one root per environment. See ADR 0003. |

Rows marked "planned" are not built yet. Don't describe them as existing in docs.

## Repo layout

```
backend/          Java Spring Boot API (core of the project)
operator/         Go Kubernetes operator (kubebuilder project)
deploy/
  compose/        docker-compose for local dependencies (Postgres, Ollama)
  k8s/            Kustomize base + overlays
  argocd/         ArgoCD Application manifests
infra/            Terraform modules + per-environment roots for AWS (planned)
docs/
  adr/            Architecture decision records
.github/
  workflows/      CI/CD workflows (planned)
```

## Running locally

Local development does not need Kubernetes. Run the backend as a plain Java process
and the dependencies in Docker.

```bash
docker compose -f deploy/compose/docker-compose.yml up -d   # Postgres, Ollama
cd backend && ./gradlew bootRun                             # API on :8080
```

Cluster mode (operator and ArgoCD) runs on a local kind cluster.
TODO: add a `make cluster-up` target once it exists.

## Common commands

```bash
# Backend
cd backend && ./gradlew build            # compile + unit + integration tests (Testcontainers)
cd backend && ./gradlew bootTestRun      # run the API against a Testcontainers Postgres

# Operator
cd operator && make test                 # envtest-based controller tests
cd operator && make manifests generate   # regenerate CRDs and deepcopy code
```

## Operator: preview environments (planned)

A cluster-scoped `PreviewEnvironment` resource (`spec.image`, `spec.ttl`) gives one
image its own short-lived copy of the stack. On every pull request, CI creates one on a
kind cluster inside the runner after the image is scanned (ADR 0004). Reconcile:

1. Ensure a Namespace `preview-<name>`, owned by the resource.
2. Ensure an ArgoCD `Application` for `deploy/k8s/overlays/preview` with the image and
   namespace overridden, owned by the resource. Manifests stay in Kustomize; the
   operator never renders workloads itself.
3. Mirror the Application's sync and health into status conditions.
4. Requeue at `status.expiresAt`; once expired, delete the resource. Garbage
   collection removes everything it owns.

The resource is cluster-scoped so it can own a Namespace. No finalizer is needed while
everything it creates is in-cluster and owned; add one only for external resources.
See ADR 0002.

## CI/CD (planned)

GitHub Actions runs CI and promotion; ArgoCD pulls from Git and deploys. CI never
holds cluster credentials. See ADR 0004.

| Workflow | Trigger | Does |
| - | - | - |
| `backend.yml` | PR, push to `main` (`backend/**`) | Gradle build + Testcontainers tests, then the image workflow |
| `operator.yml` | PR, push to `main` (`operator/**`) | gofmt, go vet, golangci-lint, envtest, generated-code drift check, then the image workflow |
| `image.yml` | `workflow_call` only | Buildx build, Trivy scan, Syft SBOM. On `main`: push to GHCR, cosign sign, provenance |
| `manifests.yml` | PR (`deploy/**`) | `kustomize build` every overlay, kubeconform |
| `preview.yml` | PR | kind in the runner, ArgoCD + operator, apply a `PreviewEnvironment`, wait Ready, smoke test |
| `promote.yml` | image published from `main` | Open a PR pinning the new digest in the cluster overlay |
| `release.yml` | tag `v*` | Semver image tag, GitHub Release with SBOM |
| `infra.yml` | PR (`infra/**`) | `terraform fmt`/`validate`, tflint, Trivy config scan |

Rules for every workflow:
- Top-level `permissions: contents: read`; jobs add only what they need
  (`packages: write`, `id-token: write`, `pull-requests: write`).
- Pin third-party actions to a full commit SHA with the version in a comment.
  Dependabot updates actions, Gradle, and Go modules.
- Use `pull_request`, never `pull_request_target`. No self-hosted runners (public repo).
- No long-lived cloud or cluster credentials in secrets. AWS uses GitHub OIDC.
- Set `concurrency` so a new push cancels the previous run on the same PR.

## Conventions

### Java
- Constructor injection only. No field injection, no Lombok.
- Records for DTOs and value objects. Sealed interfaces where a type hierarchy is closed.
- Package by feature (`io.github.aiyer8594.almanac.tasks`, `...documents`), not by layer.
- Don't expose JPA entities from controllers. Map them to records.
- Validate inputs with Bean Validation. Use problem-details (RFC 9457) for error responses.
- Tests: JUnit 5 and AssertJ. Use Testcontainers for anything touching Postgres. Don't mock the database.
- Database migrations with Flyway in `backend/src/main/resources/db/migration`.

### Go (operator)
- Follow kubebuilder conventions. Never hand-edit generated files (`zz_generated.*`, CRD YAML in `config/crd`).
- Reconcile must be idempotent. Return errors rather than logging and continuing.
- Wrap errors with context (`fmt.Errorf("...: %w", err)`).
- Run `gofmt` / `go vet` before committing.

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
