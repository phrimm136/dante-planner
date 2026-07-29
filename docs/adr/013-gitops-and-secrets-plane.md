# 013 gitops-and-secrets-plane
epic: none · pr: none

## Decisions
- @gitops @argocd — ArgoCD core runs per cluster on that cluster's control-plane node, syncing one repository through a kustomize base plus per-region overlays. REJECTED: a hub-spoke controller driving both clusters — it makes deploying to either region depend on the other being alive, which is the coupling the second region exists to remove.
- @ecr @replication — ECR cross-region replication mirrors the backend image so each region pulls from a registry in its own region. REJECTED: pulling from the primary region's registry — a region's self-healing must not depend on another region, and image pull sits on the critical path of every recovery.
- @secrets @eso @iam — Secrets reach pods through External Secrets Operator reading AWS Secrets Manager, with the RS256 signing key on multi-region secret replication. Without EKS there is no IRSA, so the EC2 instance profile is the authorization boundary. This is a recorded deviation, not an oversight: the boundary is per-node rather than per-workload, and that coarseness is the accepted cost of self-managed k3s.
- @schema @compatibility — N/N−1 compatibility and expand-contract Flyway migrations are mandatory before a second region serves traffic. Two regions deploying independently against one shared database means minutes of version skew on every release, so "can the previous version run against this change" becomes a release-checklist question rather than a nicety.
- @bootstrap @automation — Region bootstrap is unattended: Terraform, instance user-data, and an SSM-delivered k3s join token. Provisioning the second region through that automation is the test of it; a bootstrap path only ever exercised by hand is a claim.
- @boundaries @terraform @config — Terraform owns the AWS platform plane (VPCs, autoscaling groups, RDS, peering, DNS, secrets and IAM); application configuration rides the kustomize overlay ConfigMap. REJECTED: managing application config from Terraform — the two planes change at different rates and carry different blast radii, and merging them means a feature-flag flip requires an infrastructure apply.

## Takeaway
- takeaway: every cross-region dependency introduced for convenience is a dependency the second region cannot use during the failure it was bought for. The test is whether a region can rebuild itself with the other one switched off.
