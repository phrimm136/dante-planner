# Terraform stacks

Seven root modules, one shared child module (`modules/fleet`). Each stack owns a blast radius: a
mistake in one cannot destroy another's resources, and each applies independently.

That isolation costs the thing this file exists to supply. **Terraform orders resources inside a
stack; it cannot order the stacks.** Only `terraform_remote_state` reads are visible to it, and
several real dependencies are not of that shape.

## What each stack owns

| Stack | Owns | Required inputs |
|---|---|---|
| `iam-bootstrap` | The Terraform state bucket, the GitHub OIDC provider, the provisioning role | `trusted_admin_principal_arn`, `aws_account_id` |
| `secrets` | Secrets Manager **containers** and their cross-region replicas — never the values | `aws_account_id` |
| `rds` | The MySQL primary, its parameter group, subnet group and security group | `vpc_id`, `db_subnet_ids`, `engine_version`, `aws_account_id` |
| `oregon` | The primary k3s fleet (`modules/fleet`), peered to the RDS VPC | `ingress_allowed_cidrs`, `rds_vpc_id`, `aws_account_id` |
| `seoul` | The secondary fleet, the cross-region RDS read replica, cross-region peering | `rds_vpc_id`, `ingress_allowed_cidrs`, `aws_account_id` |
| `cloudflare` | Tunnels, load balancer, pools, health monitor | `cloudflare_api_token`, `account_id`, `zone_id` |
| `global-accelerator` | The AWS Global Accelerator entry plane | `aws_account_id` |

## State

`iam-bootstrap` keeps **local** state, because it creates the bucket every other stack stores state
in. Storing its own state there would be circular.

Every other stack declares a partial `backend "s3"` and takes the bucket at init time:

```bash
terraform -chdir=terraform/iam-bootstrap output -raw tf_state_bucket   # -> terraform/backend.hcl
terraform -chdir=terraform/<stack> init -backend-config=../backend.hcl
```

`terraform/backend.hcl` is gitignored because the bucket name carries the account id; copy
`backend.hcl.example` and fill it in. Locking is `use_lockfile = true` (S3-native, no DynamoDB
table), and every stack sets `workspace_key_prefix = "env"` — including the `terraform_remote_state`
data sources, so a non-default workspace reads its own peers rather than the default workspace's.

## Apply order

Edges Terraform can see are `terraform_remote_state` reads. Edges it cannot see are marked.

```
iam-bootstrap
   │
   ├─► secrets ──────────────► rds          (invisible edge: rds reads a secret secrets enrolls)
   ├─► oregon
   └─► cloudflare
              │
            seoul                            (reads oregon + rds state)
              │
      global-accelerator                     (reads oregon state)
```

`secrets`, `oregon` and `cloudflare` are independent of one another and can be applied in any order,
or concurrently.

## The edges Terraform cannot enforce

**`rds` reads a secret at plan time.** `data.aws_secretsmanager_secret_version.master_password`
resolves the container that `secrets` enrolls and `scripts/ops/provision/rds-master-password-secret.sh`
seeds. Apply `rds` first and the plan fails on a missing secret, with an error that does not name the
cause.

**`secrets` creates containers; the provision scripts inject values.** Applying `secrets` alone
leaves every container empty, and a pod that mounts an empty secret starts and then misbehaves rather
than failing loudly. One script per credential family under `scripts/ops/provision/`.

**`cloudflare-tunnel-secrets.sh` runs after `terraform/cloudflare`,** because the tunnel tokens it
injects are that stack's outputs. So the credential flow is not a straight line: `cloudflare` →
script → `secrets` values → pods.

## What must already exist

The RDS VPC is an **input**, not an output. The only `aws_vpc` resource in this repo is the fleet's
own (`modules/fleet/network.tf`); `rds` takes `vpc_id` and `db_subnet_ids`, and both fleets take
`rds_vpc_id`, all pointing at a VPC no stack manages. On an account without it there is nothing to
supply.

Every `terraform.tfvars` is gitignored. Each stack ships a tracked `terraform.tfvars.example`.

## From scratch

1. Apply `iam-bootstrap` with admin credentials. Local state, no dependencies.
2. Write `terraform/backend.hcl` from its `tf_state_bucket` output, then `init` the other stacks.
3. Apply `secrets`, then run the `scripts/ops/provision/` scripts that seed values.
4. Apply `rds`, `oregon` and `cloudflare`.
5. Run `cloudflare-tunnel-secrets.sh` now that the tunnel tokens exist.
6. Apply `seoul`, then `global-accelerator`.

This provisions infrastructure, not a running system: a fresh `rds` is an empty schema. Data arrives
from a dump, and the schema from Flyway on first pod boot.

## Guards

`allowed_account_ids = [var.aws_account_id]` on every AWS provider fails the plan when the resolved
credentials belong to another account — the guard a workspace does not provide, since workspaces
isolate state and nothing else.

`aws_db_instance.this` and the prod secrets carry `prevent_destroy` alongside RDS
`deletion_protection`, so a destroy or a replacement errors instead of deleting data. The fleet
stacks deliberately carry neither, so their destroy-and-rebuild cycle keeps working.
