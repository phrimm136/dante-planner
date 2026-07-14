# terraform/oregon — Phase 13 Oregon k3s fleet

Provisions the **Oregon (`us-west-2`) primary-region k3s fleet** for the multi-region
architecture (task 034, Phase 13): a dedicated VPC, one k3s server (CP), one Traefik ingress
node, an app ASG, one data node, the backend ECR repo, the etcd-snapshot bucket, and the
CloudWatch billing/auto-recovery alarms. The cluster is GitOps-managed by ArgoCD core, which
the CP bootstraps from `deploy/`.

This stack is **Oregon-only**. Global Accelerator, VPC peering, ECR cross-region replication,
Seoul, and the RDS read replica are **Phase 14** and are not authored here.

## Fleet shape

| Node | Type | k3s role | Lifecycle | Workload (pinned in `deploy/`) |
|------|------|----------|-----------|-------------------------------|
| CP | `t4g.small` | server, embedded etcd, `NoSchedule` taint | pet (auto-recovery) | ArgoCD core |
| ingress | `t4g.small` | agent, `role=ingress` | pet (auto-recovery) | Traefik Gateway (GA endpoint in P14) |
| app | `t4g.small` ×(1→2) | agent, `role=app` | cattle (ASG + SSM join) | Spring DaemonSet |
| data | `t4g.small` | agent, `role=data` | pet (auto-recovery) | auth Redis, rate-limit Redis, Prometheus |

Instance redundancy is spent only on the user-facing app tier (the ASG); singleton infra
nodes fail over at region granularity in Phase 14 and recover in place via EC2 auto-recovery.

## Unattended bootstrap

Terraform generates a random join token → SSM SecureString. The CP reads it to `--cluster-init`
the server; every agent (ingress, data, app) reads it in user-data to join
`https://<cp-private-ip>:6443`. No operator secret handoff. `terraform apply` from an empty
account reproduces the region (Done-When: rebuild proves the bootstrap).

## Prereqs

- A dedicated least-privilege **provisioning identity** assumed via STS (as in `terraform/rds`).
- `cp terraform.tfvars.example terraform.tfvars` and set `ingress_allowed_cidrs`
  (Cloudflare + GA-health ranges). `terraform.tfvars` is gitignored.
- The JWT material must already exist in **AWS Secrets Manager** (all three required; the
  backend fails fast at boot without them): the private key at `rs256_private_key_secret_name`
  (default `danteplanner/jwt/rs256-private-key`), the X.509 public key at
  `danteplanner/jwt/rs256-public-key`, and the base64 AES-256 encryption key at
  `danteplanner/jwt/encryption-key`. Terraform never holds this material — only grants the app
  node role read access.

## Usage

```bash
export AWS_PROFILE=<your-provisioning-profile>
terraform init
terraform validate
terraform plan        # READ IT
terraform apply
terraform output backend_ecr_repository_url
```

## Cross-stack wiring the operator must do at apply time

- **RDS reachability:** this stack does **not** edit the RDS security group (owned by
  `terraform/rds`). Take the `cluster_security_group_id` output and add it to the RDS SG
  allowlist on 3306 (via `terraform/rds` inputs), so app/data nodes can reach the primary.
- **ECR repo collision:** if `danteplanner-backend` already exists from the task-014 single-region
  deploy, `terraform import aws_ecr_repository.backend danteplanner-backend` before apply.
- **Alarm notifications:** set `alarm_sns_topic_arn` to receive billing/auto-recovery alerts;
  empty leaves the alarms visible but silent.

## Deliberate deviations (portfolio deviation table)

- **No IRSA.** Without EKS there is no IRSA; the node **instance profile** is the AWS-auth
  boundary. The External Secrets Operator controller (pinned to `role=app` nodes) reads the
  RS256 key from Secrets Manager via the app node role — coarser than IRSA, since any pod on an
  app node inherits it. Documented, accepted.
- **Public subnets, no NAT gateway.** A NAT gateway is ~$32/mo/AZ against a ~$145-190/mo budget.
  Nodes sit in public subnets; the cluster SG is the boundary and Cloudflare mTLS is the real
  origin gate.
- **No autonomous writer promotion.** The Redis StatefulSet has no Sentinel/auto-promote; a
  Redis outage is a typed 503 + auto-recovery + AOF replay. EC2 auto-recovery restarts the same
  instance (same identity) — it is not a promotion.

## Not managed here

- **The RS256 private key** (Secrets Manager, operator-populated) — referenced by name only.
- **The RDS instance and its SG** (`terraform/rds`) — referenced via the documented output wiring.
- **State backend:** configure encrypted remote state in your private setup; local state and
  `terraform.tfvars` are gitignored. Never commit `*.tfstate`.
