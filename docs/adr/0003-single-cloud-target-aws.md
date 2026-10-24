# 3. Single cloud target: AWS EKS

Date: 2026-10-23

## Status

Accepted. Supersedes the earlier plan for Terraform with Terragrunt targeting both GKE
and EKS.

## Context

The infrastructure layer was planned as Terraform modules for two targets, GKE and EKS,
with Terragrunt keeping backend state, provider settings, and per-cloud inputs DRY
across them. Terragrunt was justified by the second target: with two clouds, plain
Terraform means near-duplicate root modules.

Nothing above the cluster depends on the cloud. The backend, operator, Kustomize
manifests, and ArgoCD run the same way on any conformant Kubernetes cluster, and local
development uses kind. A second cloud therefore adds a second set of network, cluster,
IAM, and registry modules without exercising any new part of the architecture, and it
doubles the infrastructure code to review and keep current.

## Decision

Target one cloud, AWS, and build that target fully:

- Terraform modules for the VPC, an EKS cluster with a managed node group, ECR for
  images, and IAM roles bound to Kubernetes service accounts so workloads get AWS
  credentials without static keys.
- Plain Terraform with a root configuration per environment under `infra/live/`,
  using an S3 backend with S3-native state locking.
- No Terragrunt. With a single target, the duplication it removes is small enough
  that plain Terraform is simpler to read and run.

`terraform plan` against the target is the bar for this layer; clusters are not kept
running.

## Consequences

- One set of modules, built and validated in depth, instead of two at half depth.
- Workloads stay cloud-agnostic. Adding GKE later means a second set of modules and a
  second root, not application changes. If that happens, revisit Terragrunt.
- Managed services referenced elsewhere (for example a managed Postgres for a future
  production setup) are AWS services: RDS rather than Cloud SQL.
