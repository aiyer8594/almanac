# 4. CI/CD on GitHub Actions, delivery through ArgoCD

Date: 2026-10-29

## Status

Accepted. Refines the CI step in ADR 0002.

## Context

The repository holds four deliverables with different toolchains: the Java backend,
the Go operator, Kustomize manifests, and Terraform. Each needs its own checks, and
the two that ship as images (backend and operator) need the same build, scan, SBOM,
and signing steps. Changes should be verified as close to production as possible
before merge, and what reaches a cluster should be a reviewed change in Git.

Two constraints shape the design:

- The repository is public. Workflows triggered by pull requests from forks must not
  get secrets or write access, and self-hosted runners are unsafe because any fork
  could run code on them.
- The target cluster for cluster mode is a local kind cluster. GitHub-hosted runners
  cannot reach it, so CI cannot push to it, and should not hold credentials for it.

## Decision

GitHub Actions does all of CI and the promotion half of CD. ArgoCD does delivery: it
pulls from Git, and nothing in CI holds cluster credentials.

- **Per-component workflows**, path-filtered so a change only runs the checks it
  affects: backend (Gradle build with Testcontainers), operator (gofmt, go vet,
  golangci-lint, envtest, and a check that generated code is up to date), manifests
  (`kustomize build` of every overlay, validated with kubeconform), and infra
  (`terraform fmt` and `validate`, tflint, Trivy config scan).
- **One reusable image workflow** called by the backend and operator workflows. It
  builds with Buildx and a layer cache, scans with Trivy (a HIGH or CRITICAL finding
  with an available fix fails the job), and generates an SBOM with Syft. On `main` it
  also pushes to GitHub Container Registry, signs the image with cosign keyless
  signing (GitHub OIDC identity, no signing keys to store), and attaches build
  provenance.
- **Preview environment test on every pull request.** The job creates a kind cluster
  inside the runner, installs ArgoCD and the operator, loads the freshly built image,
  applies a `PreviewEnvironment`, waits for its Ready condition, and runs a smoke test
  against the backend. Logs and resource state are uploaded on failure. The cluster
  is discarded with the runner.
- **Promotion by pull request.** After an image is published from `main`, a workflow
  opens a pull request that pins the new image digest in the cluster overlay. Merging
  it is the deployment; ArgoCD syncs it.
- **Releases from tags.** A `v*` tag publishes a semver-tagged image and a GitHub
  Release with the SBOM attached.
- **Workflow hardening.** Default `GITHUB_TOKEN` permissions are read-only, and each
  job grants only what it needs. Third-party actions are pinned to commit SHAs and
  kept current by Dependabot. Pull request workflows use `pull_request`, never
  `pull_request_target`. Long-lived cloud and cluster credentials are not stored as
  secrets; AWS access, when added for `terraform plan`, uses GitHub OIDC federation
  to an IAM role.

## Consequences

- A pull request proves the operator, ArgoCD, and the backend work together on a
  real cluster before merge, at no infrastructure cost.
- Preview environments in CI live only as long as the job, so the TTL is not
  exercised there. TTL expiry is covered by the operator's envtest suite and by
  long-lived previews created by hand on the local kind cluster.
- Pull request images are scanned but not signed or published: fork pull requests
  cannot push, and the image never leaves the runner. Only images built from `main`
  or a tag are signed.
- Images are published to GitHub Container Registry, which needs no cloud account.
  ECR remains the registry for the AWS target defined in Terraform (ADR 0003).
- Signatures are produced but not yet enforced at admission. Verifying them in the
  cluster (for example with an admission policy) is a later decision.
- The runner-local cluster adds several minutes to each pull request. Path filters
  keep it off documentation-only changes.
