# 2. Operator-managed preview environments

Date: 2026-10-19

## Status

Accepted

## Context

Reviewing a backend change against a real database, migrations included, needs a
running copy of the stack. A shared staging environment serializes that work: one
change at a time, and a broken migration blocks everyone. Per-change environments fix
this, but only if they are cheap to create and cannot be forgotten. Environments that
outlive their purpose waste cluster capacity and leave stale databases behind.

ArgoCD's ApplicationSet pull-request generator already creates one Application per open
pull request. It covers the basic case, with three gaps:

- An environment lives exactly as long as its pull request is open. There is no time
  limit, and no way to create one for an arbitrary commit or image outside a pull
  request.
- There is no per-environment status beyond the Application itself. Nothing reports
  when an environment will expire or where it is reachable.
- Lifecycle rules (expiry, and later quotas or required image signatures) would have
  to live outside ArgoCD.

## Decision

Add a Go operator (kubebuilder, controller-runtime) that manages a cluster-scoped
`PreviewEnvironment` custom resource with `spec.image` and `spec.ttl`. CI creates one
per pull request after the image has been scanned and signed.

The reconciler, idempotent on every pass:

1. Ensures a Namespace `preview-<name>`, with an owner reference to the resource.
2. Ensures an ArgoCD `Application` that syncs `deploy/k8s/overlays/preview` into that
   namespace with the image overridden, also owned by the resource.
3. Copies the Application's sync and health state into status conditions, and sets
   `status.expiresAt` and the environment's URL.
4. Requeues at `status.expiresAt`. Once expired, it deletes the resource, and garbage
   collection removes the Namespace and Application, and with them the backend and
   its Postgres StatefulSet.

The operator manages lifecycle; ArgoCD delivers manifests. The operator never renders
Deployments itself, so the Kustomize overlay remains the single source of truth.

## Consequences

- The resource is cluster-scoped because a namespaced object cannot own a
  cluster-scoped Namespace. Cleanup then relies on garbage collection alone.
- No finalizer is needed while everything the operator creates is in-cluster and
  owned. A finalizer becomes necessary if a preview environment ever provisions
  something outside the cluster, such as a managed database (Cloud SQL, RDS).
- Each environment gets its own Postgres, so migrations run against an empty database
  every time. Seeding test data is a separate, later decision.
- The operator depends on the ArgoCD `Application` API. It reads and writes that type
  without importing ArgoCD as a library, which keeps the dependency to the CRD schema.
- Capacity is the main risk. Many open pull requests each run a JVM and a Postgres. A
  cap on concurrent environments, or a ResourceQuota per preview namespace, may be
  needed once usage is known.
